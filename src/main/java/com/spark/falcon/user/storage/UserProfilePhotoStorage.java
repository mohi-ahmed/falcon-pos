package com.spark.falcon.user.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserProfilePhotoStorage {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path root;

    public UserProfilePhotoStorage(@Value("${falcon.upload.user-profile-directory:uploads/users}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(Long businessId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;
        if (businessId == null || businessId <= 0) throw new IllegalArgumentException("businessId is required");

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || !matchesSignature(file, contentType)) {
            throw new IllegalArgumentException("Profile photo must be a valid JPG, PNG or WebP image");
        }

        Path businessDirectory = root.resolve(String.valueOf(businessId)).normalize();
        if (!businessDirectory.startsWith(root)) throw new IllegalArgumentException("Invalid profile photo path");
        Files.createDirectories(businessDirectory);

        String fileName = UUID.randomUUID() + extension;
        Path target = businessDirectory.resolve(fileName).normalize();
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/users/" + businessId + "/" + fileName;
    }

    public void delete(String reference) {
        if (reference == null || !reference.startsWith("/uploads/users/")) return;
        String relative = reference.substring("/uploads/users/".length());
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
    }

    private boolean matchesSignature(MultipartFile file, String contentType) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if ("image/jpeg".equals(contentType)) {
                return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            }
            if ("image/png".equals(contentType)) {
                return header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e
                        && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            }
            if ("image/webp".equals(contentType)) {
                return header.length >= 12 && new String(header, 0, 4).equals("RIFF") && new String(header, 8, 4).equals("WEBP");
            }
            return false;
        }
    }
}
