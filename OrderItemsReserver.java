package com.function.training;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with HTTP Trigger.
 */
public class OrderItemsReserver {

    private static final String CONNECTION_STRING = System.getenv("AzureWebJobsStorage");
    private static final String CONTAINER_NAME = "json-container";
    /**
     * This function listens at endpoint "/api/OrderItemsReserver". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/OrderItemsReserver
     * 2. curl {your host}/api/OrderItemsReserver?name=HTTP%20Query
     */
    @FunctionName("OrderItemsReserver")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        String jsonPayload = request.getBody().get();
         if (jsonPayload == null || jsonPayload.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid request: JSON payload is required.")
                    .build();
        }

        try {
            // Create the BlobServiceClient
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(CONNECTION_STRING)
                    .buildClient();

            // Get or create the container
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
            if (!containerClient.exists()) {
                containerClient.create();
            }

            // Generate a unique blob name
            String blobName = "json-" + UUID.randomUUID() + ".json";

            // Upload the blob
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            try (InputStream dataStream = new ByteArrayInputStream(jsonPayload.getBytes())) {
                blobClient.upload(dataStream, jsonPayload.length(), true);
            }

            context.getLogger().info("JSON data successfully stored in blob storage.");
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("JSON stored successfully in blob: " + blobName)
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Error storing JSON in blob: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred: " + e.getMessage())
                    .build();
        }
    }
}
