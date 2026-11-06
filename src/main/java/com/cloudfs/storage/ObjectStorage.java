package com.cloudfs.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;

/**
 * Cong (port) toi tang luu tru object. Phan con lai cua ung dung chi biet interface nay,
 * nen co the doi AWS S3 / SeaweedFS / MinIO / Ceph / o dia ma khong sua business logic.
 */
public interface ObjectStorage {

    void put(String key, InputStream content, long size, String contentType) throws IOException;

    InputStream get(String key) throws IOException;

    void delete(String key);

    /** URL tai truc tiep tu storage, co chu ky va thoi han; rong neu backend khong ho tro. */
    Optional<URL> presignDownload(String key, String fileName, Duration ttl);

    /** Ten backend de hien thi (vd "s3", "local"). */
    String backendName();
}
