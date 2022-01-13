package org.ericghara;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MovieCollection {

    private MovieFolder rootFolder;

    public MovieCollection(String pathString) {
        Path rootPath = FileSystems.getDefault().getPath(pathString).toAbsolutePath();
        Collector collector = new Collector(rootPath);
        rootFolder = collector.getRootFolder();
    }

    public Path getRootPath() {
        return rootFolder.getFolderPath();
    }

    Optional<MovieFolder> openFolder(Path path) {
        FileClassifier.mustBeAbsolutePath(path);
        Path rootPath = getRootPath();
        MovieFolder curFolder = path.startsWith(rootPath) ? rootFolder : null;
        // Note: a for each loop of relativized paths doesn't work because path "" is considered to have a length 1
        for (int i = rootPath.getNameCount(); i < path.getNameCount()
                && Objects.nonNull(curFolder); i++) {
            curFolder = curFolder.getFolder(path.getName(i) );
        }
        return Optional.ofNullable(curFolder);
    }

    boolean validFolderPath(Path path) {
        return openFolder(path).isPresent();
    }

    boolean validFilePath(Path filePath) {
        Path parent = filePath.getParent();
        Path filename = filePath.getFileName();
        Optional<MovieFolder> folder = openFolder(parent);
        return folder.isPresent() && folder.get().containsFile(filename);
    }

    private int getDepth(Path path) {
        return path.getNameCount() - getRootPath().getNameCount();
    }

    private static class Collector {
        private final int MIN_VIDEO_SIZE_MB = 50;

        Path rootPath;
        MovieFolder rootFolder;
        LinkedList<MovieFolder> folderStack;
        private FileClassifier fileClassifier;


        private Collector(Path rootPath) {
            mustBeDir(rootPath);
            this.rootPath = rootPath;
            folderStack = new LinkedList<>();
            fileClassifier = new FileClassifier();
            walkStream().filter(FileClassifier::isReadableAndWriteable)
                        .map(this::manageStack)
                        .forEach(this::sortFiles);
            rootFolder = folderStack.removeFirst(); // ...it's a stack 99.9% of the time.
        }

        private MovieFolder getRootFolder() {
            return rootFolder;
        }

        private void mustBeDir(Path path) {
            if (!FileClassifier.isDir(path)) {
                throw new IllegalArgumentException("Starting path must be a directory and cannot be a symlink");
            }
        }

        //Note override can't use outer class getDepth because no rootFolder has been defined
        private int getDepth(Path path) {
            return path.getNameCount() - rootPath.getNameCount();
        }

        private int currentDepth() {
            return folderStack.size() - 1;
        }

        private Path manageStack(Path path) {
            int placeAtDepth = getDepth(path)-1;
            int curDepth = currentDepth();
            int stackPops = curDepth -  placeAtDepth;
            if (stackPops < 0) {
                throw new IllegalArgumentException("Calculated a negative number of stackPops which is impossible:" + path );
            }
            IntStream.range(0, stackPops)
                    .forEach((i) -> folderStack.removeLast());
        return path;
        }

        private Path sortFiles(Path path) {
            if (FileClassifier.isFile(path)) {
                MovieFolder folder = Objects.requireNonNull(folderStack.peekLast(), "Received a null folder!");
                addFile(folder, path);
            }
            else if (FileClassifier.isDir(path)) {
                addFolder(path);
            }
            else {
                System.out.println("Path target couldn't be classified as a directory or regular file: " + path);
            }
            return path;
        }

        private void addFile(MovieFolder folder, Path path) {
            Path filename = path.getFileName();
            if (fileClassifier.fileSizeLargerThan(path, MIN_VIDEO_SIZE_MB) ) {
                if (fileClassifier.isVideo(filename) )  {
                    folder.addMovie(filename);
                }
                else {
                    folder.addUnusual(filename);
                }
            }
            // Note: currently rejecting potential isSub matches with file sizes > MIN_VIDEO_SIZE_MB.
            else if (fileClassifier.isSub(filename)) {
                folder.addSub(filename);
            }
            else {
                folder.addPossiblyJunk(filename);
            }
        }

        private void addFolder(Path path) {
            int depth = getDepth(path);
            MovieFolder newFolder = new MovieFolder(path, depth);
            if (!folderStack.isEmpty()) {
                MovieFolder curFolder = Objects.requireNonNull(folderStack.peekLast());
                curFolder.addFolder(newFolder);
            }
            folderStack.addLast(newFolder);
        }

        /**
         * Creates the stream of paths from walking from the root path.  Does not follow Symlinks, and filters files
         * which are neither readable nor writeable.
         *
         * @return Paths from current directory
         */
        Stream<Path> walkStream() {
            Stream.Builder<Path> walkStream = Stream.builder();
            try {
                // Custom SimpleFileVisitor which suppresses IOExceptions (unlike Files.walk(Path) )
                Files.walkFileTree( rootPath,new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                try {
                                        walkStream.add(file);
                                } catch (Exception e) {
                                    System.out.println("Suppressed an IOException in SimpleFileVisitor.FileVisit: "
                                    + file + ".");
                                }
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                exc.addSuppressed(new IOException());
                                System.out.println("Suppressed an IOException in SimpleFileVisitor.FileVisitFailed: "
                                        + file + ".");
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                walkStream.add(dir);
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                                if (Objects.nonNull(exc) ) {
                                    exc.addSuppressed(new IOException());
                                    System.out.println("Suppressed an IOException in SimpleFileVisitor.postVisitDirectory: "
                                            + dir + ".");
                                }
                                    return FileVisitResult.CONTINUE;
                            }

                        } ); } catch (Exception e) {
                                System.out.println("Suppressed an exception in SimpleFileVisitor.FileVisit.");
                                e.printStackTrace();
            }
            return walkStream.build();
        }


    }

    public static void main(String[] args) {
        String pathString = args[0];
        Path query = Paths.get("/mnt/media/Media/Movies/Broadcast.News.1987.1080p.BluRay.H264.AAC-RARBG/Subs/");
        MovieCollection col = new MovieCollection(pathString);
        System.out.println(col.rootFolder);
        System.out.println(col.openFolder(query));
    }
}
