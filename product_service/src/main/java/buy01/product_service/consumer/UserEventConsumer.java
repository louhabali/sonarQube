
package buy01.product_service.consumer;

import buy01.product_service.event.UserDeletedEvent;
import buy01.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ProductService productService;

    @KafkaListener(
            topics = "user-deleted",
            groupId = "product-group"
    )
    public void consume(UserDeletedEvent event) {
        System.out.println("Received UserDeletedEvent: " + event);
        productService.deleteProductsByUserId(event.getUserId());
    }
}