package org.ericghara;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

class FileClassifier {

    private static String[] VIDEO_EXTS = {"ASX", "GXF", "M2V", "M3U", "M4V", "MPEG1", "MPEG2", "MTS", "MXF",
            "OGM", "PLS", "BUP", "B4S", "CUE", "DIVX", "DV", "FLV", "M1V", "M2TS", "MKV", "MOV", "MPEG4",
            "TS", "VLC", "VOB", "XSPF", "DAT", "IFO", "3G2", "MPEG", "MPG", "OGG", "3GP", "WMV", "AVI", "ASF",
            "MP4", "M4P"};
    private static String[] SUB_EXTS = {"SRT", "SUB", "IDX"};

    private Matcher videoMatcher;
    private Matcher subMatcher;

    FileClassifier() {
        videoMatcher = getMatcher(VIDEO_EXTS);
        subMatcher = getMatcher(SUB_EXTS);
    }

    // returns an empty matcher. It needs to be reset with a new char sequence;
    private static Matcher getMatcher(String[] exts) {
        if (exts.length < 1) {
            throw new IllegalArgumentException("Received an empty extensions array");
        }
        final int Flags = Pattern.CASE_INSENSITIVE;
        // generates pattern representing the following regex: ^[^\.].*\.(ext1|ext2|ext3...)$
        final String matchFilename = "^[^\\.].*\\.";
        StringBuilder builder = new StringBuilder();
        builder.append(matchFilename);
        builder.append('(');
        Arrays.asList(exts).forEach((ext) -> builder.append(ext).append('|'));
        builder.setCharAt(builder.length()-1, ')');
        builder.append("$");
        return Pattern.compile(builder.toString(), Flags).matcher("");
    }

    static void mustBeFilename(Path filename) {
        if (filename.getNameCount() != 1) {
            throw new IllegalArgumentException("Received an invalid filename, did you accidentally provide a full path?");
        }
    }

    static void mustBeAbsolutePath(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Expected an absolute path but received a relative path: "
                + path + ".");
        }
    }

    // accepts both absolute and relative paths
    boolean isVideo(Path path) {
        String filename = path.getFileName().toString();
        videoMatcher.reset(filename);
        return videoMatcher.matches();
    }

    // accepts both absolute and relative paths
    boolean isSub(Path path) {
        String filename = path.getFileName().toString();
        subMatcher.reset(filename);
        return subMatcher.matches();
    }

    /**
     * @param path must be absolute
     * @return true if path target is a file, false if not
     */
    static boolean isFile(Path path) {
        mustBeAbsolutePath(path);
        return Files.isRegularFile(path);
    }

    /**
     * Note symlinks will return false.
     *
     * @param path must be absolute
     * @return true if path target is a directory, false if not (including symlink)
     */
    static boolean isDir(Path path) {
        mustBeAbsolutePath(path);
        return Files.isDirectory(path, NOFOLLOW_LINKS);
    }

    static boolean isSymLink(Path path) {
        mustBeAbsolutePath(path);
        return Files.isSymbolicLink(path);
    }

    static boolean isReadableAndWriteable(Path path) {
        return (Files.isReadable(path) || Files.isWritable(path) );
    }

    /**
     *
     * @param path must be absolute
     * @param sizeMB file size cutoff
     * @return tue if file size > sizeMB; false if file size <= sizeMB;
     */
    boolean fileSizeLargerThan(Path path, int sizeMB) {
        final int ONE_MB = 1_048_576; // bytes per MB
        mustBeAbsolutePath(path);
        long sizeBytes;
        try {
            sizeBytes = Files.size(path);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Couldn't open file: " + path + ".");
        }
        return sizeBytes/ONE_MB > sizeMB;
    }

//        boolean unusualFile(Path pathToFile) {
//            return filesizeLargerThan(pathToFile, MIN_VIDEO_SIZE_MB) != isVideo(pathToFile);
//        }

}
