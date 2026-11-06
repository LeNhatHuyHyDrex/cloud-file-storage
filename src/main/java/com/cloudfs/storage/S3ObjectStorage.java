package com.cloudfs.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Adapter cho moi storage noi giao thuc S3 (AWS S3, SeaweedFS, MinIO, Ceph RGW...). */
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    /** Tao bucket neu chua co; thu lai vai lan vi storage co the khoi dong cham hon app. */
    public void ensureBucket(int attempts, Duration backoff) {
        for (int i = 1; ; i++) {
            try {
                try {
                    s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                } catch (NoSuchBucketException e) {
                    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    log.info("Created bucket '{}'", bucket);
                } catch (S3Exception e) {
                    if (e.statusCode() != 404) {
                        throw e;
                    }
                    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    log.info("Created bucket '{}'", bucket);
                }
                return;
            } catch (SdkException e) {
                if (i >= attempts) {
                    throw e;
                }
                log.warn("Object storage not ready ({}/{}): {}", i, attempts, e.getMessage());
                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public InputStream get(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public Optional<URL> presignDownload(String key, String fileName, Duration ttl) {
        var request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition(ContentDispositions.attachment(fileName))
                .build();
        return Optional.of(presigner.presignGetObject(p -> p.signatureDuration(ttl).getObjectRequest(request)).url());
    }

    @Override
    public String backendName() {
        return "s3";
    }
}
