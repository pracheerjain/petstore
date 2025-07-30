package com.chtrembl.petstore.product.repository;

import com.chtrembl.petstore.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PetRepository extends JpaRepository<Product, Long> {
    // Define additional query methods if needed
}
