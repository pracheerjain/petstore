package com.chtrembl.petstore.reserver.function;

import com.chtrembl.petstore.reserver.service.BlobStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderItemsReserverFunctionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSnapshotAndPersistsCompleteContractWithProcessedAt() throws Exception {
        BlobStorageService blobStorageService = mock(BlobStorageService.class);
        OrderItemsReserverFunction function = new OrderItemsReserverFunction(blobStorageService);
        ExecutionContext context = mockContext();

        function.run(snapshotJson(), context);

        var jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(blobStorageService).uploadOrderJson(eq("session-1"), eq(8L), eq("event-8"), jsonCaptor.capture());
        JsonNode stored = objectMapper.readTree(jsonCaptor.getValue());
        assertEquals("2026-10-12T10:00:00Z", stored.get("updatedAt").asText());
        assertEquals(2, stored.get("products").size());
        assertNotNull(stored.get("processedAt"));
    }

    @Test
    void rejectsMismatchedSessionAndUnsupportedSchemaBeforeStorage() {
        BlobStorageService blobStorageService = mock(BlobStorageService.class);
        OrderItemsReserverFunction function = new OrderItemsReserverFunction(blobStorageService);
        ExecutionContext context = mockContext();

        assertThrows(RuntimeException.class, () -> function.run(
            snapshotJson().replace("\"sessionId\":\"session-1\"", "\"sessionId\":\"other\""), context));
        assertThrows(RuntimeException.class, () -> function.run(snapshotJson().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), context));

        verify(blobStorageService, never()).uploadOrderJson(any(), any(Long.class), any(), any());
    }

    @Test
    void allowsAnonymousEmptyCartSnapshot() {
        BlobStorageService blobStorageService = mock(BlobStorageService.class);
        OrderItemsReserverFunction function = new OrderItemsReserverFunction(blobStorageService);
        String emptyCart = """
                {"schemaVersion":1,"eventId":"event-empty","sessionId":"session-empty","cartRevision":1,
                 "updatedAt":"2026-10-12T10:00:00Z","id":"session-empty","email":null,"complete":true,"products":[]}
                """;

        function.run(emptyCart, mockContext());

        verify(blobStorageService).uploadOrderJson(eq("session-empty"), eq(1L), eq("event-empty"), any());
    }

    @Test
    void rejectsMalformedAndInvalidSnapshotsBeforeStorage() {
        BlobStorageService blobStorageService = mock(BlobStorageService.class);
        OrderItemsReserverFunction function = new OrderItemsReserverFunction(blobStorageService);
        String emptySession = snapshotJson().replace("\"sessionId\":\"session-1\"", "\"sessionId\":\" \"");
        String invalidQuantity = snapshotJson().replace("\"quantity\":1", "\"quantity\":0");
        String oversized = " ".repeat(200 * 1024 + 1);

        for (String invalidSnapshot : new String[] {"{", emptySession, invalidQuantity, oversized}) {
            assertThrows(RuntimeException.class, () -> function.run(invalidSnapshot, mockContext()));
        }

        verify(blobStorageService, never()).uploadOrderJson(any(), any(Long.class), any(), any());
    }

    private ExecutionContext mockContext() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.getLogger()).thenReturn(Logger.getLogger("test-function"));
        when(context.getInvocationId()).thenReturn("invocation-test");
        return context;
    }

    private String snapshotJson() {
        return """
                {"schemaVersion":1,"eventId":"event-8","sessionId":"session-1","cartRevision":8,
                 "updatedAt":"2026-10-12T10:00:00Z","id":"session-1","email":null,"status":"placed","complete":false,
                 "products":[{"id":1,"name":"Bird","quantity":1,"category":{"id":3,"name":"Cat"},"photoURL":"bird.jpg"},
                             {"id":2,"name":"Fish","quantity":2,"category":null,"photoURL":null}]}
                """;
    }
}