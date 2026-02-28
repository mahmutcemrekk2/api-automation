package com.apiautomation.context;

import com.jayway.jsonpath.JsonPath;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds state for a single test scenario.
 * <p>
 * Stores the last API response, request headers, current base URL,
 * and any variables saved during the scenario.
 * Each scenario gets its own TestContext instance via Cucumber DI
 * (PicoContainer).
 */
public class TestContext {

    private static final Logger logger = LoggerFactory.getLogger(TestContext.class);

    private final Map<String, Object> store = new ConcurrentHashMap<>();
    private Response lastResponse;
    private String currentBaseUrl = "";
    private final Map<String, String> headers = new ConcurrentHashMap<>();

    // ========== Response ==========

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response response) {
        this.lastResponse = response;
        logger.debug("Stored response with status: {}", response != null ? response.getStatusCode() : "null");
    }

    // ========== Base URL ==========

    public String getCurrentBaseUrl() {
        return currentBaseUrl;
    }

    public void setCurrentBaseUrl(String baseUrl) {
        this.currentBaseUrl = baseUrl;
    }

    // ========== Headers ==========

    public Map<String, String> getHeaders() {
        return headers;
    }

    // ========== Variable Store ==========

    public void set(String key, Object value) {
        store.put(key, value);
        logger.debug("Stored '{}' = '{}'", key, value);
    }

    public Object get(String key) {
        return store.get(key);
    }

    public String getString(String key) {
        Object value = store.get(key);
        return value != null ? value.toString() : "";
    }

    public boolean has(String key) {
        return store.containsKey(key);
    }

    public Set<String> keys() {
        return store.keySet();
    }

    // ========== JsonPath Extract ==========

    /**
     * Extracts a value from the last response using JsonPath and stores it.
     */
    public void extractAndStore(String jsonPath, String key) {
        if (lastResponse == null) {
            throw new IllegalStateException("No response available to extract from");
        }

        String responseBody = lastResponse.getBody().asString();
        Object value = JsonPath.read(responseBody, jsonPath);
        store.put(key, value);
        logger.info("Extracted '{}' from '{}' = '{}'", key, jsonPath, value);
    }

    /**
     * Extracts a value from the last response using JsonPath.
     */
    @SuppressWarnings("unchecked")
    public <T> T extract(String jsonPath) {
        if (lastResponse == null)
            return null;

        try {
            String body = lastResponse.getBody().asString();
            return (T) JsonPath.read(body, jsonPath);
        } catch (Exception e) {
            logger.warn("Failed to extract '{}': {}", jsonPath, e.getMessage());
            return null;
        }
    }

    // ========== Lifecycle ==========

    /**
     * Clears all stored state. Called between scenarios.
     */
    public void clear() {
        store.clear();
        lastResponse = null;
        currentBaseUrl = "";
        headers.clear();
        logger.debug("TestContext cleared");
    }
}
