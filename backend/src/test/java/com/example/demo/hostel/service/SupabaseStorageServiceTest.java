package com.example.demo.hostel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseStorageServiceTest {

    private static final String URL = "https://project.supabase.co";
    private static final String KEY = "service-key";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    private MockRestServiceServer server;
    private SupabaseStorageService storageService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        // Trailing slash should be tolerated in the configured URL
        storageService = new SupabaseStorageService(URL + "/", KEY, restTemplate);
    }

    @Test
    void uploadsJpegAndReturnsPublicUrl() {
        server.expect(requestTo(matchesPattern(
                        URL + "/storage/v1/object/complaint-images/" + UUID_PATTERN + "\\.jpg")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(header("apikey", KEY))
                .andExpect(header("Content-Type", MediaType.IMAGE_JPEG_VALUE))
                .andRespond(withSuccess());

        String url = storageService.uploadImage(file("photo.jpeg", MediaType.IMAGE_JPEG_VALUE, JPEG));

        assertThat(url).matches(URL + "/storage/v1/object/public/complaint-images/" + UUID_PATTERN + "\\.jpg");
        server.verify();
    }

    @Test
    void newSecretKeyIsSentOnlyAsApikey() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer newKeyServer = MockRestServiceServer.bindTo(restTemplate).build();
        SupabaseStorageService newKeyService = new SupabaseStorageService(URL, "sb_secret_abc", restTemplate);

        newKeyServer.expect(requestTo(matchesPattern(".*/complaint-images/.*")))
                .andExpect(header("apikey", "sb_secret_abc"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess());

        newKeyService.uploadImage(file("a.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG));
        newKeyServer.verify();
    }

    @Test
    void usesExtensionFromVerifiedTypeNotFileName() {
        server.expect(requestTo(matchesPattern(".*/complaint-images/" + UUID_PATTERN + "\\.png")))
                .andRespond(withSuccess());

        String url = storageService.uploadImage(file("../evil.html", MediaType.IMAGE_PNG_VALUE, PNG));

        assertThat(url).endsWith(".png").doesNotContain("evil");
        server.verify();
    }

    @Test
    void acceptsWebp() {
        server.expect(requestTo(matchesPattern(".*/complaint-images/" + UUID_PATTERN + "\\.webp")))
                .andRespond(withSuccess());

        assertThat(storageService.uploadImage(file("a.webp", "image/webp", WEBP))).endsWith(".webp");
    }

    @Test
    void rejectsDisallowedContentType() {
        assertStatus(file("a.gif", "image/gif", JPEG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(file("a.txt", MediaType.TEXT_PLAIN_VALUE, JPEG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(file("a", null, JPEG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        server.verify();
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredType() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        assertStatus(file("a.png", MediaType.IMAGE_PNG_VALUE, html), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(file("a.jpg", MediaType.IMAGE_JPEG_VALUE, PNG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        server.verify();
    }

    @Test
    void rejectsEmptyFile() {
        assertStatus(file("a.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]), HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsSupabaseErrorToBadGateway() {
        server.expect(requestTo(matchesPattern(".*/complaint-images/.*"))).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertStatus(file("a.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void failsClearlyWhenNotConfigured() {
        SupabaseStorageService unconfigured = new SupabaseStorageService("", "", new RestTemplate());

        assertThatThrownBy(() -> unconfigured.uploadImage(file("a.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void assertStatus(MockMultipartFile file, HttpStatus expected) {
        assertThatThrownBy(() -> storageService.uploadImage(file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
