package com.apiautomation.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Analyzes API test coverage by comparing endpoint-registry.json
 * with existing feature files. Produces api-coverage.json report.
 *
 * <p>
 * Runs automatically after test suite via Hooks @AfterAll.
 */
public class CoverageAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(CoverageAnalyzer.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String REGISTRY_FILE = "endpoint-registry.json";
    private static final String FEATURES_DIR = "src/test/resources/features";
    private static final String OUTPUT_FILE = "target/api-coverage.json";

    // Regex patterns for extracting endpoint usage from feature files
    // Matches: user sends a "GET" request to "/products/search"
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
            "user sends (?:form )?\"(GET|POST|PUT|PATCH|DELETE)\" request to \"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    /**
     * Main analysis method. Call after test suite completes.
     */
    public static void analyze() {
        try {
            logger.info("Starting API coverage analysis...");

            // 1. Load endpoint registry
            List<JsonNode> endpoints = loadRegistry();
            if (endpoints.isEmpty()) {
                logger.warn("No endpoints found in registry. Skipping analysis.");
                return;
            }

            // 2. Scan feature files for used endpoints
            Map<String, FeatureUsage> usedEndpoints = scanFeatureFiles();

            // 3. Compare and categorize
            ArrayNode covered = mapper.createArrayNode();
            ArrayNode uncovered = mapper.createArrayNode();

            for (JsonNode endpoint : endpoints) {
                String method = endpoint.get("method").asText();
                String path = endpoint.get("path").asText();

                // Find matching feature usage (handles path variables)
                FeatureUsage usage = findMatchingUsage(method, path, usedEndpoints);

                if (usage != null) {
                    ObjectNode coveredEntry = mapper.createObjectNode();
                    coveredEntry.put("resource", endpoint.get("resource").asText());
                    coveredEntry.put("name", endpoint.get("name").asText());
                    coveredEntry.put("method", method);
                    coveredEntry.put("path", path);
                    coveredEntry.put("featureFile", usage.featureFile);
                    ArrayNode scenarios = mapper.createArrayNode();
                    usage.scenarios.forEach(scenarios::add);
                    coveredEntry.set("scenarios", scenarios);
                    covered.add(coveredEntry);
                } else {
                    // Copy full endpoint info for uncovered
                    uncovered.add(endpoint.deepCopy());
                }
            }

            // 4. Build report
            ObjectNode report = mapper.createObjectNode();
            report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            ObjectNode summary = mapper.createObjectNode();
            summary.put("totalEndpoints", endpoints.size());
            summary.put("coveredEndpoints", covered.size());
            summary.put("uncoveredEndpoints", uncovered.size());
            double coverage = endpoints.isEmpty() ? 0
                    : Math.round((double) covered.size() / endpoints.size() * 1000.0) / 10.0;
            summary.put("coveragePercent", coverage);
            report.set("summary", summary);

            report.set("covered", covered);
            report.set("uncovered", uncovered);

            // 5. Write report
            File outputFile = new File(OUTPUT_FILE);
            outputFile.getParentFile().mkdirs();
            mapper.writeValue(outputFile, report);

            // 6. Print summary
            printSummary(endpoints.size(), covered.size(), uncovered.size(), coverage);

            logger.info("Coverage report saved to: {}", OUTPUT_FILE);

        } catch (Exception e) {
            logger.error("Coverage analysis failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads endpoint definitions from endpoint-registry.json.
     */
    private static List<JsonNode> loadRegistry() throws IOException {
        List<JsonNode> endpoints = new ArrayList<>();

        try (InputStream is = CoverageAnalyzer.class.getClassLoader().getResourceAsStream(REGISTRY_FILE)) {
            if (is == null) {
                logger.warn("endpoint-registry.json not found in classpath");
                return endpoints;
            }

            JsonNode root = mapper.readTree(is);
            JsonNode endpointsNode = root.get("endpoints");
            if (endpointsNode != null && endpointsNode.isArray()) {
                endpointsNode.forEach(endpoints::add);
            }
        }

        logger.info("Loaded {} endpoints from registry", endpoints.size());
        return endpoints;
    }

    /**
     * Scans all .feature files and extracts endpoint usage.
     */
    private static Map<String, FeatureUsage> scanFeatureFiles() throws IOException {
        Map<String, FeatureUsage> usages = new LinkedHashMap<>();
        Path featuresPath = Paths.get(FEATURES_DIR);

        if (!Files.exists(featuresPath)) {
            logger.warn("Features directory not found: {}", FEATURES_DIR);
            return usages;
        }

        try (Stream<Path> files = Files.walk(featuresPath)) {
            files.filter(p -> p.toString().endsWith(".feature"))
                    .forEach(featureFile -> {
                        try {
                            scanSingleFeature(featureFile, usages);
                        } catch (IOException e) {
                            logger.error("Error scanning {}: {}", featureFile, e.getMessage());
                        }
                    });
        }

        logger.info("Found {} unique endpoint usages in feature files", usages.size());
        return usages;
    }

    /**
     * Scans a single feature file for endpoint usage.
     */
    private static void scanSingleFeature(Path featureFile, Map<String, FeatureUsage> usages) throws IOException {
        String fileName = featureFile.getFileName().toString();
        List<String> lines = Files.readAllLines(featureFile);

        String currentScenario = null;
        for (String line : lines) {
            String trimmed = line.trim();

            // Track current scenario name
            if (trimmed.startsWith("Scenario:") || trimmed.startsWith("Scenario Outline:")) {
                currentScenario = trimmed.replaceFirst("Scenario(\\s+Outline)?:\\s*", "").trim();
            }

            // Find endpoint usage
            Matcher matcher = REQUEST_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                String method = matcher.group(1).toUpperCase();
                String path = matcher.group(2);

                // Normalize path: replace {{var}} and ${var} with {param}
                String normalizedPath = normalizePath(path);
                String key = method + " " + normalizedPath;

                FeatureUsage usage = usages.computeIfAbsent(key,
                        k -> new FeatureUsage(fileName, new ArrayList<>()));

                if (currentScenario != null && !usage.scenarios.contains(currentScenario)) {
                    usage.scenarios.add(currentScenario);
                }
            }
        }
    }

    /**
     * Normalizes an endpoint path by replacing variable placeholders
     * with a generic {param} pattern for matching.
     *
     * Examples:
     * /carts/{{cartId}} → /carts/{param}
     * /posts/${postId} → /posts/{param}
     * /products/1 → /products/{param}
     * /products/search → /products/search (unchanged)
     */
    private static String normalizePath(String path) {
        // Replace {{variable}} patterns
        String normalized = path.replaceAll("\\{\\{[^}]+}}", "{param}");
        // Replace ${variable} patterns
        normalized = normalized.replaceAll("\\$\\{[^}]+}", "{param}");
        // Replace numeric IDs (e.g., /products/1 → /products/{param})
        normalized = normalized.replaceAll("/\\d+", "/{param}");
        return normalized;
    }

    /**
     * Finds a matching feature usage for a registry endpoint.
     * Handles path variables like {id}, {userId}, {categoryName} etc.
     */
    private static FeatureUsage findMatchingUsage(String method, String registryPath,
            Map<String, FeatureUsage> usages) {
        // Convert registry path to normalized form
        // /products/{id} → /products/{param}
        String normalizedRegistry = registryPath.replaceAll("\\{[^}]+}", "{param}");
        String key = method + " " + normalizedRegistry;

        return usages.get(key);
    }

    /**
     * Prints a formatted coverage summary to console.
     */
    private static void printSummary(int total, int covered, int uncovered, double percent) {
        String bar = "═".repeat(50);
        logger.info("\n\n{}\n  API Coverage Report\n  Total: {} | Covered: {} | Missing: {}\n  Coverage: {}%\n{}\n",
                bar, total, covered, uncovered, percent, bar);
    }

    /**
     * Internal record for tracking feature file endpoint usage.
     */
    private static class FeatureUsage {
        final String featureFile;
        final List<String> scenarios;

        FeatureUsage(String featureFile, List<String> scenarios) {
            this.featureFile = featureFile;
            this.scenarios = scenarios;
        }
    }
}
