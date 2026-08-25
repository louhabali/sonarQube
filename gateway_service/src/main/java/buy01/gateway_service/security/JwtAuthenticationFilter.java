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
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isPublicEndpoint(method, path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (isInvalidAuthHeader(authHeader)) {
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

        return processUserValidation(exchange, chain, userId, username, jwtRole);
    }

    private boolean isPublicEndpoint(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            return true;
        }
        if (HttpMethod.GET.equals(method)) {
            return path.startsWith("/api/products")
                    || path.startsWith("/api/uploads/")
                    || path.startsWith("/api/media/");
        }
        return HttpMethod.POST.equals(method) && path.equals("/api/media/avatars/public");
    }

    private boolean isInvalidAuthHeader(String authHeader) {
        return authHeader == null || !authHeader.startsWith("Bearer ");
    }

    private Mono<Void> processUserValidation(ServerWebExchange exchange, GatewayFilterChain chain,
                                             String userId, String username, String jwtRole) {
        return userBlacklistService.isBlacklisted(userId)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return unauthorizedResponse(exchange, "User is blacklisted");
                    }
                    return verifyUserAndForward(exchange, chain, userId, username, jwtRole);
                });
    }

    private Mono<Void> verifyUserAndForward(ServerWebExchange exchange, GatewayFilterChain chain,
                                           String userId, String username, String jwtRole) {
        return userServiceClient.getUserVerification(userId)
                .flatMap(userDto -> {
                    if (!userDto.isExists()) {
                        return unauthorizedResponse(exchange, "User does not exist");
                    }

                    String activeRole = (userDto.getRole() != null) ? userDto.getRole() : jwtRole;

                    ServerHttpRequest request = exchange.getRequest()
                            .mutate()
                            .header("X-User-Id", userId)
                            .header("X-Username", username)
                            .header("X-Role", activeRole)
                            .build();

                    return chain.filter(exchange.mutate().request(request).build());
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