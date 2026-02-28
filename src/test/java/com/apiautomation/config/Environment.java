package com.apiautomation.config;

/**
 * Enumeration of supported test environments.
 * The active environment is determined by the system property "env".
 * <p>
 * Usage: {@code -Denv=qa} to run tests against the QA environment.
 */
public enum Environment {

    DEV,
    QA;

    /**
     * Resolves an environment from a string value. Defaults to DEV if unrecognized.
     */
    public static Environment fromString(String env) {
        if (env == null || env.isBlank()) return DEV;

        return switch (env.trim().toUpperCase()) {
            case "QA" -> QA;
            default -> DEV;
        };
    }

    /**
     * Returns the currently active environment based on the "env" system property.
     */
    public static Environment current() {
        String envProperty = System.getProperty("env", "dev");
        return fromString(envProperty);
    }
}
