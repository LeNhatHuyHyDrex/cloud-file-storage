package com.cloudfs.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

class S3ObjectStorageTest {
    @Test
    void permanentDeletionRemovesOnlyExactKeyAcrossVersionsAndMarkers() {
        S3Client s3 = Mockito.mock(S3Client.class);
        String key = "alice/object";
        when(s3.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder().isTruncated(true).nextKeyMarker(key).nextVersionIdMarker("v1")
                        .versions(ObjectVersion.builder().key(key).versionId("v1").build(),
                                ObjectVersion.builder().key(key + "-other").versionId("other").build()).build(),
                ListObjectVersionsResponse.builder().isTruncated(false)
                        .versions(ObjectVersion.builder().key(key).versionId("v2").build())
                        .deleteMarkers(DeleteMarkerEntry.builder().key(key).versionId("marker").build()).build());
        var storage = new S3ObjectStorage(s3, null, "bucket", true);
        storage.deletePermanently(key);
        var requests = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3, times(2)).listObjectVersions(any(ListObjectVersionsRequest.class));
        verify(s3, times(3)).deleteObject(requests.capture());
        assertEquals(Set.of("v1", "v2", "marker"), requests.getAllValues().stream().map(DeleteObjectRequest::versionId).collect(Collectors.toSet()));
        requests.getAllValues().forEach(r -> assertEquals(key, r.key()));
    }
}
