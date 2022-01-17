package org.ericghara;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("MovieCollectionTest - Read-only tests")
public class MovieCollectionTest {
    final static String TEST_CSV = "Example.csv";

    /*These tests can use a static tmpDir to avoid nuking the hard drive with a lot of repetitive writes*/
    static MovieCollection collection;
    static TestMovieDir testMovieDir;
    @TempDir
    static Path tmpDir; // note: Junit waits until after constructor to inject TempDir path

    @BeforeAll
    static void setup() {
        testMovieDir = new TestMovieDir(TEST_CSV, tmpDir);
        collection = new MovieCollection(tmpDir.toString());
    }

    @Test
    void validFolderPathTest() {
        Iterable<Path> folders = testMovieDir.getDirs();
        folders.forEach((p) ->
                Assertions.assertTrue(collection.containsFolder(p)));
    }

    @Test
    void validFilePathTest() {
        Iterable<Path> files = testMovieDir.getFiles();
        files.forEach((p) ->
                Assertions.assertTrue(collection.containsFile(p)));
    }

    @TestInstance(Lifecycle.PER_METHOD)
    @Nested
    @DisplayName("MovieCollectionTest - File write tests")
    class FileWriteTests {

        TestMovieDir testMovieDir;
        MovieCollection collection;

        @BeforeEach
        void setup(@TempDir Path tmpDir) {
            testMovieDir = new TestMovieDir(TEST_CSV, tmpDir);
            collection = new MovieCollection(tmpDir.toString());
        }

        @Test
        @DisplayName("deleteFile method test")
        void deleteFileTest() {
            Iterable<Path> files = testMovieDir.getFiles();
            files.forEach((p) -> {
                collection.deleteFile(p);
                Assertions.assertFalse(Files.exists(p, LinkOption.NOFOLLOW_LINKS));
            });
        }

