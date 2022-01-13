package org.ericghara;

import java.io.BufferedWriter;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This creates a dummy Movie dir from a csv file -- see Example.csv for details.
 * The idea is to trick other classes into seeing
 */
public class TestMovieDir {

    static String hashCommentsRegEx = "(\\b|^)\\s*#.*$"; 
    static String whitespaceNotInQuotesRegEx = "\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)"; 
    static String justaDRegEx = "^(d|D)$";
    static String justaFRegEx = "^(f|F)$";
    static String textInQuotesRegEx = "(?<=^\").+(?=\"$)";

    private final Matcher hashComments, whitespaceNotInQuotes, justaD, justaF, textInQuotes;

    Path testDir;

    public TestMovieDir(String csvName) {
        File csvFile = getResourceFile(csvName);
        Scanner csvScanner = getFileScanner(csvFile);
        testDir = csvFile.toPath()
                              .toAbsolutePath()
                              .getParent();
        hashComments = compileMatcher(hashCommentsRegEx);  // match all text after # and all leading whitespace
        whitespaceNotInQuotes = compileMatcher(whitespaceNotInQuotesRegEx);  // match whitespace with 0 or even number of " ahead
        justaD = compileMatcher(justaDRegEx); // matches "D" or "d" only
        justaF = compileMatcher(justaFRegEx); // matches "F" of "f" only
        textInQuotes = compileMatcher(textInQuotesRegEx); // matches text only between quotes that start and end a string

        parse(csvScanner);
    }

    public TestMovieDir(String csvName, Path testDir) {
        File csvFile = getResourceFile(csvName);
        Scanner csvScanner = getFileScanner(csvFile);
        this.testDir = testDir;
        hashComments = compileMatcher("(\\b|^)\\s*#.*$");  // match all text after # and all leading whitespace
        whitespaceNotInQuotes = compileMatcher("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)");  // match whitespace with 0 or even number of " ahead
        justaD = compileMatcher("^(d|D)$"); // matches "D" or "d" only
        justaF = compileMatcher("^(f|F)$"); // matches "F" of "f" only
        textInQuotes = compileMatcher("(?<=^\").+(?=\"$)"); // matches text only between quotes that start and end a string

        parse(csvScanner);
    }

    public Path getTestDir() {
        return testDir;
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
                createDir(splitLine[1]);
            }
            else if (justaF.reset(splitLine[0]).matches() && splitLine.length == 3) {
                String pathString = splitLine[1];
                int sizeMB = Integer.parseInt(splitLine[2]);
                createFile(pathString, sizeMB);
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

    private void createDir(String pathString) {
        Path absPath = testDir.resolve(pathString);
        createDir(absPath);
    }

    private void mustBeAbsolute(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Received a path that was not absolute " + path + ".");
        }
    }

    private void createFile(String pathString, int sizeMB) {
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
    }

    private static Matcher compileMatcher(String regEx) {
        return Pattern.compile(regEx).matcher("");
    }

    // Simple test.  Note this will write to the file system into the resources dir, if run under gradle that will be
    // build/resources/test/org/ericghara
    public static void main(String[] args) {
        new TestMovieDir("Example.csv");
    }

}
