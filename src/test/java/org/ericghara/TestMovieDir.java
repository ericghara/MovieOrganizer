package org.ericghara;

import java.io.BufferedWriter;
import java.io.File;
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
    // matches a line consisting of d or D
    private final String justaDRegEx = "^(d|D)$";
    private final Matcher justaD = compileMatcher(justaDRegEx);
    // matches a line consisting of f or F
    private final String justaFRegEx = "^(f|F)$";
    private final Matcher justaF = compileMatcher(justaFRegEx);
    // match text between quotes which appear at the beginning and end of the line
    private final String textInQuotesRegEx = "(?<=^\").+(?=\"$)";
    private final Matcher textInQuotes = compileMatcher(textInQuotesRegEx);
            
    private final Path testDir;
    private final LinkedList<Path> files = new LinkedList<>();
    private final LinkedList<Path> dirs = new LinkedList<>();


    /**
     * Initializes a Test Movie Dir from a csv template.  The location of the movie dir is the resources directory.
     * @param csvName the filename (not full path) of the csv file in the test resources directory
     */
    public TestMovieDir(String csvName) {
        File csvFile = getResourceFile(csvName);
        Scanner csvScanner = getFileScanner(csvFile);
        testDir = csvFile.toPath()
                              .toAbsolutePath()
                              .getParent();
        parse(csvScanner);
    }

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
            URI csvPath = this.getClass().getResource("Example.csv").toURI();
            csv = new File(csvPath);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Couldn't open the csv " + csvName);
        }
        return csv;
    }

    private void parse(Scanner csvScanner) {
        for (int i = 0; csvScanner.hasNextLine(); i++) {
            String line = hashComments.reset(csvScanner.nextLine()).replaceAll("");
            String[] splitLine = whitespaceNotInQuotes.pattern().split(line);
            if (splitLine.length >= 2 && textInQuotes.reset(splitLine[1]).find() ) { // remove quotes
                splitLine[1] = textInQuotes.group();
            }
            if (splitLine.length == 1 && splitLine[0].isEmpty()) {
                continue;
            }
            if (justaD.reset(splitLine[0]).matches() && splitLine.length == 2 ) {
                Path dirPath = createDir(splitLine[1]);
                dirs.addLast(dirPath);
            }
            else if (justaF.reset(splitLine[0]).matches() && splitLine.length == 3) {
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

    // Simple test.  Note this will write to the file system into the resources dir, if run under gradle that will be
    // build/resources/test/org/ericghara
    public static void main(String[] args) {
        new TestMovieDir("Example.csv");
    }

}
