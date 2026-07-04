package com.example.openapp.jwk;

public record StoredJwk(String kid, String keyType, String encryptedData, String status) {}
