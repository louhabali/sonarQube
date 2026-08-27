package buy01.user_service.dto;

import com.mongodb.lang.NonNull;

import buy01.user_service.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "Username must be between 3 and 20 characters and contain only letters, numbers, and underscores")
        private String username;
        @Email(message = "Invalid email format")
        @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
        private String email;
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{4,}$", message = "Password must be at least 4 characters long and contain at least one letter and one number")
        private String password;
        private String avatarUrl;
        // Role is optional and can be CLIENT or SELLER. If not provided, it defaults to CLIENT.
        private Role role;
}
