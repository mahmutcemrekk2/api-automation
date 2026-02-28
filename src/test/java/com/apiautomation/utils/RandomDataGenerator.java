package com.apiautomation.utils;

import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates random test data for use in scenarios.
 */
public final class RandomDataGenerator {

    private static final Random RANDOM = new Random();

    private RandomDataGenerator() {
    } // Utility class

    /**
     * Generates a random phone number.
     * Format: 5xxxxxxxxx (10 digits)
     */
    public static String generatePhoneNumber() {
        long randomSuffix = 100000000L + (long) (RANDOM.nextDouble() * 899999999L);
        return "5" + randomSuffix;
    }

    /**
     * Generates a random email address.
     */
    public static String generateEmail() {
        String randomString = RANDOM.ints(10, 'a', 'z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
        return "test." + randomString + "@testmail.com";
    }

    /**
     * Generates a random UUID (can be used as device ID, correlation ID, etc.)
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a random slug (URL-friendly identifier).
     */
    public static String generateSlug() {
        long timestamp = System.currentTimeMillis();
        String randomSuffix = RANDOM.ints(6, 'a', 'z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
        return "test-" + timestamp + "-" + randomSuffix;
    }

    /**
     * Generates a string representation of the current timestamp.
     */
    public static String generateTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Generates a random number string of the specified length.
     */
    public static String generateRandomNumber(int length) {
        if (length <= 0)
            throw new IllegalArgumentException("Length must be greater than 0");

        return RANDOM.ints(length, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
    }
}
