package org.ericghara;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MovieCollectionTest {
    private static final String testCsv = "Example.csv";
    @TempDir
    Path tmpDir; // literally a dir created in /tmp by JUnit
    TestMovieDir testMovieDir;


    @BeforeEach
    void setup() {
        tmpDir = Paths.get("").toAbsolutePath();
        testMovieDir = new TestMovieDir(testCsv, tmpDir.toAbsolutePath());
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    static class ReadOnlyTests { // revisit static nested declaration due to warning
        /*These tests can use a static tmpDir to avoid nuking the hard drive with a lot of repetitive IO*/
        static MovieCollection collection;
        @TempDir
        static Path tmpDir;

        @BeforeAll
        static void setup() {
            TestMovieDir testMovieDir = new TestMovieDir(testCsv, tmpDir);
            collection = new MovieCollection(tmpDir.toString() );
        }

        @Test
        void validFolderPathTest() {
            Path absPath = collection.getRootPath().resolve("dir0");
            Assertions.assertTrue(collection.validFolderPath(absPath));
        }

        @Test
        void validFilePathTest() {
            Path absPath = collection.getRootPath().resolve("dir0/subs/subsForMovie1.srt");
            Assertions.assertTrue(collection.validFilePath(absPath) );
        }





    }
}
