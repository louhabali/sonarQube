package buy01.user_service.controller;

import buy01.user_service.dto.ProfileRequest;
import buy01.user_service.dto.ProfileResponse;
import buy01.user_service.model.Role;
import buy01.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.mongodb.lang.NonNull;;
@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService us;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return us.register(request.getUsername(), request.getEmail(),
                request.getPassword(), request.getRole() , request.getAvatarUrl());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        System.out.println("Login attempt for email: " + request.getEmail());
        return us.login(request.getEmail(), request.getPassword());
    }
    @GetMapping("/profile")
    public ProfileResponse profile(
            @RequestHeader("X-User-Id") String userId) {
           System.out.println("Fetching profile for userId: " + userId);
        return us.getProfile(userId);
    }
    @PutMapping("/profile")
    public ProfileResponse updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody ProfileRequest profile) {
           
        return us.updateProfile(userId, profile);
    }
    @DeleteMapping("/profile")
    public Map<String, Object> deleteProfile(
            @RequestHeader("X-User-Id") String userId) {
            
        return us.deleteProfile(userId);
    }

    static class RegisterRequest {
        @NotEmpty(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "Username must be between 3 and 20 characters and contain only letters, numbers, and underscores")
        private String username;
        @Email(message = "Invalid email format")
        @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
        private String email;
        @NotEmpty(message = "Password is required")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{4,}$", message = "Password must be at least 4 characters long and contain at least one letter and one number")
        private String password;
        private String avatarUrl;
        @NonNull
        private String role;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    static class LoginRequest {
        @NotEmpty(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        @NotEmpty(message = "Password is required")
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
