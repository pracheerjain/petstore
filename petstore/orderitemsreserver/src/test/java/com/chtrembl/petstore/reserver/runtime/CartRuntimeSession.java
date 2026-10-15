package com.chtrembl.petstore.reserver.runtime;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CartRuntimeSession implements AutoCloseable {
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final String baseUrl = required("MODULE09_APP_URL");
    private final ObjectMapper json = new ObjectMapper();
    private final BlobClient blob;
    final String sessionId;

    CartRuntimeSession() throws Exception {
        assertTrue(baseUrl.startsWith("https://"), "Live tests require HTTPS");
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/cart"))
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("Sorry, we couldn't load your cart."), "Cart rendered an error view");
        sessionId = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        blob = new BlobServiceClientBuilder().connectionString(required("AZURE_STORAGE_CONNECTION_STRING"))
                .buildClient().getBlobContainerClient("order-requests").getBlobClient(sessionId + ".json");
    }

    void mutate(long productId, String operator) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/updatecart"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString("productId=" + productId + "&operator=" + operator)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode(), "Cart mutation did not return its expected success redirect");
    }

    JsonNode awaitRevision(long revision) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                JsonNode snapshot = json.readTree(blob.downloadContent().toString());
                if (snapshot.path("cartRevision").asLong() >= revision) {
                    assertEquals(revision, snapshot.path("cartRevision").asLong());
                    assertEquals(sessionId, snapshot.path("sessionId").asText());
                    assertTrue(snapshot.hasNonNull("processedAt"));
                    assertEquals("application/json", blob.getProperties().getContentType());
                    assertEquals(Long.toString(revision), blob.getProperties().getMetadata().get("cartrevision"));
                    return snapshot;
                }
            } catch (BlobStorageException error) {
                if (error.getStatusCode() != 404) {
                    throw error;
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("No persisted cart snapshot at the expected revision within 120 seconds");
    }

    void assertInvalidStorageKeyRejected() {
        String connection = required("AZURE_STORAGE_CONNECTION_STRING");
        String invalidConnection = connection.replaceFirst("AccountKey=[^;]+",
                "AccountKey=" + java.util.Base64.getEncoder().encodeToString(new byte[64]));
        assertFalse(connection.equals(invalidConnection), "Expected an account-key connection string");
        BlobClient unauthorized = new BlobServiceClientBuilder().connectionString(invalidConnection)
                .retryOptions(new RequestRetryOptions(RetryPolicyType.FIXED, 1, Duration.ofSeconds(15),
                        Duration.ofMillis(1), Duration.ofMillis(1), null))
                .buildClient().getBlobContainerClient("order-requests").getBlobClient(sessionId + ".json");
        BlobStorageException error = org.junit.jupiter.api.Assertions.assertThrows(BlobStorageException.class,
                unauthorized::getProperties);
        assertEquals(403, error.getStatusCode());
    }

    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for live tests");
        }
        return value;
    }

    @Override
    public void close() {
        blob.deleteIfExists();
    }
}
