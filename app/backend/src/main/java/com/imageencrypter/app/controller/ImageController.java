package com.imageencrypter.app.controller;

import com.imageencrypter.app.service.ImagePublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ImageController {

    private final ImagePublisher imagePublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public ImageController(ImagePublisher imagePublisher) {
        this.imagePublisher = imagePublisher;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("operation") String operation,
            @RequestParam("mode") String mode,
            @RequestParam(value = "key", required = false) String key,
            Principal principal) throws Exception {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        // Validate operation and mode
        if (!operation.equals("encrypt") && !operation.equals("decrypt")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Operation must be 'encrypt' or 'decrypt'"));
        }
        if (!mode.equals("ecb") && !mode.equals("cbc")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mode must be 'ecb' or 'cbc'"));
        }

        // Handle AES key: use provided key or auto-generate
        if (key != null && !key.isBlank()) {
            key = key.trim().toLowerCase();
            if (!key.matches("[0-9a-f]{64}")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Key must be 64 hex characters (256-bit AES key)"));
            }
        } else {
            key = generateAesKey();
        }

        String jobId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        byte[] imageBytes = file.getBytes();

        String userEmail = principal.getName();
        imagePublisher.publish(imageBytes, jobId, operation, mode, key, originalName, userEmail);

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "key", key,
                "message", "Image submitted for " + operation
        ));
    }

    private String generateAesKey() {
        byte[] keyBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(keyBytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : keyBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
