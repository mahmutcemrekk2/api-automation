package com.apiautomation.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Environment-specific configuration loaded from environments.json.
 * <p>
 * Simplified for the education project — only contains generic fields
 * needed for API testing (no project-specific fields).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentConfig {

    private String baseUrl;
    private String defaultUsername;
    private String defaultPassword;

    // Default constructor for Jackson
    public EnvironmentConfig() {
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public void setDefaultUsername(String defaultUsername) {
        this.defaultUsername = defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }

    /**
     * Dynamic property lookup by key name.
     */
    public String get(String key) {
        return switch (key) {
            case "baseUrl" -> baseUrl;
            case "defaultUsername" -> defaultUsername;
            case "defaultPassword" -> defaultPassword;
            default -> null;
        };
    }

    /**
     * Factory for loading configuration from environments.json
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static EnvironmentConfig cachedConfig;

    public static synchronized EnvironmentConfig load() {
        if (cachedConfig != null)
            return cachedConfig;

        Environment env = Environment.current();
        InputStream configStream = EnvironmentConfig.class
                .getClassLoader()
                .getResourceAsStream("environments.json");

        if (configStream == null) {
            throw new RuntimeException("environments.json not found in resources");
        }

        try {
            JsonNode rootNode = MAPPER.readTree(configStream);
            JsonNode envNode = rootNode.path(env.name().toLowerCase());
            cachedConfig = MAPPER.treeToValue(envNode, EnvironmentConfig.class);
            return cachedConfig;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load environment config for: " + env, e);
        }
    }

    /**
     * Resets cached config (useful for testing).
     */
    public static synchronized void resetCache() {
        cachedConfig = null;
    }
}
