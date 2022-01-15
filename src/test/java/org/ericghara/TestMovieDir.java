package org.ericghara;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This creates a dummy Movie dir from a csv file -- see Example.csv for details.
 * The idea is to trick other classes into seeing
 */
public class TestMovieDir {

    // match all text after # and all leading whitespace
    private final String hashCommentsRegEx = "(\\b|^)\\s*#.*$";
    private final Matcher hashComments = compileMatcher(hashCommentsRegEx);
    // match whitespace with 0 or even number of " ahead
    private final String whitespaceNotInQuotesRegEx = "\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)";
    private final Matcher whitespaceNotInQuotes = compileMatcher(whitespaceNotInQuotesRegEx);
    // match text between quotes which appear at the beginning and end of the line
    private final String textInQuotesRegEx = "(?<=^\").+(?=\"$)";
    private final Matcher textInQuotes = compileMatcher(textInQuotesRegEx);
            
    private final Path testDir; // parent which all relative paths in csv are resolved against
    private final LinkedList<Path> files = new LinkedList<>(); // all files successfully written
    private final LinkedList<Path> dirs = new LinkedList<>(); // all dirs successfully written

    /**
     * Initializes a Test Movie Dir from a csv template. The location of the movie dir is defined by the testDir argument.
     *
     * @param csvName the filename (not full path) of the csv file in the test resources directory
     * @param testDir absolute path to the desired directory
     */
    public TestMovieDir(String csvName, Path testDir) {
        mustBeDir(testDir);
        File csvFile = getResourceFile(csvName);
        Scanner csvScanner = getFileScanner(csvFile);
        this.testDir = testDir;
        parse(csvScanner);
    }

    public Path getTestDir() {
        return testDir;
    }

    public Iterable<Path> getFiles() {
        return files;
    }

    public LinkedList<Path> getDirs() {
        return dirs;
    }

    private Scanner getFileScanner(File file) {
        Scanner scanner;
        try {
            scanner = new Scanner(file);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Couldn't open the csv " + file);
        }
        return scanner;
    }

    private File getResourceFile(String csvName) {
        File csv;
        try {
            URI csvPath = this.getClass().getResource(csvName).toURI();
            csv = new File(csvPath);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Couldn't open the csv " + csvName);
        }
        return csv;
    }

    private void parse(Scanner csvScanner) {
        for (int i = 0; csvScanner.hasNextLine(); i++) {
            String line = hashComments.reset(csvScanner.nextLine()).replaceAll(""); // strip comments
            String[] splitLine = whitespaceNotInQuotes.pattern().split(line); // split columns
            if (splitLine.length >= 2 && textInQuotes.reset(splitLine[1]).find() ) {
                splitLine[1] = textInQuotes.group(); // remove quotes
            }
            if (splitLine.length == 1 && splitLine[0].isEmpty()) { // ignore empty line (or a stripped comment line)
                continue;
            }
            if (splitLine[0].equalsIgnoreCase("D") && splitLine.length == 2 ) { // directory
                Path dirPath = createDir(splitLine[1]);
                dirs.addLast(dirPath);
            }
            else if (splitLine[0].equalsIgnoreCase("F") && splitLine.length == 3) { // file
                String pathString = splitLine[1];
                int sizeMB = Integer.parseInt(splitLine[2]);
                Path filePath = createFile(pathString, sizeMB);
                files.addLast(filePath);
            }
            else {
                throw new IllegalArgumentException("Couldn't parse line: " + i + ".");
            }
        }
    }

    private void createDir(Path path) {
        mustBeAbsolute(path);
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Could not create dir " + path);
        }
    }


    /**
     * Creates a directory at the given relative path.  If the parent path to the directory does not yet exist,
     * directories are created in order to complete the path.  The absolute path to the directory is constructed
     * by merging {@code testDir}'s path with the {@code pathString}.
     * @param pathString relative location of the new directory
     * @return absolute path to the directory
     */
    private Path createDir(String pathString) {
        Path absPath = testDir.resolve(pathString);
        createDir(absPath);
        return absPath;
    }


    /**
     * Creates file of specified size in the given relative path.  The absolute path is constructed by merging
     * the {@code testDir} path with the given {@code pathString}.  Any new directories required to create a file
     * at the specified path are created if the parent path does not yet exist.
     * @param pathString relative file path
     * @param sizeMB size of the file to create in MB
     * @return the absolute path to the file
     */
    private Path createFile(String pathString, int sizeMB) {
        Path filePath = testDir.resolve(Paths.get(pathString) );
        Path parentPath = filePath.getParent();
        try {
            if (Files.notExists(parentPath, LinkOption.NOFOLLOW_LINKS)) {
                createDir(parentPath);
            }
            Files.createFile(filePath);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Could not create the file:" + filePath + ".");
        }
        long bytes = 1024L * 1024L * sizeMB;
        try {
            BufferedWriter writer = Files.newBufferedWriter(filePath);
            while (bytes > 0) {
                bytes--;
                writer.write(0);
            }
            writer.close();
        } catch (Exception e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Could not write " + sizeMB + "MB to: " + filePath );
            }
        return filePath;
    }

    private static Matcher compileMatcher(String regEx) {
        return Pattern.compile(regEx).matcher("");
    }

    private void mustBeAbsolute(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Received a path that was not absolute " + path + ".");
        }
    }

    private void mustBeDir(Path path) {
        mustBeAbsolute(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ) {
            throw new IllegalArgumentException("Expected an existing directory but it does not exist: "
                    + path + ".");
        }
    }

    /*Simple test.  Note this will write to the file system to the tmp dir
    * the exact folder will be printed to std out*/
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Improper usage, provide 1 argument " +
                    "- the name of the csv file resource");
        }
        // Note this dir will persist after exit
        Path tempDir = Files.createTempDirectory("TestMovieDir");
        TestMovieDir tmd = new TestMovieDir(args[0], tempDir);
        System.out.printf("A test movie dir was successfully created at: %s%n", tmd.getTestDir() );
    }
}
