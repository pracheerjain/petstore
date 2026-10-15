package com.chtrembl.petstore.reserver.function;

import com.chtrembl.petstore.reserver.model.OrderRequest;
import com.chtrembl.petstore.reserver.service.BlobStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;

import java.time.Instant;
import java.nio.charset.StandardCharsets;

/**
 * OrderItemsReserver Azure Function.
 *
 * Trigger: Azure Service Bus Queue ("order-items-queue")
 * Action:  Deserializes the order message, enriches it with a timestamp,
 *          and uploads it as a JSON blob to Azure Blob Storage.
 *          The blob is named after the customer's session ID so it is
 *          overwritten on every cart update within the same session.
 *
 * Retry policy: The Service Bus queue's max delivery count is 3 total deliveries.
 *               After three failed deliveries the broker moves the message to the
 *               Dead-Letter Queue (DLQ), where the Logic App picks it up.
 *
 * Blob Storage retry: BlobStorageService makes one upload attempt per delivery so the
 *                     Service Bus delivery count is the only processing retry budget.
 */
public class OrderItemsReserverFunction {

    private static final int MAX_MESSAGE_BYTES = 200 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final BlobStorageService blobStorageService;

    public OrderItemsReserverFunction() {
        this(new BlobStorageService());
    }

    OrderItemsReserverFunction(BlobStorageService blobStorageService) {
        this.blobStorageService = blobStorageService;
    }

    @FunctionName("OrderItemsReserver")
    public void run(
            @ServiceBusQueueTrigger(
                    name = "message",
                    queueName = "%SERVICE_BUS_QUEUE_NAME%",
                    connection = "ServiceBusConnection",
                    isSessionsEnabled = true
            ) String messageBody,
            final ExecutionContext context) {
        String sessionId = null;
        String eventId = null;
        Long revision = null;

        try {
            if (messageBody == null || messageBody.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Order snapshot is empty or exceeds the supported payload size");
            }
            OrderRequest orderRequest = OBJECT_MAPPER.readValue(messageBody, OrderRequest.class);
            sessionId = orderRequest.getSessionId();
            eventId = orderRequest.getEventId();
            revision = orderRequest.getCartRevision();

            if (!Integer.valueOf(1).equals(orderRequest.getSchemaVersion())) {
                throw new IllegalArgumentException("Unsupported order snapshot schema version");
            }
            if (sessionId == null || sessionId.isBlank() || !sessionId.equals(orderRequest.getId())) {
                throw new IllegalArgumentException("Order snapshot sessionId is missing or does not match id");
            }
            if (eventId == null || eventId.isBlank() || revision == null || revision < 1) {
                throw new IllegalArgumentException("Order snapshot event ID or revision is invalid");
            }
            orderRequest.ensureProducts();
            if (orderRequest.getProducts().stream().anyMatch(product -> product.getQuantity() == null
                    || product.getQuantity() < 1)) {
                throw new IllegalArgumentException("Order snapshot contains an invalid product quantity");
            }

            orderRequest.setProcessedAt(Instant.now().toString());
            String enrichedJson = OBJECT_MAPPER.writeValueAsString(orderRequest);
            blobStorageService.uploadOrderJson(sessionId, revision, eventId, enrichedJson);

            context.getLogger().info(String.format("Stored event %s revision %d for session %s (invocation %s)",
                    eventId, revision, sessionId, context.getInvocationId()));

        } catch (Exception e) {
            // Throwing an exception causes the Service Bus SDK to NOT complete the message.
            // The queue will redeliver it (up to maxDeliveryCount times) and then
            // dead-letter it automatically — which the Logic App monitors.
                context.getLogger().severe(String.format("Failed event %s revision %s for session %s (invocation %s): %s",
                    eventId, revision, sessionId, context.getInvocationId(), e.getClass().getSimpleName()));
            throw new RuntimeException("OrderItemsReserver processing failed", e);
        }
    }
}
