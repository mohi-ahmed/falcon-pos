package com.spark.falcon.product.service;

import com.spark.falcon.product.exception.ProductImageStorageException;
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
public class ProductImageStorageService {
    static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private final Path productDirectory;
    private final Path categoryDirectory;

    public ProductImageStorageService(@Value("${falcon.upload.root-directory:${user.dir}/uploads}") String directory) {
        this.productDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("products");
        this.categoryDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("categories");
    }

    public String store(MultipartFile file) {
        return store(file, productDirectory, "/uploads/products/", "product");
    }

    public String storeCategory(MultipartFile file) {
        return store(file, categoryDirectory, "/uploads/categories/", "category");
    }

    private String store(MultipartFile file, Path directory, String publicPrefix, String subject) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ProductImageStorageException("Each " + subject + " image must be 5 MB or smaller");
        }
        String extension = detectedExtension(file);
        try {
            Files.createDirectories(directory);
            String storedName = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "." + extension;
            Path target = directory.resolve(storedName).normalize();
            if (!target.getParent().equals(directory)) {
                throw new ProductImageStorageException("Invalid " + subject + " image path");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return publicPrefix + storedName;
        } catch (IOException exception) {
            throw new ProductImageStorageException("Product image could not be stored", exception);
        }
    }

    public void deleteNewReference(String reference) {
        if (reference == null) return;
        Path directory;
        String prefix;
        if (reference.startsWith("/uploads/products/")) {
            directory = productDirectory; prefix = "/uploads/products/";
        } else if (reference.startsWith("/uploads/categories/")) {
            directory = categoryDirectory; prefix = "/uploads/categories/";
        } else return;
        String name = reference.substring(prefix.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\")) return;
        try { Files.deleteIfExists(directory.resolve(name).normalize()); }
        catch (IOException ignored) { }
    }

    private String detectedExtension(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) { header = input.readNBytes(12); }
        catch (IOException exception) { throw new ProductImageStorageException("Product image could not be read", exception); }
        int count = header.length;
        if (count >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) return "jpg";
        if (count >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a) return "png";
        if (count >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "webp";
        throw new ProductImageStorageException("Only JPG, PNG and WebP product images are allowed");
    }
}
