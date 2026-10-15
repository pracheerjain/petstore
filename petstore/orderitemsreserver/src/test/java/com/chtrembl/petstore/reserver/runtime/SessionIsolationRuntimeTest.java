package com.chtrembl.petstore.reserver.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@EnabledIfEnvironmentVariable(named = "MODULE09_LIVE_TEST", matches = "true")
class SessionIsolationRuntimeTest {
    @Test
    void separateSessionsPersistDifferentFilesWithoutOverwritingEachOther() throws Exception {
        try (CartRuntimeSession first = new CartRuntimeSession(); CartRuntimeSession second = new CartRuntimeSession()) {
            assertNotEquals(first.sessionId, second.sessionId);
            first.mutate(1, "plus");
            second.mutate(2, "plus");
            assertEquals(1, first.awaitRevision(1).path("products").get(0).path("id").asLong());
            assertEquals(2, second.awaitRevision(1).path("products").get(0).path("id").asLong());
            first.assertInvalidStorageKeyRejected();
            first.mutate(1, "plus");
            assertEquals(2, first.awaitRevision(2).path("products").get(0).path("quantity").asInt());
            assertEquals(1, second.awaitRevision(1).path("products").get(0).path("quantity").asInt());
        }
    }
}
