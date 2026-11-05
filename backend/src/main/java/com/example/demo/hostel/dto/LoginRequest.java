package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.LoginType;

/** credential is the Google ID token from the sign-in popup. */
public record LoginRequest(String credential, LoginType loginType) {
}
