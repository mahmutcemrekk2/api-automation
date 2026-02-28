package com.apiautomation.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe singleton to hold state across different scenarios within a
 * feature.
 * <p>
 * <b>LOCAL MODE:</b> Persists to file for cross-run persistence (useful for
 * debugging).<br>
 * <b>CI MODE:</b> Memory-only (no file persistence).
 */
public final class GlobalTestState {

    private static final Logger logger = LoggerFactory.getLogger(GlobalTestState.class);

    private static final boolean IS_LOCAL_MODE = System.getenv("CI") == null || System.getenv("CI").isEmpty();
    private static final File STATE_FILE = new File(
            new File(System.getProperty("user.dir"), "target"), ".global-test-state.json");

    private static final ThreadLocal<Map<String, String>> THREAD_LOCAL_STORE = ThreadLocal.withInitial(() -> {
        Map<String, String> map = new ConcurrentHashMap<>();
        if (IS_LOCAL_MODE && STATE_FILE.exists()) {
            try {
                String content = Files.readString(STATE_FILE.toPath());
                Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    map.put(matcher.group(1), matcher.group(2));
                }
                logger.info("[GlobalTestState] Loaded {} variables from file for thread {}",
                        map.size(), Thread.currentThread().getName());
            } catch (IOException e) {
                logger.warn("[GlobalTestState] Could not load from file: {}", e.getMessage());
            }
        }
        return map;
    });

    private GlobalTestState() {
    } // Utility class

    public static void set(String key, String value) {
        THREAD_LOCAL_STORE.get().put(key, value);
        if (IS_LOCAL_MODE)
            saveToFile();
    }

    public static String get(String key) {
        return THREAD_LOCAL_STORE.get().get(key);
    }

    public static boolean has(String key) {
        return THREAD_LOCAL_STORE.get().containsKey(key);
    }

    public static void clear() {
        THREAD_LOCAL_STORE.get().clear();
        if (IS_LOCAL_MODE && STATE_FILE.exists()) {
            STATE_FILE.delete();
        }
    }

    public static void clearAll() {
        THREAD_LOCAL_STORE.remove();
        if (IS_LOCAL_MODE && STATE_FILE.exists()) {
            STATE_FILE.delete();
        }
    }

    private static synchronized void saveToFile() {
        Map<String, String> currentMap = THREAD_LOCAL_STORE.get();
        File parentDir = STATE_FILE.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        StringBuilder json = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> entry : currentMap.entrySet()) {
            if (i > 0)
                json.append(",\n");
            json.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            i++;
        }
        json.append("\n}");

        try {
            Files.writeString(STATE_FILE.toPath(), json.toString());
        } catch (IOException e) {
            logger.error("Failed to save GlobalTestState to file: {}", e.getMessage());
        }
    }
}
