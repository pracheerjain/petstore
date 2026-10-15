package com.chtrembl.petstore.reserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @JsonProperty("schemaVersion")
    private Integer schemaVersion;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("cartRevision")
    private Long cartRevision;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("id")
    private String id;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("status")
    private String status;

    @JsonProperty("complete")
    private boolean complete;

    @JsonProperty("products")
    private List<ProductItem> products;

    @JsonProperty("processedAt")
    private String processedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("category")
        private CategoryItem category;

        @JsonProperty("photoURL")
        private String photoURL;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryItem {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;
    }

    public void ensureProducts() {
        if (products == null) {
            products = new ArrayList<>();
        }
    }

    public static OrderRequest fromJson(String json, com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        return mapper.readValue(json, OrderRequest.class);
    }
}
