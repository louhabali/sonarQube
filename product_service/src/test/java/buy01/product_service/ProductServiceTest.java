package buy01.product_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import buy01.product_service.client.MediaClient;
import buy01.product_service.exceptions.ForbiddenException;
import buy01.product_service.model.Product;
import buy01.product_service.repository.ProductRepository;
import buy01.product_service.service.ProductAssertions;
import buy01.product_service.service.ProductService;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

        @Mock
        private ProductRepository repository;

        @Mock
        private MediaClient mediaClient;

        @InjectMocks
        private ProductService service;

        private Product product;

        private MultipartFile image;

        @BeforeEach
        void setup() {

                image = new MockMultipartFile(
                                "image",
                                "product.jpg",
                                "image/jpeg",
                                "image".getBytes());

                product = Product.builder()
                                .id("1")
                                .name("Gaming Laptop")
                                .description("RTX 4090 Gaming Laptop")
                                .price(1999.99)
                                .quantity(5)
                                .userId("seller-1")
                                .imageUrls(List.of("img1.jpg"))
                                .build();
        }

        @Test
        void shouldReturnAllProducts() {
                List<Product> expected = List.of(product, Product.builder()
                                .id("2")
                                .name("Phone")
                                .description("Latest smartphone")
                                .price(999.99)
                                .quantity(10)
                                .userId("seller-2")
                                .imageUrls(List.of("img2.jpg"))
                                .build());

                when(repository.findAll()).thenReturn(expected);

                List<Product> result = service.getAllProducts();
                System.out.println("--------------- Expected: " + expected);
                System.out.println("--------------- Result: " + result);
                ProductAssertions.assertProductListEquals(expected, result);
                verify(repository).findAll();
        }
        //test without imagess
        @Test
        void shouldCreateProductWithImages() {
                Product expected = Product.builder()
                                .id("1")
                                .name("Gaming Laptop")
                                .description("RTX 4090 Gaming Laptop")
                                .price(1999.99)
                                .quantity(5)
                                .userId("seller-1")
                                .imageUrls(null)
                                .build();

                when(mediaClient.uploadImages(any(MultipartFile[].class)))
                                .thenReturn(List.of("uploaded.jpg"));
                when(repository.save(any(Product.class)))
                                .thenReturn(expected);

                Product result = service.createProduct(
                                "Gaming Laptop",
                                "RTX 4090 Gaming Laptop",
                                1999.99,
                                5,
                                // new MultipartFile[] { image },
                                new MultipartFile[] { image },
                                "seller-1",
                                "SELLER");
                
                ProductAssertions.assertProductEquals(expected, result);
                verify(mediaClient).uploadImages(any(MultipartFile[].class));
                verify(repository).save(any(Product.class));
        }

        @Test
        void shouldUpdateProductWithoutUploadingNewImages() {
                when(repository.findById("1")).thenReturn(Optional.of(product));
                when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = service.updateProduct(
                                "1",
                                "Updated Laptop",
                                "Updated RTX 4090 Gaming Laptop",
                                1599.99,
                                3,
                                List.of("existing.jpg"),
                                null,
                                "seller-1",
                                "SELLER");

                assertThat(result.getName()).isEqualTo("Updated Laptop");
                assertThat(result.getDescription()).isEqualTo("Updated RTX 4090 Gaming Laptop");
                assertThat(result.getImageUrls()).containsExactly("existing.jpg");
                verify(repository).findById("1");
                verify(repository).save(any(Product.class));
                verifyNoInteractions(mediaClient);
        }

        @Test
        void shouldUpdateProductWithNewImages() {
                when(repository.findById("1")).thenReturn(Optional.of(product));
                when(mediaClient.uploadImages(any(MultipartFile[].class)))
                                .thenReturn(List.of("new-image.jpg"));
                when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

                Product result = service.updateProduct(
                                "1",
                                "Updated Laptop",
                                "Updated RTX 4090 Gaming Laptop",
                                1599.99,
                                3,
                                List.of("existing.jpg"),
                                new MultipartFile[] { image },
                                "seller-1",
                                "SELLER");

                assertThat(result.getImageUrls()).containsExactly("existing.jpg", "new-image.jpg");
                verify(mediaClient).uploadImages(any(MultipartFile[].class));
                verify(repository).save(any(Product.class));
        }

        @Test
        void shouldDeleteProduct() {
                when(repository.findById("1")).thenReturn(Optional.of(product));

                service.deleteProduct("1", "seller-1", "SELLER");

                verify(repository).delete(product);
        }

        @Test
        void shouldDeleteProductsByUserId() {
                service.deleteProductsByUserId("seller-1");

                verify(repository).deleteByUserId("seller-1");
        }

        @Test
        void shouldThrowWhenProductDoesNotExist() {
                when(repository.findById("missing")).thenReturn(Optional.empty());

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.getProduct("missing"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldThrowWhenUserIsNotSeller() {
                ForbiddenException exception = assertThrows(
                                ForbiddenException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                null,
                                                "seller-1",
                                                "BUYER"));

                assertThat(exception.getMessage()).contains("permission");
        }

        @Test
        void shouldThrowWhenUserDoesNotOwnProduct() {
                when(repository.findById("1")).thenReturn(Optional.of(product));

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.updateProduct(
                                                "1",
                                                "Updated Laptop",
                                                "Updated RTX 4090 Gaming Laptop",
                                                1599.99,
                                                3,
                                                List.of("existing.jpg"),
                                                null,
                                                "another-user",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldThrowWhenUserIdMissing() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                null,
                                                null,
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldThrowWhenNameIsBlank() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "   ",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenDescriptionTooShort() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "short",
                                                1999.99,
                                                5,
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenPriceIsNegative() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                -5.0,
                                                5,
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenPriceHasMoreThanTwoDecimals() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.999,
                                                5,
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenQuantityIsNegative() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                -1,
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenTooManyImagesUploaded() {
                MultipartFile[] files = new MultipartFile[6];
                for (int i = 0; i < files.length; i++) {
                        files[i] = image;
                }

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                files,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenImageTypeIsInvalid() {
                MultipartFile invalidImage = new MockMultipartFile(
                                "image",
                                "product.svg",
                                "image/svg+xml",
                                "image".getBytes());

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                new MultipartFile[] { invalidImage },
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenImageTooLarge() {
                MultipartFile largeImage = new MockMultipartFile(
                                "image",
                                "product.jpg",
                                "image/jpeg",
                                new byte[2 * 1024 * 1024 + 1]);

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.createProduct(
                                                "Gaming Laptop",
                                                "RTX 4090 Gaming Laptop",
                                                1999.99,
                                                5,
                                                new MultipartFile[] { largeImage },
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldThrowWhenTotalImagesExceedLimit() {
                when(repository.findById("1")).thenReturn(Optional.of(product));

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> service.updateProduct(
                                                "1",
                                                "Updated Laptop",
                                                "Updated RTX 4090 Gaming Laptop",
                                                1599.99,
                                                3,
                                                List.of("1", "2", "3", "4", "5", "6"),
                                                null,
                                                "seller-1",
                                                "SELLER"));

                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldCreateProductWithoutImages() {

                Product expected = Product.builder()
                                .id("1")
                                .name("Gaming Laptop")
                                .description("RTX 4090 Gaming Laptop")
                                .price(1999.99)
                                .quantity(5)
                                .userId("seller-1")
                                .imageUrls(List.of())
                                .build();

                when(repository.save(any(Product.class)))
                                .thenReturn(expected);

                Product result = service.createProduct(
                                "Gaming Laptop",
                                "RTX 4090 Gaming Laptop",
                                1999.99,
                                5,
                                null,
                                "seller-1",
                                "SELLER");

                ProductAssertions.assertProductEquals(expected, result);

                verify(repository).save(any(Product.class));
                verifyNoInteractions(mediaClient);
        }

        @Test
        void shouldGetProductById() {

                when(repository.findById("10000000000"))
                                .thenReturn(Optional.of(product));

                Product result = service.getProduct("10000000000");

                ProductAssertions.assertProductEquals(product, result);

                verify(repository).findById("10000000000");
        }
}