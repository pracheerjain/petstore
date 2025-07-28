package com.chtrembl.petstore.order.service;

import com.chtrembl.petstore.order.exception.OrderNotFoundException;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.Product;
import com.chtrembl.petstore.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
//@RequiredArgsConstructor
public class OrderService {

    private static final String ORDERS = "orders";
//    private final CacheManager cacheManager;

    @Autowired
    private final ProductService productService;

    @Autowired
    private OrderRepository orderRepository;

    public OrderService(ProductService productService) {
        this.productService = productService;
    }

    //    @Cacheable(ORDERS)
    public Order createOrder(String orderId) {
        log.info("Creating new order with id: {} and caching it", orderId);
        return Order.builder()
                .id(orderId)
                .products(new ArrayList<>())
                .status(Order.Status.PLACED)
                .complete(false)
                .build();
    }

    /**
     * Retrieves an existing order by ID. Does NOT create a new order if not found.
     *
     * @param orderId the order ID to retrieve
     * @return the existing order
     * @throws OrderNotFoundException if order does not exist
     */
    public Order getOrderById(String orderId) {
        log.info("Retrieving order from repository: {}", orderId);

        // Validate orderId (not covered by Bean Validation for path variables)
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }

        // Try to get from cosmos DB
        Order orderById = getOrderByOrderId(orderId);
        if (orderById != null) return orderById;

//        // Try to get from cache
//        Cache cache = cacheManager.getCache(ORDERS);
//        if (cache != null) {
//            Cache.ValueWrapper wrapper = cache.get(orderId);
//            if (wrapper != null) {
//                Order existingOrder = (Order) wrapper.get();
//                if (existingOrder != null) {
//                    log.info("Found existing order: {}", orderId);
//                    return existingOrder;
//                }
//            }
//        }

