package buy01.gateway_service.security;

import buy01.gateway_service.service.UserBlacklistService;
import buy01.gateway_service.service.UserServiceClient;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final UserBlacklistService userBlacklistService;
    private final UserServiceClient userServiceClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }
        System.out.println("Request Path: " + path + ", Method: " + method);
        // Public endpoints
        if (path.startsWith("/api/auth/login") ||
                path.startsWith("/api/auth/register") ||
                (method == HttpMethod.GET && path.startsWith("/api/products")) ||
                (method == HttpMethod.GET && path.startsWith("/api/uploads/")) ||
                (HttpMethod.GET.equals(method) && path.startsWith("/api/media/")) ||
                (HttpMethod.POST.equals(method) && path.equals("/api/media/avatars/public"))) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtService.validateToken(token)) {
            return unauthorizedResponse(exchange, "Invalid JWT Token");
        }

        Claims claims = jwtService.extractClaims(token);
        String username = claims.getSubject();
        String userId = claims.get("userId", String.class);
        String jwtRole = claims.get("role", String.class);

        // Check if user is blacklisted
        return userBlacklistService.isBlacklisted(userId)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return unauthorizedResponse(exchange, "User is blacklisted");
                    }

                    // Fetch fresh user verification
                    return userServiceClient.getUserVerification(userId)
                            .flatMap(userDto -> {
                                if (!userDto.isExists()) {
                                    return unauthorizedResponse(exchange, "User does not exist");
                                }

                                // Use updated DB role; fallback to JWT role if DB role is null
                                String activeRole = (userDto.getRole() != null) ? userDto.getRole() : jwtRole;

                                // Mutate request with updated headers and forward downstream
                                ServerHttpRequest request = exchange.getRequest()
                                        .mutate()
                                        .header("X-User-Id", userId)
                                        .header("X-Username", username)
                                        .header("X-Role", activeRole)
                                        .build();

                                return chain.filter(exchange.mutate().request(request).build());
                            });
                });
    }

    // Helper method to keep code clean & readable
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"message\": \"%s\"}", message);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}