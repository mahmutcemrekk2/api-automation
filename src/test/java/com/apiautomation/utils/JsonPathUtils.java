package com.apiautomation.utils;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for JsonPath operations with safe error handling.
 */
public final class JsonPathUtils {

    private JsonPathUtils() {
    } // Utility class

    /**
     * Extracts a value from JSON using the given path. Returns null if not found.
     */
    @SuppressWarnings("unchecked")
    public static <T> T extract(String json, String path) {
        try {
            return (T) JsonPath.read(json, path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Checks if a path exists in the JSON.
     */
    public static boolean pathExists(String json, String path) {
        try {
            JsonPath.read(json, path);
            return true;
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    /**
     * Extracts a list from JSON using the given path. Returns empty list if not
     * found.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> extractList(String json, String path) {
        try {
            return (List<T>) JsonPath.read(json, path);
        } catch (PathNotFoundException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Counts the number of items matching the given path.
     */
    public static int count(String json, String path) {
        try {
            List<Object> result = JsonPath.read(json, path);
            return result.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
