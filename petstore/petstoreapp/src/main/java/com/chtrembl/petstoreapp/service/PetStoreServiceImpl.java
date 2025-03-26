package com.chtrembl.petstoreapp.service;

/**
 * Implementation for service calls to the APIM/AKS
 */

import com.chtrembl.petstoreapp.model.Category;
import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.Order;
import com.chtrembl.petstoreapp.model.Pet;
import com.chtrembl.petstoreapp.model.Product;
import com.chtrembl.petstoreapp.model.Tag;
import com.chtrembl.petstoreapp.model.User;
import com.chtrembl.petstoreapp.model.WebRequest;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PetStoreServiceImpl implements PetStoreService {
	private static final Logger logger = LoggerFactory.getLogger(PetStoreServiceImpl.class);

	private final User sessionUser;
	private final ContainerEnvironment containerEnvironment;
	private final WebRequest webRequest;

	private WebClient petServiceWebClient = null;
	private WebClient productServiceWebClient = null;
	private WebClient orderServiceWebClient = null;

	public PetStoreServiceImpl(User sessionUser, ContainerEnvironment containerEnvironment, WebRequest webRequest) {
		this.sessionUser = sessionUser;
		this.containerEnvironment = containerEnvironment;
		this.webRequest = webRequest;
	}

	@PostConstruct
	public void initialize() {
		this.petServiceWebClient = WebClient.builder()
				.baseUrl(this.containerEnvironment.getPetStorePetServiceURL())
				.build();
		this.productServiceWebClient = WebClient.builder()
				.baseUrl(this.containerEnvironment.getPetStoreProductServiceURL()).build();
		this.orderServiceWebClient = WebClient.builder().baseUrl(this.containerEnvironment.getPetStoreOrderServiceURL())
				.build();
	}

	@Override
	public Collection<Pet> getPets(String category) {
		List<Pet> pets = new ArrayList<>();

		this.sessionUser.getTelemetryClient().trackEvent(
				String.format("PetStoreApp user %s is requesting to retrieve pets from the PetStorePetService",
						this.sessionUser.getName()),
				this.sessionUser.getCustomEventProperties(), null);
		try {
			Consumer<HttpHeaders> consumer = it -> it.addAll(this.webRequest.getHeaders());
			pets = this.petServiceWebClient.get().uri("petstorepetservice/v2/pet/findByStatus?status=available")
					.accept(MediaType.APPLICATION_JSON)
					.headers(consumer)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.header("Cache-Control", "no-cache")
					.retrieve()
					.bodyToMono(new ParameterizedTypeReference<List<Pet>>() {
					}).block();

			// use this for look up on details page, intentionally avoiding spring cache to
			// ensure service calls are made each for each browser session
			// to show Telemetry with APIM requests (normally this would be cached in a real
			// world production scenario)
			this.sessionUser.setPets(pets);

			// filter this specific request per category
			pets = pets.stream().filter(pet -> category.equals(pet.getCategory().getName()))
					.collect(Collectors.toList());
			return pets;
		} catch (WebClientException wce) {
			this.sessionUser.getTelemetryClient().trackException(wce);
			this.sessionUser.getTelemetryClient().trackEvent(
					String.format("PetStoreApp %s received %s, container host: %s", this.sessionUser.getName(),
							wce.getMessage(), this.containerEnvironment.getContainerHostName()));
			Pet pet = new Pet();
			pet.setName(wce.getMessage());
			pet.setPhotoURL("");
			pet.setCategory(new Category());
			pet.setId((long) 0);
			pets.add(pet);
		} catch (IllegalArgumentException iae) {
			// little hack to visually show the error message within our Azure Pet Store
			// Reference Guide (Academic Tutorial)
			Pet pet = new Pet();
			pet.setName("petstore.service.url:${PETSTOREPETSERVICE_URL} needs to be enabled for this service to work"
					+ iae.getMessage());
			pet.setPhotoURL("");
			pet.setCategory(new Category());
			pet.setId((long) 0);
			pets.add(pet);
		}
		return pets;
	}

	@Override
	public Collection<Product> getProducts(String category, List<Tag> tags) throws Exception {
		List<Product> products = new ArrayList<>();

		try {
			Consumer<HttpHeaders> consumer = it -> it.addAll(this.webRequest.getHeaders());
			products = this.productServiceWebClient.get()
					.uri("petstoreproductservice/v2/product/findByStatus?status=available")
					.accept(MediaType.APPLICATION_JSON)
					.headers(consumer)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.header("Cache-Control", "no-cache")
					.retrieve()
					.bodyToMono(new ParameterizedTypeReference<List<Product>>() {
					}).block();

			this.sessionUser.setProducts(products);

			this.sessionUser.getTelemetryClient().trackEvent(
					String.format("PetStoreApp user %s is requesting to retrieve pets from the PetStorePetService for sessionId %s",
							this.sessionUser.getName(), this.sessionUser.getSessionId()),
					this.sessionUser.getCustomEventProperties(), null);

			throw new Exception("Cannot move further");

			// filter this specific request per category
//			if (tags.stream().anyMatch(t -> t.getName().equals("large"))) {
//				products = products.stream().filter(product -> category.equals(product.getCategory().getName())
//						&& product.getTags().toString().contains("large")).collect(Collectors.toList());
//			} else {
//
//				products = products.stream().filter(product -> category.equals(product.getCategory().getName())
//						&& product.getTags().toString().contains("small")).collect(Collectors.toList());
//			}
//			logger.info("PetStoreApp user added {} no. of products in cart",products.size());
//			this.sessionUser.getTelemetryClient().trackMetric("Number of products added to cart", products.size());

//			return products;
		} catch (

		WebClientException wce) {
			// little hack to visually show the error message within our Azure Pet Store
			// Reference Guide (Academic Tutorial)
			Product product = new Product();
			product.setName(wce.getMessage());
			product.setPhotoURL("");
			product.setCategory(new Category());
			product.setId((long) 0);
			products.add(product);
		} catch (IllegalArgumentException iae) {
			// little hack to visually show the error message within our Azure Pet Store
			// Reference Guide (Academic Tutorial)
			Product product = new Product();
			product.setName(
					"petstore.service.url:${PETSTOREPRODUCTSERVICE_URL} needs to be enabled for this service to work"
							+ iae.getMessage());
			product.setPhotoURL("");
			product.setCategory(new Category());
			product.setId((long) 0);
			products.add(product);
		}
		return products;
	}

	@Override
	public void updateOrder(long productId, int quantity, boolean completeOrder) {
		this.sessionUser.getTelemetryClient()
				.trackEvent(String.format(
						"PetStoreApp user %s is requesting to update an order with the PetStoreOrderService",
						this.sessionUser.getName()), this.sessionUser.getCustomEventProperties(), null);

		try {
			Order updatedOrder = new Order();

			updatedOrder.setId(this.sessionUser.getSessionId());
			updatedOrder.setEmail(this.sessionUser.getEmail());

			if (completeOrder) {
				updatedOrder.setComplete(true);
			} else {
				List<Product> products = new ArrayList<Product>();
				Product product = new Product();
				product.setId(Long.valueOf(productId));
				product.setQuantity(quantity);
				products.add(product);
				updatedOrder.setProducts(products);
			}

			String orderJSON = new ObjectMapper().setSerializationInclusion(Include.NON_NULL)
					.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
					.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false).writeValueAsString(updatedOrder);

			Consumer<HttpHeaders> consumer = it -> it.addAll(this.webRequest.getHeaders());

			updatedOrder = this.orderServiceWebClient.post().uri("petstoreorderservice/v2/store/order")
					.body(BodyInserters.fromPublisher(Mono.just(orderJSON), String.class))
					.accept(MediaType.APPLICATION_JSON)
					.headers(consumer)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.header("Cache-Control", "no-cache")
					.retrieve()
					.bodyToMono(Order.class).block();

		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
	}

	@Override
	public Order retrieveOrder(String orderId) {
		this.sessionUser.getTelemetryClient()
				.trackEvent(String.format(
						"PetStoreApp user %s is requesting to retrieve an from with the PetStoreOrderService",
						this.sessionUser.getName()), this.sessionUser.getCustomEventProperties(), null);

		Order order = null;
		try {
			Consumer<HttpHeaders> consumer = it -> it.addAll(this.webRequest.getHeaders());

			order = this.orderServiceWebClient.get()
					.uri(uriBuilder -> uriBuilder.path("petstoreorderservice/v2/store/order/{orderId}").build(orderId))
					.accept(MediaType.APPLICATION_JSON)
					.headers(consumer)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.header("Cache-Control", "no-cache")
					.retrieve()
					.bodyToMono(new ParameterizedTypeReference<Order>() {
					}).block();
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
		return order;
	}

}
