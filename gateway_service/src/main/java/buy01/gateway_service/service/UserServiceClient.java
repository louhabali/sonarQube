package buy01.gateway_service.service;

import buy01.gateway_service.dto.UserVerificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient webClient = WebClient.create("http://user-service:8081");

    public Mono<UserVerificationResponse> getUserVerification(String userId) {
        return webClient.get()
                .uri("/internal/users/{id}/exists", userId)
                .retrieve()
                // Handles 404 Not Found cleanly by returning exists = false
                .onStatus(status -> status.equals(HttpStatus.NOT_FOUND), 
                        response -> Mono.empty())
                // Parses JSON response: {"exists": true, "role": "SELLER"}
                .bodyToMono(UserVerificationResponse.class)
                .defaultIfEmpty(new UserVerificationResponse(false, null))
                .doOnError(Throwable::printStackTrace);
    }
}