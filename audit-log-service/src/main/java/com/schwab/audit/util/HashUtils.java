package com.schwab.audit.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic hashing operations.
 * 
 * Provides SHA-256 hashing for audit events to ensure tamper-evidence.
 */
@Component
public class HashUtils {

    /**
     * Computes SHA-256 hash of the given content.
     * 
     * @param content the content to hash
     * @return hexadecimal representation of SHA-256 hash (64 characters)
     * @throws IllegalStateException if SHA-256 algorithm is not available
     */
    public String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(Constants.HASH_ALGORITHM);
            byte[] encodedHash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param hash the byte array to convert
     * @return hexadecimal string representation
     */
    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Verifies if the computed hash matches the stored hash.
     * 
     * @param stored the stored hash value
     * @param computed the computed hash value
     * @return true if hashes match, false otherwise
     */
    public boolean verifyHash(String stored, String computed) {
        if (stored == null || computed == null) {
            return false;
        }
        return stored.equalsIgnoreCase(computed);
    }
}
