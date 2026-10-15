package com.chtrembl.petstore.reserver.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnabledIfEnvironmentVariable(named = "MODULE09_LIVE_TEST", matches = "true")
class EmptyCartRuntimeTest {
    @Test
    void emptyCartOverwritesTheSameBlobAndValidCredentialsStillWorkAfterRejection() throws Exception {
        try (CartRuntimeSession cart = new CartRuntimeSession()) {
            cart.mutate(1, "plus");
            String firstEvent = cart.awaitRevision(1).path("eventId").asText();
            cart.assertInvalidStorageKeyRejected();
            cart.mutate(1, "remove");
            var empty = cart.awaitRevision(2);
            assertEquals(0, empty.path("products").size());
            assertFalse(firstEvent.equals(empty.path("eventId").asText()));
            cart.mutate(2, "plus");
            assertEquals(2, cart.awaitRevision(3).path("products").get(0).path("id").asLong());
        }
    }
}
