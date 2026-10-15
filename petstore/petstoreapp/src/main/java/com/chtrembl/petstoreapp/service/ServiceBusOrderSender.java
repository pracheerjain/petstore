package com.chtrembl.petstoreapp.service;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "petstore.messaging", name = "enabled", havingValue = "true")
public class ServiceBusOrderSender {

    @Value("${AZURE_SERVICE_BUS_CONNECTION_STRING:}")
    private String connectionString;

    @Value("${petstore.messaging.queue-name:order-items-queue}")
    private String queueName;

    private ServiceBusSenderClient senderClient;

    @PostConstruct
    public void init() {
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException(
                    "Messaging is enabled but AZURE_SERVICE_BUS_CONNECTION_STRING is not configured");
        }
        this.senderClient = new ServiceBusClientBuilder().connectionString(connectionString)
                .sender().queueName(queueName).buildClient();
        log.info("ServiceBusSenderClient initialized for queue: {}", queueName);
    }

    public void sendOrderMessage(String orderJson, String eventId, String sessionId, long cartRevision) {
        ServiceBusMessage message = new ServiceBusMessage(orderJson);
        message.setMessageId(eventId);
        message.setSessionId(sessionId);
        message.setCorrelationId(sessionId);
        message.setContentType("application/json");
        message.getApplicationProperties().put("schemaVersion", 1);
        message.getApplicationProperties().put("cartRevision", cartRevision);
        message.getApplicationProperties().put("eventType", "CartSnapshot");
        senderClient.sendMessage(message);
        log.info("Sent cart snapshot event {} revision {} for session {}", eventId, cartRevision, sessionId);
    }

    @PreDestroy
    public void close() {
        if (senderClient != null) {
            senderClient.close();
        }
    }
}
