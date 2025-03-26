package com.chtrembl.petstoreapp.service;

import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Pet;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;

import java.util.Collection;
import java.util.List;

public interface PetStoreService {
	Collection<Pet> getPets(String category);

	Collection<Product> getProducts(String category, List<Tag> tags) throws Exception;

	void updateOrder(long productId, int quantity, boolean completeOrder);

	Order retrieveOrder(String orderId);
}
