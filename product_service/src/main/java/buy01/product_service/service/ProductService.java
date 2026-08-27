package buy01.product_service.service;

import buy01.product_service.client.MediaClient;
import buy01.product_service.exceptions.ForbiddenException;
import buy01.product_service.model.Product;
import buy01.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;


import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final MediaClient mediaClient;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif", "image/x-avif");
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final int MAX_IMAGES_COUNT = 5;

    public List<Product> getAllProducts() {
        return repository.findAll();
    }

    public Product getProduct(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public Product createProduct(
            String name,
            String description,
            Double price,
            Integer quantity,
            MultipartFile[] images,
            String userId,
            String userRole) {

        validateUserData(userId);

        if (!"SELLER".equalsIgnoreCase(userRole)) {
            throw new ForbiddenException("You do not have permission to perform this action.");
        }

        validateProductDetails(name, description, price, quantity);

        List<String> imageUrls = new ArrayList<>();
        if (hasValidImages(images)) {
            validateImages(images);
            imageUrls = mediaClient.uploadImages(images);
        }
        Product product = Product.builder()
                .name(name.trim())
                .description(description.trim())
                .price(price)
                .quantity(quantity)
                .userId(userId)
                .imageUrls(imageUrls)
                .build();

        return repository.save(product);
    }

    public Product updateProduct(
            String id,
            String name,
            String description,
            Double price,
            Integer quantity,
            List<String> existingImageUrls,
            MultipartFile[] newImages,
            String userId,
            String userRole) {

        Product product = getProduct(id);
        verifyOwnership(product, userId);

        validateProductDetails(name, description, price, quantity);

        product.setName(name.trim());
        product.setDescription(description.trim());
        product.setPrice(price);
        product.setQuantity(quantity);

        // 1. Start with remaining existing URLs passed from frontend
        List<String> finalImageUrls = new ArrayList<>();
        if (existingImageUrls != null) {
            finalImageUrls.addAll(existingImageUrls);
        }

        // 2. Upload and append any newly added images
        if (hasValidImages(newImages)) {
            validateImages(newImages);
            List<String> newlyUploadedUrls = mediaClient.uploadImages(newImages);
            finalImageUrls.addAll(newlyUploadedUrls);
        }

        // 3. Enforce maximum total allowed images limit across both existing and new
        if (finalImageUrls.size() > MAX_IMAGES_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total images cannot exceed " + MAX_IMAGES_COUNT + " per product.");
        }

        product.setImageUrls(finalImageUrls);

        return repository.save(product);
    }

    public void deleteProduct(String id, String userId, String userRole) {
        Product product = getProduct(id);
        verifyOwnership(product, userId);
        repository.delete(product);
    }

    public void deleteProductsByUserId(String userId) {
        System.out.println("DELETAAAAAAAAAAWWWWWWWWWWWWWWWWWWWWWWWWWW :" + userId);
        if (userId != null && !userId.isBlank()) {
            repository.deleteByUserId(userId);
        }
    }

    private void verifyOwnership(Product product, String userId) {
        validateUserData(userId);

        boolean isOwner = product.getUserId() != null && product.getUserId().equals(userId);

        if (!isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Unauthorized action");
        }
    }

    private void validateUserData(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context missing or unauthenticated");
        }
    }

    private void validateProductDetails(String name, String description, Double price, Integer quantity) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required.");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < 3 || trimmedName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Product name must be between 3 and 100 characters.");
        }

        if (description == null || description.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required.");
        }
        String trimmedDesc = description.trim();
        if (trimmedDesc.length() < 10 || trimmedDesc.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Description must be between 10 and 1000 characters.");
        }

        if (price == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price is required.");
        }
        if (price < 0.01) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be at least 0.01 DH.");
        }
        if (price > 9999999.99) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot exceed 9,999,999.99 DH.");
        }
        if (BigDecimal.valueOf(price).scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot have more than 2 decimal places.");
        }

        if (quantity == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity is required.");
        }
        if (quantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity cannot be negative.");
        }
        if (quantity > 999999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity cannot exceed 999,999 units.");
        }
    }

    private void validateImages(MultipartFile[] images) {
        if (!hasValidImages(images)) {
            return;
        }

        if (images.length > MAX_IMAGES_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_IMAGES_COUNT + " images allowed per product.");
        }

        for (MultipartFile file : images) {
            if (file.isEmpty()) {
                continue;
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid file type for '" + file.getOriginalFilename()
                                + "'. Only JPG, PNG, WEBP, GIF, and AVIF are allowed.");
            }

            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File '" + file.getOriginalFilename() + "' exceeds the 2MB size limit.");
            }
        }
    }

    private boolean hasValidImages(MultipartFile[] images) {
        return images != null && images.length > 0 && !images[0].isEmpty();
    }
}