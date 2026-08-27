package buy01.media_service.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class MediaService {

    private static final Path UPLOAD_DIR = Paths.get("/app/uploads").toAbsolutePath().normalize();
    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final List<String> ALLOWED_MIME_TYPES = List.of("image/jpeg", "image/png", "image/webp","image/gif","image/x-avif","image/avif");
    // for single avatar upload
    public String uploadSingleAvatar(MultipartFile avatar) {
        validateImage(avatar);
        return saveFile(avatar);
    }

    // for multiple images upload
    public List<String> upload(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("At least one image must be provided");
        }

        List<String> imageUrls = new ArrayList<>();

        for (MultipartFile image : images) {
            validateImage(image);
            imageUrls.add(saveFile(image));
        }

        return imageUrls;
    }
    // Save the file to the upload directory and return its URL
    private String saveFile(MultipartFile file) {
        try {
            if (!Files.exists(UPLOAD_DIR)) {
                Files.createDirectories(UPLOAD_DIR);
            }

            // Extract & sanitize extension
            String originalFilename = StringUtils.cleanPath(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg"
            );
            String fileExtension = "";
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0) {
                fileExtension = originalFilename.substring(dotIndex).toLowerCase();
            }

            // Generate unique, safe filename using System time + hash
            String fileName = System.currentTimeMillis() + "_" + Math.abs(originalFilename.hashCode()) + fileExtension;
            Path filePath = UPLOAD_DIR.resolve(fileName).normalize();

            
            if (!filePath.startsWith(UPLOAD_DIR)) {
                throw new SecurityException("Cannot store file outside upload directory");
            }

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private void validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
        throw new IllegalArgumentException("Image file cannot be empty");
    }

    String originalFilename = image.getOriginalFilename();
    String fileExtension = "";
    if (originalFilename != null && originalFilename.contains(".")) {
        fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    String contentType = image.getContentType() != null ? image.getContentType().toLowerCase() : "";

    // Check if either the MIME type matches OR it has a valid image extension
    boolean isValidMimeType = ALLOWED_MIME_TYPES.contains(contentType);
    boolean isValidExtension = List.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif").contains(fileExtension);

    if (!isValidMimeType && !isValidExtension) {
        throw new IllegalArgumentException("Invalid image format. Allowed formats: JPEG, PNG, WEBP, GIF, AVIF");
    }

    if (image.getSize() > MAX_SIZE) {
        throw new IllegalArgumentException("Image size must not exceed 2MB");
    }
}

    public boolean deleteImage(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                return false;
            }

           
            if (fileName.startsWith("/uploads/")) {
                fileName = fileName.substring("/uploads/".length());
            }

            Path filePath = UPLOAD_DIR.resolve(fileName).normalize();

            // Directory Traversal Prevention
            if (!filePath.startsWith(UPLOAD_DIR)) {
                throw new SecurityException("Cannot delete files outside upload directory");
            }

            return Files.deleteIfExists(filePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete image: " + e.getMessage(), e);
        }
    }
}