package com.chtrembl.petstoreapp.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ServiceBusOrderSenderTest {
    @Test
    void requiresExplicitConnectionStringWhenMessagingIsEnabled() {
        ServiceBusOrderSender sender = new ServiceBusOrderSender();
        ReflectionTestUtils.setField(sender, "connectionString", " ");

        IllegalStateException error = assertThrows(IllegalStateException.class, sender::init);

        assertTrue(error.getMessage().contains("AZURE_SERVICE_BUS_CONNECTION_STRING"));
    }

    @Test
    void rejectsMalformedConnectionStringWithoutIdentityFallback() {
        ServiceBusOrderSender sender = new ServiceBusOrderSender();
        ReflectionTestUtils.setField(sender, "connectionString", "not-a-connection-string");
        ReflectionTestUtils.setField(sender, "queueName", "order-items-queue");

        assertThrows(IllegalArgumentException.class, sender::init);
    }

    @Test
    void closesSenderOnShutdown() {
        ServiceBusSenderClient client = mock(ServiceBusSenderClient.class);
        ServiceBusOrderSender sender = new ServiceBusOrderSender();
        ReflectionTestUtils.setField(sender, "senderClient", client);

        sender.close();

        verify(client).close();
    }

    @Test
    void sendsSessionAwareSnapshotMessage() {
        ServiceBusSenderClient client = mock(ServiceBusSenderClient.class);
        ServiceBusOrderSender sender = new ServiceBusOrderSender();
        ReflectionTestUtils.setField(sender, "senderClient", client);

        sender.sendOrderMessage("{\"schemaVersion\":1}", "event-1", "session-1", 7);

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(client).sendMessage(messageCaptor.capture());
        ServiceBusMessage message = messageCaptor.getValue();
        assertEquals("event-1", message.getMessageId());
        assertEquals("session-1", message.getSessionId());
        assertEquals("session-1", message.getCorrelationId());
        assertEquals("application/json", message.getContentType());
        assertEquals(7L, message.getApplicationProperties().get("cartRevision"));
        assertEquals("CartSnapshot", message.getApplicationProperties().get("eventType"));
    }
}