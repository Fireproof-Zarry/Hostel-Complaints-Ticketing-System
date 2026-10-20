package com.example.demo.hostel.controller;

import com.example.demo.hostel.config.SecurityConfig;
import com.example.demo.hostel.model.Complaint;
import com.example.demo.hostel.repository.UserRepository;
import com.example.demo.hostel.service.AuthService;
import com.example.demo.hostel.service.ComplaintService;
import com.example.demo.hostel.validation.RoomLocationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComplaintController.class)
@Import({SecurityConfig.class, RoomLocationValidator.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class ComplaintImageUploadSecurityTest {

    private static final String EMAIL = "student@smail.iitm.ac.in";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final MockMultipartFile image =
            new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    @Test
    void uploadReturnsUpdatedComplaint() throws Exception {
        Complaint updated = new Complaint();
        updated.setId(7L);
        updated.setImageUrl("https://project.supabase.co/storage/v1/object/public/complaint-images/a.jpg");
        when(complaintService.attachImage(eq(7L), eq(EMAIL), any())).thenReturn(updated);

        mockMvc.perform(multipart("/api/complaints/7/image").file(image)
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.imageUrl").value(updated.getImageUrl()));

        verify(complaintService).attachImage(eq(7L), eq(EMAIL), any());
    }

    @Test
    void uploadWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/complaints/7/image").file(image))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(complaintService);
    }

    @Test
    void unsupportedImageTypeReturns415() throws Exception {
        when(complaintService.attachImage(eq(7L), eq(EMAIL), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        mockMvc.perform(multipart("/api/complaints/7/image").file(image)
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/complaints/7/image")
                        .with(jwt().jwt(token -> token.claim("email", EMAIL))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(complaintService);
    }
}
