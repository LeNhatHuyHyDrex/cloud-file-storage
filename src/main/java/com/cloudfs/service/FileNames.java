package com.cloudfs.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class FileNames {

    private FileNames() {
    }

    /** Bo phan duong dan, ky tu dieu khien; gioi han 255 ky tu. */
    static String clean(String raw) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid name");
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
