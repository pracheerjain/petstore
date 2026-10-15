package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.client.OrderServiceClient;
import com.chtrembl.petstoreapp.exception.OrderPublicationException;
import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.User;
import com.chtrembl.petstoreapp.telemetry.PetStoreTelemetryClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderManagementServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user;
    private OrderServiceClient orderServiceClient;
    private ServiceBusOrderSender sender;
    private OrderManagementService service;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setSessionId("session-1");
        user.setTelemetryClient(mock(PetStoreTelemetryClient.class));
        user.setContainerEnvironment(mock(com.chtrembl.petstoreapp.model.ContainerEnvironment.class));
        orderServiceClient = mock(OrderServiceClient.class);
        sender = mock(ServiceBusOrderSender.class);
        service = new OrderManagementService(user, orderServiceClient);
        ReflectionTestUtils.setField(service, "serviceBusOrderSender", sender);
    }

    @Test
    void publishesFullAcceptedSnapshotAfterFeignMutation() throws Exception {
        Order acceptedOrder = acceptedOrder();
        when(orderServiceClient.createOrUpdateOrder(anyString())).thenAnswer(invocation -> {
            JsonNode delta = objectMapper.readTree((String) invocation.getArgument(0));
            assertEquals(1, delta.get("products").size());
            return acceptedOrder;
        });

        service.updateOrder(1, -999, false);

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender).sendOrderMessage(messageCaptor.capture(), anyString(),
                org.mockito.ArgumentMatchers.eq("session-1"), org.mockito.ArgumentMatchers.eq(1L));
        JsonNode snapshot = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(1, snapshot.get("schemaVersion").asInt());
        assertEquals("session-1", snapshot.get("sessionId").asText());
        assertEquals(2, snapshot.get("products").size());
        assertEquals("Cat", snapshot.at("/products/0/category/name").asText());
        assertEquals(1, snapshot.at("/products/0/quantity").asInt());
    }

    @Test
    void allocatesDistinctEventsAndIncreasingRevisionsForSameSession() {
        when(orderServiceClient.createOrUpdateOrder(anyString())).thenReturn(acceptedOrder());

        service.updateOrder(1, 1, false);
        service.updateOrder(2, 1, false);

        var eventIds = org.mockito.ArgumentCaptor.forClass(String.class);
        var revisions = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(sender, times(2)).sendOrderMessage(anyString(), eventIds.capture(),
                org.mockito.ArgumentMatchers.eq("session-1"), revisions.capture());
        assertNotEquals(eventIds.getAllValues().get(0), eventIds.getAllValues().get(1));
        assertEquals(List.of(1L, 2L), revisions.getAllValues());
    }

    @Test
    void doesNotPublishWhenOrderServiceRejectsMutation() {
        when(orderServiceClient.createOrUpdateOrder(anyString()))
                .thenThrow(new IllegalStateException("order service unavailable"));

        assertThrows(RuntimeException.class, () -> service.updateOrder(1, 1, false));

        verify(sender, never()).sendOrderMessage(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void distinguishesPublicationFailureAfterAcceptedMutation() {
        when(orderServiceClient.createOrUpdateOrder(anyString())).thenReturn(acceptedOrder());
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(sender).sendOrderMessage(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());

        assertThrows(OrderPublicationException.class, () -> service.updateOrder(1, 1, false));
    }

    private Order acceptedOrder() {
        Product first = new Product();
        first.setId(1L);
        first.setName("Bird");
        first.setQuantity(1);
        first.setCategory(new com.chtrembl.petstoreapp.model.Category(3L, "Cat"));
        first.setPhotoURL("bird.jpg");
        Product second = new Product();
        second.setId(2L);
        second.setName("Fish");
        second.setQuantity(2);

        Order order = new Order();
        order.setId("session-1");
        order.setProducts(List.of(first, second));
        order.setComplete(false);
        return order;
    }
}