package org.ericghara;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

@TestInstance(Lifecycle.PER_CLASS)
public class MovieCollectionTest {
    final static String TEST_CSV = "Example.csv";
    /*These tests can use a static tmpDir to avoid nuking the hard drive with a lot of repetitive IO*/
    static MovieCollection collection;
    static TestMovieDir testMovieDir;
    @TempDir
    static Path tmpDir; // note: Junit waits until after constructor to inject TempDir path

    @BeforeAll
    static void setup() {
        testMovieDir = new TestMovieDir(TEST_CSV, tmpDir);
        collection = new MovieCollection(tmpDir.toString() );
    }

    @Test
    void validFolderPathTest() {
        Iterable<Path> folders = testMovieDir.getDirs();
        folders.forEach( (p) ->
                Assertions.assertTrue(collection.validFolderPath(p) ) );
    }

    @Test
    void validFilePathTest() {
        Iterable<Path> files = testMovieDir.getFiles();
        files.forEach((p) ->
                Assertions.assertTrue(collection.validFilePath(p)));
    }
}
