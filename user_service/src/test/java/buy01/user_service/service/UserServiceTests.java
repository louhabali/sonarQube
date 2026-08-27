package buy01.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import buy01.user_service.dto.ProfileRequest;
import buy01.user_service.dto.ProfileResponse;
import buy01.user_service.exceptions.BadRequestException;
import buy01.user_service.model.Role;
import buy01.user_service.model.User;
import buy01.user_service.producer.UserEventProducer;
import buy01.user_service.repo.UserRepository;
import buy01.user_service.security.JwtUtil;
import buy01.user_service.service.UserBlacklistService;
import buy01.user_service.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserEventProducer producer;

    @Mock
    private UserBlacklistService blacklistService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id("user-1")
                .username("alice")
                .email("alice@example.com")
                .password("encoded-password")
                .role(Role.CLIENT)
                .avatarUrl("avatar.png")
                .createdAt("2024-01-01T00:00:00")
                .build();
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");

        Map<String, Object> result = userService.register("alice", "alice@example.com", "secret123", "CLIENT", "avatar.png");
        // cc
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("message", "User registered successfully");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.register("alice", "alice@example.com", "secret123", "CLIENT", "avatar.png"));

        assertThat(exception.getMessage()).isEqualTo("Email already exists");
    }

    @Test
    void shouldThrowWhenUsernameAlreadyExists() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.register("alice", "alice@example.com", "secret123", "CLIENT", "avatar.png"));

        assertThat(exception.getMessage()).isEqualTo("Username already exists");
    }

    @Test
    void shouldLoginSuccessfully() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("token-123");

        Map<String, Object> result = userService.login("alice@example.com", "secret123");

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("message", "Login successful");
        assertThat(result).containsEntry("token", "token-123");
    }

    @Test
    void shouldThrowWhenUserNotFoundDuringLogin() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.login("missing@example.com", "secret123"));

        assertThat(exception.getMessage()).isEqualTo("User not found");
    }

    @Test
    void shouldThrowWhenPasswordIsInvalid() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.login("alice@example.com", "wrong-password"));

        assertThat(exception.getMessage()).isEqualTo("Invalid password");
    }

    @Test
    void shouldGetProfileSuccessfully() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        ProfileResponse result = userService.getProfile("user-1");

        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getName()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldThrowWhenProfileUserDoesNotExist() {
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userService.getProfile("missing-user"));

        assertThat(exception.getMessage()).isEqualTo("User not found");
    }

    @Test
    void shouldUpdateProfileSuccessfully() {
        ProfileRequest request = new ProfileRequest("bob", "bob@example.com", null, "new-avatar.png", Role.SELLER);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse result = userService.updateProfile("user-1", request);

        assertThat(result.getName()).isEqualTo("bob");
        assertThat(result.getEmail()).isEqualTo("bob@example.com");
        assertThat(result.getRole()).isEqualTo(Role.SELLER);
    }

    @Test
    void shouldThrowWhenUpdatingProfileWithEmptyUsername() {
        ProfileRequest request = new ProfileRequest("   ", "bob@example.com", null, "new-avatar.png", Role.CLIENT);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.updateProfile("user-1", request));

        assertThat(exception.getMessage()).isEqualTo("Username cannot be empty");
    }

    @Test
    void shouldDeleteProfileSuccessfully() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        Map<String, Object> result = userService.deleteProfile("user-1");

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("message", "User deleted successfully");
        verify(producer).sendUserDeletedEvent(any());
    }
}
