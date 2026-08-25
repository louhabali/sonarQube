package buy01.gateway_service.security;

import buy01.gateway_service.dto.UserVerificationResponse;
import buy01.gateway_service.service.UserBlacklistService;
import buy01.gateway_service.service.UserServiceClient;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserBlacklistService userBlacklistService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private void logTestSummary(TestInfo testInfo, String endpoint, HttpMethod method, Object expectedStatus, HttpStatusCode status) {
        String actualStatusStr = (status != null) ? status.toString() : "PASSED THROUGH (No Response)";

        System.out.printf("%n==================================================%n");
        System.out.printf("TEST: %s%n", testInfo.getDisplayName());
        System.out.printf("  Endpoint Target : [%s] %s%n", method, endpoint);
        System.out.printf("  Expected Result : %s%n", expectedStatus);
        System.out.printf("  Actual Result   : %s%n", actualStatusStr);
        System.out.printf("==================================================%n");
    }

    // Bypass authentication for OPTIONS requests
    @Test
    @DisplayName("Should bypass authentication for OPTIONS requests")
    void filter_OptionsMethod_ShouldPassThrough(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.OPTIONS, "/api/products")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        logTestSummary(testInfo, "/api/products", HttpMethod.OPTIONS, "200 OK (Bypassed)", exchange.getResponse().getStatusCode());

        verify(filterChain, times(1)).filter(exchange);
        verifyNoInteractions(jwtService, userBlacklistService, userServiceClient);
    }

    // Bypass authentication for public endpoints (login)
    @Test
    @DisplayName("Should bypass authentication for public endpoints (login)")
    void filter_PublicEndpoints_ShouldPassThrough(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        logTestSummary(testInfo, "/api/auth/login", HttpMethod.GET, "200 OK (Bypassed)", exchange.getResponse().getStatusCode());

        verify(filterChain, times(1)).filter(exchange);
        verifyNoInteractions(jwtService, userBlacklistService, userServiceClient);
    }

    // Bypass authentication for all public endpoint variations
    @Test
    @DisplayName("Should bypass authentication for all public endpoint variations")
    void filter_AllPublicEndpointVariants_ShouldPassThrough() {
        String[][] publicRequests = {
                {"POST", "/api/auth/register"},
                {"GET", "/api/products/123"},
                {"GET", "/api/uploads/avatar.png"},
                {"GET", "/api/media/video/1"},
                {"POST", "/api/media/avatars/public"}
        };

        for (String[] req : publicRequests) {
            HttpMethod method = HttpMethod.valueOf(req[0]);
            String path = req[1];

            MockServerHttpRequest request = MockServerHttpRequest.method(method, path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

            verify(filterChain, atLeastOnce()).filter(exchange);
        }

        verifyNoInteractions(jwtService, userBlacklistService, userServiceClient);
    }

    // Return 401 when Authorization header is missing
    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void filter_MissingAuthHeader_ShouldReturn401(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        HttpStatusCode actualStatus = exchange.getResponse().getStatusCode();
        logTestSummary(testInfo, "/api/products", HttpMethod.POST, HttpStatus.UNAUTHORIZED, actualStatus);

        assertThat(actualStatus)
                .as("Request without Authorization header must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(filterChain);
    }

    // Return 401 when Authorization header does not start with Bearer
    @Test
    @DisplayName("Should return 401 when Authorization header does not start with Bearer")
    void filter_InvalidAuthHeaderPrefix_ShouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Basic invalid_prefix_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(filterChain, jwtService, userBlacklistService, userServiceClient);
    }

    // Test for invalid token
    @Test
    @DisplayName("Should return 401 when JWT token is invalid")
    void filter_InvalidToken_ShouldReturn401(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("invalid_token")).thenReturn(false);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        HttpStatusCode actualStatus = exchange.getResponse().getStatusCode();
        logTestSummary(testInfo, "/api/products", HttpMethod.POST, HttpStatus.UNAUTHORIZED, actualStatus);

        assertThat(actualStatus)
                .as("Request with invalid token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(filterChain, userBlacklistService, userServiceClient);
    }

    // Return 401 when user is blacklisted
    @Test
    @DisplayName("Should return 401 when user is blacklisted")
    void filter_BlacklistedUser_ShouldReturn401(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("valid_token")).thenReturn(true);
        when(jwtService.extractClaims("valid_token")).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn("user_123");

        when(userBlacklistService.isBlacklisted("user_123")).thenReturn(Mono.just(true));

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        HttpStatusCode actualStatus = exchange.getResponse().getStatusCode();
        logTestSummary(testInfo, "/api/products", HttpMethod.POST, HttpStatus.UNAUTHORIZED + " (Blacklisted User)", actualStatus);

        assertThat(actualStatus)
                .as("Blacklisted user must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(userServiceClient);
        verify(filterChain, never()).filter(any());
    }

    // Return 401 when user does not exist in User Service
    @Test
    @DisplayName("Should return 401 when user does not exist in User Service")
    void filter_UserDoesNotExist_ShouldReturn401(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("valid_token")).thenReturn(true);
        when(jwtService.extractClaims("valid_token")).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn("user_123");

        when(userBlacklistService.isBlacklisted("user_123")).thenReturn(Mono.just(false));

        UserVerificationResponse userResponse = new UserVerificationResponse(false, null);
        when(userServiceClient.getUserVerification("user_123")).thenReturn(Mono.just(userResponse));

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        HttpStatusCode actualStatus = exchange.getResponse().getStatusCode();
        logTestSummary(testInfo, "/api/products", HttpMethod.POST, HttpStatus.UNAUTHORIZED + " (User Not Found)", actualStatus);

        assertThat(actualStatus)
                .as("Non-existent user must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(filterChain, never()).filter(any());
    }

    // Successful authentication and header mutation test
    @Test
    @DisplayName("Should forward request downstream with X-User-Id, X-Username, and X-Role headers")
    void filter_ValidTokenAndUser_ShouldMutateHeadersAndProceed(TestInfo testInfo) {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("valid_token")).thenReturn(true);
        when(jwtService.extractClaims("valid_token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("john_doe");
        when(claims.get("userId", String.class)).thenReturn("user_123");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");

        when(userBlacklistService.isBlacklisted("user_123")).thenReturn(Mono.just(false));

        UserVerificationResponse userResponse = new UserVerificationResponse(true, "SELLER");
        when(userServiceClient.getUserVerification("user_123")).thenReturn(Mono.just(userResponse));

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain, times(1)).filter(captor.capture());

        ServerWebExchange mutatedExchange = captor.getValue();
        HttpHeaders headers = mutatedExchange.getRequest().getHeaders();

        String actualUserId = headers.getFirst("X-User-Id");
        String actualUsername = headers.getFirst("X-Username");
        String actualRole = headers.getFirst("X-Role");

        System.out.printf("%n==================================================%n");
        System.out.printf("TEST: %s%n", testInfo.getDisplayName());
        System.out.printf("  Status: Forwarded Downstream%n");
        System.out.printf("  Injected Headers:%n");
        System.out.printf("    - X-User-Id  : %s%n", actualUserId);
        System.out.printf("    - X-Username : %s%n", actualUsername);
        System.out.printf("    - X-Role     : %s%n", actualRole);
        System.out.printf("==================================================%n");

        assertThat(actualUserId).as("Check injected X-User-Id header").isEqualTo("user_123");
        assertThat(actualUsername).as("Check injected X-Username header").isEqualTo("john_doe");
        assertThat(actualRole).as("Check injected X-Role header").isEqualTo("SELLER");
    }

    // Fallback to JWT role when DB user role is null
    @Test
    @DisplayName("Should fallback to JWT role when DB user role is null")
    void filter_UserDbRoleNull_ShouldFallbackToJwtRole() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtService.validateToken("valid_token")).thenReturn(true);
        when(jwtService.extractClaims("valid_token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("john_doe");
        when(claims.get("userId", String.class)).thenReturn("user_123");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");

        when(userBlacklistService.isBlacklisted("user_123")).thenReturn(Mono.just(false));

        UserVerificationResponse userResponse = new UserVerificationResponse(true, null);
        when(userServiceClient.getUserVerification("user_123")).thenReturn(Mono.just(userResponse));

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain, times(1)).filter(captor.capture());

        String actualRole = captor.getValue().getRequest().getHeaders().getFirst("X-Role");

        assertThat(actualRole)
                .as("Should fallback to JWT role when DB role is null")
                .isEqualTo("ROLE_USER");
    }
}