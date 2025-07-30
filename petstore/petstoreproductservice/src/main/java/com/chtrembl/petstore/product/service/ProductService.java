package com.chtrembl.petstore.product.service;

import com.chtrembl.petstore.product.model.Product;
import com.chtrembl.petstore.product.repository.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProductService {

    private final PetRepository petRepository;

    public ProductService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public List<Product> findProductsByStatus(List<String> status) {
        log.info("Finding products with status: {}", status);

        return getAllProducts().stream()
                .filter(product -> status.contains(product.getStatus().toString()))
                .toList();
    }

    public Optional<Product> findProductById(Long productId) {
        log.info("Finding product with id: {}", productId);

        return getAllProducts().stream()
                .filter(product -> product.getId().equals(productId))
                .findFirst();
    }

    public List<Product> getAllProducts() {
        log.info("Getting all products");
        return petRepository.findAll();
    }

    public int getProductCount() {
        return petRepository.findAll().size();
    }
}