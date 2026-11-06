package com.cloudfs.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Chay voi SeaweedFS S3 that trong container (CI co Docker). Tu bo qua neu may khong co Docker. */
@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStorageIT {

    @Container
    static final GenericContainer<?> SEAWEED = new GenericContainer<>("chrislusf/seaweedfs:4.52")
            .withCommand("server", "-s3", "-ip.bind=0.0.0.0")
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static S3Client s3;
    static S3Presigner presigner;
    static S3ObjectStorage storage;

    @BeforeAll
    static void setUp() {
        URI endpoint = URI.create("http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333));
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create("any", "any"));
        var cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        s3 = S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(creds).serviceConfiguration(cfg).build();
        presigner = S3Presigner.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .credentialsProvider(creds).serviceConfiguration(cfg).build();
        storage = new S3ObjectStorage(s3, presigner, "it-bucket");
        storage.ensureBucket(20, Duration.ofSeconds(2));
    }

    @AfterAll
    static void tearDown() {
        if (s3 != null) {
            s3.close();
            presigner.close();
        }
    }

    @Test
    void putGetDeleteRoundTrip() throws Exception {
        byte[] data = "xin chao S3".getBytes(StandardCharsets.UTF_8);
        storage.put("alice/obj-1", new ByteArrayInputStream(data), data.length, "text/plain");
        try (InputStream in = storage.get("alice/obj-1")) {
            assertEquals("xin chao S3", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        storage.delete("alice/obj-1");
        assertThrows(NoSuchKeyException.class, () -> storage.get("alice/obj-1").close());
    }

    @Test
    void presignedUrlDownloadsWithoutCredentials() throws Exception {
        byte[] data = "presigned".getBytes(StandardCharsets.UTF_8);
        storage.put("alice/obj-2", new ByteArrayInputStream(data), data.length, "text/plain");
        var url = storage.presignDownload("alice/obj-2", "báo cáo.txt", Duration.ofMinutes(5)).orElseThrow();
        assertTrue(url.toString().contains("X-Amz-Signature"));
        var res = HttpClient.newHttpClient().send(HttpRequest.newBuilder(url.toURI()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertEquals("presigned", res.body());
    }

    @Test
    void ensureBucketIsIdempotent() {
        storage.ensureBucket(1, Duration.ZERO);
    }
}
