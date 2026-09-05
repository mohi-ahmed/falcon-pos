package com.spark.falcon.settings.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class SettingsBrandingStorageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final Path logoDirectory;
    private final Path faviconDirectory;

    public SettingsBrandingStorageService(
            @Value("${falcon.upload.root-directory:${user.dir}/uploads}") String directory) {
        Path root = Path.of(directory).toAbsolutePath().normalize().resolve("settings");
        this.logoDirectory = root.resolve("logos");
        this.faviconDirectory = root.resolve("favicons");
    }

    public String storeLogo(MultipartFile file) {
        return store(file, logoDirectory, "/uploads/settings/logos/", "business logo");
    }

    public String storeFavicon(MultipartFile file) {
        return store(file, faviconDirectory, "/uploads/settings/favicons/", "favicon");
    }

    public void deleteOwnedReference(String reference) {
        if (reference == null || reference.isBlank()) return;
        delete(reference, "/uploads/settings/logos/", logoDirectory);
        delete(reference, "/uploads/settings/favicons/", faviconDirectory);
    }

    private String store(MultipartFile file, Path directory, String publicPrefix, String label) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("The " + label + " must be 5 MB or smaller");
        }
        String extension = detectedExtension(file, label);
        try {
            Files.createDirectories(directory);
            String storedName = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "." + extension;
            Path target = directory.resolve(storedName).normalize();
            if (!target.getParent().equals(directory)) {
                throw new IllegalArgumentException("Invalid " + label + " path");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return publicPrefix + storedName;
        } catch (IOException exception) {
            throw new IllegalArgumentException("The " + label + " could not be stored", exception);
        }
    }

    private void delete(String reference, String prefix, Path directory) {
        if (!reference.startsWith(prefix)) return;
        String name = reference.substring(prefix.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\")) return;
        try {
            Files.deleteIfExists(directory.resolve(name).normalize());
        } catch (IOException ignored) {
            // A failed cleanup must never make a saved settings update fail.
        }
    }

    private String detectedExtension(MultipartFile file, String label) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The " + label + " could not be read", exception);
        }
        int count = header.length;
        if (count >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff) return "jpg";
        if (count >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N'
                && header[3] == 'G' && header[4] == 0x0d && header[5] == 0x0a
                && header[6] == 0x1a && header[7] == 0x0a) return "png";
        if (count >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') return "webp";
        throw new IllegalArgumentException("Only JPG, PNG and WebP " + label + " images are allowed");
    }
}
