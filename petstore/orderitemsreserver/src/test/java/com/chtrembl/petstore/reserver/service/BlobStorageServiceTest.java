package com.chtrembl.petstore.reserver.service;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.common.policy.RequestRetryOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlobStorageServiceTest {
    @Test
    void requiresStorageConnectionStringEvenWhenIdentitySettingsExist() {
        Map<String, String> settings = Map.of("BLOB_CONTAINER_NAME", "order-requests",
                "BLOB_SERVICE_ENDPOINT", "https://example.blob.core.windows.net",
                "AZURE_CLIENT_ID", "unused-identity");
        BlobStorageService service = new BlobStorageService(settings::get);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.uploadOrderJson("session-1", 1, "event-1", "{}"));

        assertTrue(error.getMessage().contains("AZURE_STORAGE_CONNECTION_STRING"));
    }

    @Test
    void rejectsMalformedStorageConnectionString() {
        Map<String, String> settings = Map.of("BLOB_CONTAINER_NAME", "order-requests",
                "AZURE_STORAGE_CONNECTION_STRING", "not-a-connection-string");

        assertThrows(IllegalArgumentException.class, () -> new BlobStorageService(settings::get)
                .uploadOrderJson("session-1", 1, "event-1", "{}"));
    }


    @Test
    void updateWritesHeadersMetadataAndEtagConditionInOneRequest() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(container.getBlobClient("session-1.json")).thenReturn(blob);
        when(blob.getProperties()).thenReturn(properties);
        when(properties.getMetadata()).thenReturn(Map.of("cartrevision", "2"));
        when(properties.getETag()).thenReturn("etag-2");
        BlobStorageService service = new BlobStorageService(container);

        service.uploadOrderJson("session-1", 3, "event-3", "{\"id\":\"session-1\"}");

        var optionsCaptor = org.mockito.ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
        verify(blob, times(1)).uploadWithResponse(optionsCaptor.capture(), eq(Duration.ofSeconds(30)), eq(Context.NONE));
        BlobParallelUploadOptions options = optionsCaptor.getValue();
        assertEquals("application/json", options.getHeaders().getContentType());
        assertEquals(Map.of("cartrevision", "3", "eventid", "event-3"), options.getMetadata());
        assertEquals("etag-2", options.getRequestConditions().getIfMatch());
        verify(blob, never()).setHttpHeaders(any());
    }

    @Test
    void staleOrDuplicateRevisionDoesNotUpload() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        when(container.getBlobClient("session-1.json")).thenReturn(blob);
        when(blob.getProperties()).thenReturn(properties);
        when(properties.getMetadata()).thenReturn(Map.of("cartrevision", "9"));

        new BlobStorageService(container).uploadOrderJson("session-1", 8, "event-8", "{}");

        verify(blob, never()).uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any(Context.class));
    }

    @Test
    void missingBlobUsesCreateOnlyCondition() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        BlobStorageException notFound = mock(BlobStorageException.class);
        when(notFound.getStatusCode()).thenReturn(404);
        when(container.getBlobClient("session-1.json")).thenReturn(blob);
        when(blob.getProperties()).thenThrow(notFound);

        new BlobStorageService(container).uploadOrderJson("session-1", 1, "event-1", "{}");

        var optionsCaptor = org.mockito.ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
        verify(blob).uploadWithResponse(optionsCaptor.capture(), eq(Duration.ofSeconds(30)), eq(Context.NONE));
        BlobRequestConditions conditions = optionsCaptor.getValue().getRequestConditions();
        assertEquals("*", conditions.getIfNoneMatch());
    }

    @Test
    void propagatesEtagContentionForServiceBusRedelivery() {
        BlobContainerClient container = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        BlobProperties properties = mock(BlobProperties.class);
        BlobStorageException preconditionFailed = mock(BlobStorageException.class);
        when(preconditionFailed.getStatusCode()).thenReturn(412);
        when(container.getBlobClient("session-1.json")).thenReturn(blob);
        when(blob.getProperties()).thenReturn(properties);
        when(properties.getMetadata()).thenReturn(Map.of("cartrevision", "2"));
        when(properties.getETag()).thenReturn("etag-2");
        when(blob.uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any(Context.class)))
                .thenThrow(preconditionFailed);

        org.junit.jupiter.api.Assertions.assertThrows(BlobStorageException.class,
                () -> new BlobStorageService(container).uploadOrderJson("session-1", 3, "event-3", "{}"));
    }

    @Test
    void configuresStorageSdkForSingleTry() throws ReflectiveOperationException {
        var retryOptionsField = BlobStorageService.class.getDeclaredField("SINGLE_TRY");
        retryOptionsField.setAccessible(true);
        RequestRetryOptions retryOptions = (RequestRetryOptions) retryOptionsField.get(null);

        assertEquals(1, retryOptions.getMaxTries());
    }
}