package com.chtrembl.petstoreapp.model;

import lombok.Data;

import java.util.List;

@Data
public class OrderSnapshot {
	private int schemaVersion;
	private String eventId;
	private String sessionId;
	private long cartRevision;
	private String updatedAt;
	private String id;
	private String email;
	private Order.Status status;
	private boolean complete;
	private List<ProductSnapshot> products;

	@Data
	public static class ProductSnapshot {
		private Long id;
		private String name;
		private Integer quantity;
		private Category category;
		private String photoURL;

		public ProductSnapshot(Long id, String name, Integer quantity, Category category, String photoURL) {
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.category = category;
			this.photoURL = photoURL;
		}
	}
}