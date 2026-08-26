package buy01.user_service.producer;

import buy01.user_service.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeletedEvent(UserDeletedEvent event) {

        kafkaTemplate.send(
                "user-deleted",
                event
        );
        
        System.out.println("UserDeletedEvent sent!");
    }
}