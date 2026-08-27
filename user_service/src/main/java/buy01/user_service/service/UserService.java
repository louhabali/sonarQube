package buy01.user_service.service;

import buy01.user_service.dto.ProfileRequest;
import buy01.user_service.dto.ProfileResponse;
import buy01.user_service.event.UserDeletedEvent;
import buy01.user_service.exceptions.BadRequestException;
import buy01.user_service.model.Role;
import buy01.user_service.model.User;
import buy01.user_service.producer.UserEventProducer;
import buy01.user_service.repo.UserRepository;
import buy01.user_service.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mongodb.DuplicateKeyException;

@Service
@RequiredArgsConstructor
public class UserService {
    // communicate
    private final UserEventProducer producer;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserBlacklistService blacklistService;

    public Map<String, Object> register(String username, String email, String password, String role, String avatarUrl) {
        String cleanEmail = email.toLowerCase().trim();
        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            throw new BadRequestException("Email already exists");
        } else if (userRepository.findByUsername(username).isPresent()) {
            throw new BadRequestException("Username already exists");
        }
        Role checkedRole = (role.equals("SELLER")) ? Role.SELLER : Role.CLIENT;
        User user = User.builder()
                .username(username)
                .email(cleanEmail)
                .password(passwordEncoder.encode(password))
                .role(checkedRole).createdAt(LocalDateTime.now().toString())
                .avatarUrl(avatarUrl)
                .build();
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("Email or username already exists");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User registered successfully");

        return response;

    }

    public Map<String, Object> login(String email, String password) {
        System.out.println("Login attempt for email: " + email);
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            throw new BadRequestException("User not found");
        }
        if (!passwordEncoder.matches(password, user.get().getPassword())) {
            throw new BadRequestException("Invalid password");
        }
        String token = jwtUtil.generateToken(user.get());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Login successful");
        response.put("token", token);
        return response;
    }

    @Cacheable(value = "profiles", key = "#userId")
    public ProfileResponse getProfile(String userId) {
       
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getAvatarUrl(),
                user.getCreatedAt());
    }

    @CachePut(value = "profiles", key = "#userId")
    public ProfileResponse updateProfile(String userId, ProfileRequest profile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (profile.getUsername() == null || profile.getUsername().trim().isEmpty()) {
            throw new BadRequestException("Username cannot be empty");
        }
        if (!profile.getUsername().matches("^[a-zA-Z0-9]+$")) {
            throw new BadRequestException("Username must contain only letters and digits");
        }
        if (profile.getEmail() == null || profile.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email cannot be empty");
        }
        if (profile.getRole().name()!="CLIENT" && profile.getRole().name()!="SELLER") {
            throw new BadRequestException("Invalid role. Must be CLIENT or SELLER");
        }   

        userRepository.findByUsername(profile.getUsername()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                
                throw new BadRequestException("Username already taken by another account");
            }
        });

       
        userRepository.findByEmail(profile.getEmail()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new BadRequestException("Email address already registered by another account");
            }
        });
        user.setUsername(profile.getUsername());
        user.setEmail(profile.getEmail());
        user.setAvatarUrl(profile.getAvatarUrl());
        user.setRole(profile.getRole());

        User updatedUser = userRepository.save(user);

        return new ProfileResponse(
                updatedUser.getId(),
                updatedUser.getUsername(),
                updatedUser.getEmail(),
                updatedUser.getRole(),
                updatedUser.getAvatarUrl(),
                updatedUser.getCreatedAt());
    }

    @CacheEvict(value = "profiles", key = "#userId")
    public Map<String, Object> deleteProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        System.out.println("11111111111111");
        userRepository.delete(user);
        // Send UserDeletedEvent to Kafka
        producer.sendUserDeletedEvent(new UserDeletedEvent(userId));
        System.out.println("UserDeletedEvent sent for userId: " + userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User deleted successfully");

        return response;
    }
}