package com.apiautomation.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides common default header sets for API requests.
 */
public final class DefaultHeaders {

    private DefaultHeaders() {
    } // Utility class

    /**
     * Standard JSON headers.
     */
    public static Map<String, String> jsonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
    }

    /**
     * Form URL-encoded headers.
     */
    public static Map<String, String> formHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        return headers;
    }

    /**
     * Authenticated headers with a Bearer token.
     */
    public static Map<String, String> authenticatedHeaders(String accessToken) {
        Map<String, String> headers = jsonHeaders();
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }
}
