package com.example.demo.hostel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    static final String BUCKET = "complaint-images";

    // Allowed content types mapped to the extension stored in the bucket
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            MediaType.IMAGE_JPEG_VALUE, "jpg",
            MediaType.IMAGE_PNG_VALUE, "png",
            "image/webp", "webp"
    );

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String supabaseUrl;
    private final String supabaseKey;
    private final RestTemplate restTemplate;

    @Autowired
    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.key}") String supabaseKey) {
        this(supabaseUrl, supabaseKey, new RestTemplate());
    }

    SupabaseStorageService(String supabaseUrl, String supabaseKey, RestTemplate restTemplate) {
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.supabaseKey = supabaseKey;
        this.restTemplate = restTemplate;
    }

    public String uploadImage(MultipartFile file) {
        String contentType = file == null ? null : file.getContentType();
        if (contentType == null || !EXTENSIONS_BY_CONTENT_TYPE.containsKey(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG, PNG and WebP images are allowed");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read image file", e);
        }

        // The Content-Type header is client-supplied, so confirm the bytes match it
        if (!matchesSignature(contentType, bytes)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File content does not match its image type");
        }

        if (supabaseUrl.isBlank() || supabaseKey == null || supabaseKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is not configured");
        }

        // Extension comes from the verified type, never from the user-supplied file name
        String fileName = UUID.randomUUID() + "." + EXTENSIONS_BY_CONTENT_TYPE.get(contentType);

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey);
        // Legacy service_role keys are JWTs and also go in Authorization;
        // new sb_secret_ keys are not JWTs and must only be sent as apikey
        if (!supabaseKey.startsWith("sb_")) {
            headers.setBearerAuth(supabaseKey);
        }
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set("x-upsert", "false");

        try {
            restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + fileName,
                    HttpMethod.POST,
                    new HttpEntity<>(bytes, headers),
                    String.class
            );
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image upload failed", e);
        }

        return supabaseUrl + "/storage/v1/object/public/" + BUCKET + "/" + fileName;
    }

    private static boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case MediaType.IMAGE_JPEG_VALUE -> startsWith(bytes, JPEG_SIGNATURE);
            case MediaType.IMAGE_PNG_VALUE -> startsWith(bytes, PNG_SIGNATURE);
            // RIFF....WEBP
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