        // Order not found - throw exception instead of creating new one
        log.warn("Order not found: {}", orderId);
        throw new OrderNotFoundException("Order with ID " + orderId + " not found");
    }

    private Order getOrderByOrderId(String orderId) {
        Optional<Order> orderById= orderRepository.findById(orderId);

        if (orderById.isPresent()){
            log.info("Found existing order: {}", orderId);
            return orderById.get();
        }
        return null;
    }

    /**
     * Gets an existing order or creates a new one if it doesn't exist.
     * Used internally for order updates.
     */
    public Order getOrCreateOrder(String orderId) {
        log.info("Getting or creating order: {}", orderId);

        // Try to get from cosmos DB first
        Order orderById = getOrderByOrderId(orderId);
        if (orderById != null) return orderById;

//        // Try to get from cache first
//        Cache cache = cacheManager.getCache(ORDERS);
//        if (cache != null) {
//            Cache.ValueWrapper wrapper = cache.get(orderId);
//            if (wrapper != null) {
//                Order existingOrder = (Order) wrapper.get();
//                if (existingOrder != null) {
//                    log.info("Found existing order for update: {}", orderId);
//                    return existingOrder;
//                }
//            }
//        }

        // Create new order if not found
        log.info("Creating new order for update: {}", orderId);
        Order newOrder = createOrder(orderId);
        orderRepository.save(newOrder);
//        if (cache != null) {
//            cache.put(orderId, newOrder);
//        }

        return newOrder;
    }

    public Order updateOrder(Order order) {
        log.info("Updating order: {}", order.getId());

        // Validate products exist before processing order
        if (order.getProducts() != null && !order.getProducts().isEmpty()) {
            List<Product> availableProducts = productService.getAvailableProducts();
            validateProductsExist(order.getProducts(), availableProducts);
        }

        // Use getOrCreateOrder for updates (allows creation)
        Order existingOrder = getOrCreateOrder(order.getId());

        // Update basic fields
        existingOrder.setEmail(order.getEmail());

        // Update status only if new status is provided
        if (order.getStatus() != null) {
            existingOrder.setStatus(order.getStatus());
        }

        // Handle completion status
        Boolean isComplete = order.getComplete();
        if (isComplete != null && isComplete) {
            log.info("Completing order {} - clearing products", order.getId());
            existingOrder.setProducts(new ArrayList<>());
            existingOrder.setComplete(true);
        } else {
            existingOrder.setComplete(isComplete != null ? isComplete : false);
            updateOrderProducts(existingOrder, order.getProducts());
        }

        // Explicitly update cosmos DB
        orderRepository.save(existingOrder);

        // Explicitly update cache
//        Cache cache = cacheManager.getCache(ORDERS);
//        if (cache != null) {
//            cache.put(order.getId(), existingOrder);
//        }

        return existingOrder;
    }

    /**
     * Validates that all products in the order exist in the available products list
     *
     * @param orderProducts List of products from the order
     * @param availableProducts List of available products from Product Service
     * @throws IllegalArgumentException if any product is not found
     */
    private void validateProductsExist(List<Product> orderProducts, List<Product> availableProducts) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            return;
        }

        List<Long> requestedProductIds = orderProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> availableProductIds = availableProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> missingProductIds = requestedProductIds.stream()
                .filter(id -> !availableProductIds.contains(id))
                .collect(Collectors.toList());

        if (!missingProductIds.isEmpty()) {
            String errorMessage = String.format("Products with IDs %s are not available or do not exist",
                    missingProductIds);
            log.warn("Product validation failed for order: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.debug("Product validation passed for {} products", requestedProductIds.size());
    }

    private void updateOrderProducts(Order existingOrder, List<Product> incomingProducts) {
        if (incomingProducts == null || incomingProducts.isEmpty()) {
            return;
        }

        // Single product update (add/remove/update from product page)
        if (incomingProducts.size() == 1) {
            handleSingleProductUpdate(existingOrder, incomingProducts.stream().findFirst().get());
        }
        // Multiple products (cart update)
        else {
            existingOrder.setProducts(new ArrayList<>(incomingProducts));
        }
    }

    private void handleSingleProductUpdate(Order existingOrder, Product incomingProduct) {
        List<Product> existingProducts = existingOrder.getProducts();
        if (existingProducts == null) {
            existingProducts = new ArrayList<>();
            existingOrder.setProducts(existingProducts);
        }

        Integer quantity = incomingProduct.getQuantity();

        // Find existing product
        Optional<Product> existingProductOpt = existingProducts.stream()
                .filter(p -> p.getId().equals(incomingProduct.getId()))
                .findFirst();

        if (existingProductOpt.isPresent()) {
            // Update existing product quantity
            Product existingProduct = existingProductOpt.get();
            int currentQuantity = existingProduct.getQuantity();
            int newQuantity = currentQuantity + quantity;

            log.info("Updating product {} quantity: {} + {} = {}",
                    incomingProduct.getId(), currentQuantity, quantity, newQuantity);

            if (newQuantity <= 0) {
                existingProducts.removeIf(p -> p.getId().equals(incomingProduct.getId()));
                log.info("Removed product {} from order {} (quantity became {})",
                        incomingProduct.getId(), existingOrder.getId(), newQuantity);
            } else if (newQuantity <= 10) { // Max quantity limit
                existingProduct.setQuantity(newQuantity);
                log.info("Updated product {} quantity to {} in order {}",
                        incomingProduct.getId(), newQuantity, existingOrder.getId());
            } else {
                // Cap at maximum quantity
                existingProduct.setQuantity(10);
                log.warn("Quantity capped at maximum (10) for product {} in order {}",
                        incomingProduct.getId(), existingOrder.getId());
            }
        } else {
            // Add new product only if quantity is positive
            if (quantity > 0) {
                int finalQuantity = Math.min(quantity, 10); // Ensure max limit
                existingProducts.add(Product.builder()
                        .id(incomingProduct.getId())
                        .quantity(finalQuantity)
                        .name(incomingProduct.getName())
                        .photoURL(incomingProduct.getPhotoURL())
                        .build());

                log.info("Added new product {} with quantity {} to order {}",
                        incomingProduct.getId(), finalQuantity, existingOrder.getId());

                if (quantity > 10) {
                    log.warn("Quantity reduced to maximum (10) for new product {} in order {}",
                            incomingProduct.getId(), existingOrder.getId());
                }
            } else {
                log.info("Ignoring request to add product {} with non-positive quantity {} to order {}",
                        incomingProduct.getId(), quantity, existingOrder.getId());
            }
        }
    }

    public void enrichOrderWithProductDetails(Order order, List<Product> availableProducts) {
        if (order.getProducts() == null || availableProducts == null) {
            log.warn("Cannot enrich order: order.products={}, availableProducts={}",
                    order.getProducts(), availableProducts != null ? availableProducts.size() : "null");
            return;
        }

        log.info("Enriching order {} with {} available products",
                order.getId(), availableProducts.size());

        for (Product orderProduct : order.getProducts()) {
            String originalName = orderProduct.getName();
            String originalURL = orderProduct.getPhotoURL();

            Optional<Product> foundProduct = availableProducts.stream()
                    .filter(p -> p.getId().equals(orderProduct.getId()))
                    .findFirst();

            if (foundProduct.isPresent()) {
                Product availableProduct = foundProduct.get();
                orderProduct.setName(availableProduct.getName());
                orderProduct.setPhotoURL(availableProduct.getPhotoURL());

                log.info("Enriched product {}: '{}' -> '{}', URL: '{}' -> '{}'",
                        orderProduct.getId(), originalName, availableProduct.getName(),
                        originalURL, availableProduct.getPhotoURL());
            } else {
                log.warn("Product with id {} not found in available products during enrichment",
                        orderProduct.getId());
            }
        }
    }
    
    private List<Order> getAllOrders(){
        return (List<Order>) orderRepository.findAll();
    }
}