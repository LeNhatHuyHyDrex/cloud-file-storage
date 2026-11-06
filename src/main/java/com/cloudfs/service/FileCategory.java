package com.cloudfs.service;

import java.util.Locale;
import java.util.Set;

/** Phan loai file de hien thi icon va thong ke dung luong. */
public enum FileCategory {
    IMAGE("image"), VIDEO("video"), AUDIO("audio"), DOCUMENT("document"), ARCHIVE("archive"), CODE("code"), OTHER("other");

    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md",
            "csv", "odt", "ods", "odp", "rtf");
    private static final Set<String> ARCHIVE_EXT = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    private static final Set<String> CODE_EXT = Set.of("java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs",
            "html", "css", "json", "xml", "yml", "yaml", "sql", "sh", "kt", "php");

    private final String id;

    FileCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static FileCategory of(String contentType, String fileName) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String ext = extension(fileName);
        if (type.startsWith("image/")) {
            return IMAGE;
        }
        if (type.startsWith("video/")) {
            return VIDEO;
        }
        if (type.startsWith("audio/")) {
            return AUDIO;
        }
        if (CODE_EXT.contains(ext)) {
            return CODE;
        }
        if (DOC_EXT.contains(ext) || type.equals("application/pdf") || type.startsWith("text/")) {
            return DOCUMENT;
        }
        if (ARCHIVE_EXT.contains(ext)) {
            return ARCHIVE;
        }
        return OTHER;
    }

    static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
