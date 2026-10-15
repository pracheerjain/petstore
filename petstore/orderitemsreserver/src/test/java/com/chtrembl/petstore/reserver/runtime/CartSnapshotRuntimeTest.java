package com.chtrembl.petstore.reserver.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "MODULE09_LIVE_TEST", matches = "true")
class CartSnapshotRuntimeTest {
    @Test
    void fullSnapshotSurvivesAddQuantityChangeAndRemoval() throws Exception {
        try (CartRuntimeSession cart = new CartRuntimeSession()) {
            cart.mutate(1, "plus");
            assertEquals(1, cart.awaitRevision(1).path("products").size());
            cart.mutate(2, "plus");
            assertEquals(2, cart.awaitRevision(2).path("products").size());
            cart.mutate(1, "plus");
            assertEquals(2, cart.awaitRevision(3).path("products").get(0).path("quantity").asInt());
            cart.mutate(2, "remove");
            assertEquals(1, cart.awaitRevision(4).path("products").size());
            cart.assertInvalidStorageKeyRejected();
            assertEquals(1, cart.awaitRevision(4).path("products").size());
        }
    }
}