        @Test
        @DisplayName("copyFile - copy all files to depth 0")
        void copyFileToRoot() {
            Iterable<Path> files = testMovieDir.getFiles();
            Path rootPath = collection.getRootPath();
            for (Path fullSourcePath : files) {
                Path filename = fullSourcePath.getFileName();
                Path fullDestinationPath = rootPath.resolve(filename);
                if (!collection.containsFile(fullDestinationPath)) { // don't copy files at depth 0 onto themselves
                    collection.copyFile(fullSourcePath, fullDestinationPath);
                    Assertions.assertTrue(collection.containsFile(fullDestinationPath)); // data record assertions
                    Assertions.assertTrue(collection.containsFile(fullSourcePath));    // data record assertions
                    Assertions.assertTrue(Files.exists(fullDestinationPath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                    Assertions.assertTrue(Files.exists(fullSourcePath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                }
            }
        }

        @Test
        @DisplayName("copyFile - copy all files to the greatest depth folder")
        void copyFileToDeepestLevel() {
            Iterable<Path> files = testMovieDir.getFiles();
            Stream.Builder<Path> dirStream = Stream.builder();
            testMovieDir.getFiles().forEach((f) -> dirStream.add(f.getParent()));
            testMovieDir.getDirs().forEach(dirStream::add);
            Path deepestDir = dirStream.build().max(Comparator.comparingInt(Path::getNameCount))
                                               .orElse(collection.getRootPath());
            for (Path fullSourcePath : files) {
                Path filename = fullSourcePath.getFileName();
                Path fullDestinationPath = deepestDir.resolve(filename);
                if (!collection.containsFile(fullDestinationPath)) { // don't copy files at deepestDir onto themselves
                    collection.copyFile(fullSourcePath, fullDestinationPath);
                    Assertions.assertTrue(collection.containsFile(fullDestinationPath)); // data record assertions
                    Assertions.assertTrue(collection.containsFile(fullSourcePath));    // data record assertions
                    Assertions.assertTrue(Files.exists(fullDestinationPath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                    Assertions.assertTrue(Files.exists(fullSourcePath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                    }
                }
            }


        @Test
        @DisplayName("moveFile - move all files to the greatest depth folder")
        void moveFileToDeepestDir() {
            Iterable<Path> files = testMovieDir.getFiles();
            Stream.Builder<Path> dirStream = Stream.builder();
            testMovieDir.getFiles().forEach((f) -> dirStream.add(f.getParent()));
            testMovieDir.getDirs().forEach(dirStream::add);
            Path deepestDir = dirStream.build().max(Comparator.comparingInt(Path::getNameCount))
                    .orElse(collection.getRootPath());
            for (Path fullSourcePath : files) {
                Path filename = fullSourcePath.getFileName();
                Path fullDestinationPath = deepestDir.resolve(filename);
                if (!collection.containsFile(fullDestinationPath)) { // don't move files at depth 0 onto themselves
                    collection.moveFile(fullSourcePath, fullDestinationPath);
                    Assertions.assertTrue(collection.containsFile(fullDestinationPath)); // data record assertions
                    Assertions.assertFalse(collection.containsFile(fullSourcePath));    // data record assertions
                    Assertions.assertTrue(Files.exists(fullDestinationPath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                    Assertions.assertFalse(Files.exists(fullSourcePath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                }
            }
        }

        @Test
        @DisplayName("moveFile - move all files to depth 0")
        void moveFileToRootPath() {
            Iterable<Path> files = testMovieDir.getFiles();
            Path rootPath = collection.getRootPath();
            for (Path fullSourcePath : files) {
                Path filename = fullSourcePath.getFileName();
                Path fullDestinationPath = rootPath.resolve(filename);
                if (!collection.containsFile(fullDestinationPath)) { // don't copy files at deepestDir onto themselves
                    collection.moveFile(fullSourcePath, fullDestinationPath);
                    Assertions.assertTrue(collection.containsFile(fullDestinationPath)); // data record assertions
                    Assertions.assertFalse(collection.containsFile(fullSourcePath));    // data record assertions
                    Assertions.assertTrue(Files.exists(fullDestinationPath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                    Assertions.assertFalse(Files.exists(fullSourcePath, LinkOption.NOFOLLOW_LINKS)); // filesystem assertions
                }
            }
        }
    }


        @TestInstance(Lifecycle.PER_METHOD)
        @Nested
        @DisplayName("MovieCollectionTest - Dir write tests")
        class DirWriteTests {
            TestMovieDir testMovieDir;
            MovieCollection collection;
            @TempDir Path tmpDir;

            void setup(String csv) {
                testMovieDir = new TestMovieDir(csv, tmpDir);
                collection = new MovieCollection(tmpDir.toString());
            }

            @ParameterizedTest
            @ValueSource(strings = {"deleteTopLevelDirs.csv"} )
            @DisplayName("deleteFolder - Single level deletion of empty folders")
            void deleteFolder(String csv) {
                setup(csv);
                Iterable<Path> dirs = testMovieDir.getDirs();
                dirs.forEach((p) -> {
                    collection.deleteFolder(p);
                    Assertions.assertFalse(Files.exists(p, LinkOption.NOFOLLOW_LINKS));
                });
            }

            @ParameterizedTest
            @ValueSource(strings = {"deepDirs.csv"} )
            @DisplayName("moveFolder - move all folders into the root dir")
            void moveFolderToRoot(String csv) {
                setup(csv);
                Stream.Builder<Path> dirStream = Stream.builder();
                testMovieDir.getFiles().forEach((f) -> dirStream.add(f.getParent()));
                testMovieDir.getDirs().forEach(dirStream::add);
                Path rootDir = collection.getRootPath();
                dirStream.build().forEach( (d) -> {
                    Path rel = rootDir.relativize(d);
                    Path last = rootDir;
                    for (Path rp: rel) {
                        Path src = last.resolve(rp);
                        Path dst = rootDir.resolve(rp);
                        last = dst;
                        if (Files.exists(src, LinkOption.NOFOLLOW_LINKS) && !src.equals(dst) ) {
                            collection.moveFolder(src, dst);
                            Assertions.assertFalse(Files.exists(src, LinkOption.NOFOLLOW_LINKS) );
                            Assertions.assertTrue(Files.exists(dst, LinkOption.NOFOLLOW_LINKS) );
                            Assertions.assertFalse(collection.containsFolder(src) );
                            Assertions.assertTrue(collection.containsFolder(dst) );
                            Assertions.assertEquals(collection.openFolder(dst).orElseThrow(IllegalArgumentException::new).getDepth(), 1);
                        }
                    }
                } );
            }
        }
    }
