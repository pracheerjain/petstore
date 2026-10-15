package com.chtrembl.petstore.reserver.service;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Performs one revision-conditional JSON upload per Service Bus delivery.
 */
public class BlobStorageService {

    private static final int MAX_JSON_BYTES = 200 * 1024;
    private static final String CONTENT_TYPE = "application/json";
    private static final RequestRetryOptions SINGLE_TRY = new RequestRetryOptions(
            RetryPolicyType.EXPONENTIAL, 1, Duration.ofSeconds(30), Duration.ofMillis(1),
            Duration.ofMillis(1), null);
    private volatile BlobContainerClient containerClient;
    private final Function<String, String> settings;

    public BlobStorageService() {
        this(System::getenv);
    }

    BlobStorageService(Function<String, String> settings) {
        this.settings = settings;
    }

    BlobStorageService(BlobContainerClient containerClient) {
        this();
        this.containerClient = containerClient;
    }

    /**
     * Uploads (or overwrites) the order JSON file in Blob Storage.
     * The blob name is the sessionId, ensuring one file per user session.
     *
         * @param sessionId the customer's session ID, used as the blob name
         * @param revision monotonically increasing cart revision
         * @param eventId unique accepted cart update identifier
         * @param orderJson serialized order snapshot
     */
        public void uploadOrderJson(String sessionId, long revision, String eventId, String orderJson) {
                if (sessionId == null || sessionId.isBlank() || revision < 1 || eventId == null || eventId.isBlank()) {
                        throw new IllegalArgumentException("Session, revision, and event ID are required");
                }
        String blobName = sessionId + ".json";
        byte[] content = orderJson.getBytes(StandardCharsets.UTF_8);
                if (content.length > MAX_JSON_BYTES) {
                        throw new IllegalArgumentException("Order snapshot exceeds the supported payload size");
                }

                BlobClient blobClient = getContainerClient().getBlobClient(blobName);
                BlobRequestConditions conditions = new BlobRequestConditions();
                try {
                        BlobProperties properties = blobClient.getProperties();
                        Map<String, String> metadata = properties.getMetadata();
                        long storedRevision = metadata == null ? 0
                                        : Long.parseLong(metadata.getOrDefault("cartrevision", "0"));
                        if (storedRevision >= revision) {
                                return;
                        }
                        conditions.setIfMatch(properties.getETag());
                } catch (BlobStorageException e) {
                        if (e.getStatusCode() != 404) {
                                throw e;
                        }
                        conditions.setIfNoneMatch("*");
                }

                BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(CONTENT_TYPE);
                BlobParallelUploadOptions options = new BlobParallelUploadOptions(BinaryData.fromBytes(content))
                                .setHeaders(headers)
                                .setMetadata(Map.of("cartrevision", Long.toString(revision), "eventid", eventId))
                                .setRequestConditions(conditions);
                blobClient.uploadWithResponse(options, Duration.ofSeconds(30), Context.NONE);
        }

        private BlobContainerClient getContainerClient() {
                BlobContainerClient client = containerClient;
                if (client != null) {
                        return client;
                }
                synchronized (this) {
                        if (containerClient == null) {
                                String containerName = requiredSetting("BLOB_CONTAINER_NAME");
                                String connectionString = requiredSetting("AZURE_STORAGE_CONNECTION_STRING");
                                BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                                                .retryOptions(SINGLE_TRY).connectionString(connectionString).buildClient();
                                containerClient = serviceClient.getBlobContainerClient(containerName);
                        }
                        return containerClient;
                }
        }

        private String requiredSetting(String name) {
                String value = settings.apply(name);
                if (value == null || value.isBlank()) {
                        throw new IllegalStateException(name + " environment variable is not set");
                }
                return value;
    }
}
