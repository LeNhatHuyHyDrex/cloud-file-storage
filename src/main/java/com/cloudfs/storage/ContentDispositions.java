package com.cloudfs.storage;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;

final class ContentDispositions {

    private ContentDispositions() {
    }

    /** Header attachment ho tro ten file Unicode (RFC 5987, filename*=UTF-8''...). */
    static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
    }
}
