package com.cloudfs.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudfs.storage.LocalObjectStorage;
import com.cloudfs.storage.ObjectStorage;
import com.cloudfs.storage.S3ObjectStorage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class StorageConfig {

    @Configuration
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
    static class S3 {

        @Bean
        AwsCredentialsProvider awsCredentials(@Value("${app.s3.access-key:}") String accessKey,
                                              @Value("${app.s3.secret-key:}") String secretKey) {
            // Khong dat key -> dung chuoi mac dinh cua AWS (IAM role tren EC2/ECS, ~/.aws, env...)
            if (accessKey.isBlank()) {
                return DefaultCredentialsProvider.create();
            }
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        @Bean(destroyMethod = "close")
        S3Client s3Client(AwsCredentialsProvider credentials,
                          @Value("${app.s3.endpoint:}") String endpoint,
                          @Value("${app.s3.region}") String region,
                          @Value("${app.s3.path-style:true}") boolean pathStyle) {
            var builder = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
            // Co endpoint -> storage S3-compatible; bo trong -> AWS S3 that
            if (!endpoint.isBlank()) {
                builder.endpointOverride(URI.create(endpoint));
            }
            return builder.build();
        }

        @Bean(destroyMethod = "close")
        S3Presigner s3Presigner(AwsCredentialsProvider credentials,
                                @Value("${app.s3.endpoint:}") String endpoint,
                                @Value("${app.s3.public-endpoint:}") String publicEndpoint,
                                @Value("${app.s3.region}") String region,
                                @Value("${app.s3.path-style:true}") boolean pathStyle) {
            var builder = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
            // URL ky san phai tro toi host ma trinh duyet truy cap duoc
            String target = publicEndpoint.isBlank() ? endpoint : publicEndpoint;
            if (!target.isBlank()) {
                builder.endpointOverride(URI.create(target));
            }
            return builder.build();
        }

        @Bean
        ObjectStorage objectStorage(S3Client s3, S3Presigner presigner,
                                    @Value("${app.s3.bucket}") String bucket) {
            S3ObjectStorage storage = new S3ObjectStorage(s3, presigner, bucket);
            storage.ensureBucket(10, Duration.ofSeconds(3));
            return storage;
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
    static class Local {

        @Bean
        ObjectStorage objectStorage(@Value("${app.storage.local-dir}") String dir) {
            return new LocalObjectStorage(Path.of(dir));
        }
    }
}
