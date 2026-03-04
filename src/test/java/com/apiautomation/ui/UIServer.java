package com.apiautomation.ui;

import com.apiautomation.utils.CoverageAnalyzer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Lightweight HTTP server for the API Test Automation Dashboard.
 * Serves UI files and provides REST API for test management.
 *
 * <p>
 * Start with: mvn exec:java -Dexec.mainClass="com.apiautomation.ui.UIServer"
 */
public class UIServer {

    private static final Logger logger = LoggerFactory.getLogger(UIServer.class);
    private static final int PORT = 8090;
    private static final String UI_DIR = "src/test/resources/ui";
    private static final String FEATURES_DIR = "src/test/resources/features";
    private static final String COVERAGE_FILE = "target/api-coverage.json";
    private static final String REGISTRY_FILE = "src/test/resources/endpoint-registry.json";

    // Track test results for commit gate
    private static boolean testsRan = false;
    private static boolean allTestsPassed = false;
    private static String lastTestOutput = "";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // UI static files
        server.createContext("/", UIServer::handleStaticFile);

        // API endpoints
        server.createContext("/api/registry", UIServer::handleRegistry);
        server.createContext("/api/coverage", UIServer::handleCoverage);
        server.createContext("/api/features", UIServer::handleFeatures);
        server.createContext("/api/save-feature", UIServer::handleSaveFeature);
        server.createContext("/api/run-tests", UIServer::handleRunTests);
        server.createContext("/api/test-results", UIServer::handleTestResults);
        server.createContext("/api/git/status", UIServer::handleGitStatus);
        server.createContext("/api/git/new-branch", UIServer::handleNewBranch);
        server.createContext("/api/git/commit-push", UIServer::handleCommitPush);
        server.createContext("/api/git/pull", UIServer::handleGitPull);
        server.createContext("/api/comment-scenario", UIServer::handleCommentScenario);
        server.createContext("/api/delete-scenario", UIServer::handleDeleteScenario);
        server.createContext("/api/reorder-scenario", UIServer::handleReorderScenario);
        server.createContext("/api/run-single", UIServer::handleRunSingle);
        server.createContext("/api/run-temp", UIServer::handleRunTemp);

        server.start();
        logger.info("╔══════════════════════════════════════╗");
        logger.info("║  API Test Automation Dashboard       ║");
        logger.info("║  http://localhost:{}                ║", PORT);
        logger.info("╚══════════════════════════════════════╝");
    }

    // ============================================
    // STATIC FILE SERVING
    // ============================================

    // ============================================
    // SECURITY: LOCALHOST ONLY CHECK
    // ============================================
    private static boolean isLocalhostOnly(HttpExchange exchange) throws IOException {
        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress)) {
            sendResponse(exchange, 403, "application/json", "{\"error\":\"Forbidden (Localhost only)\"}");
            return false;
        }
        return true;
    }

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path))
            path = "/index.html";

        File file = new File(UI_DIR + path);

        // Prevent Path Traversal
        String canonicalTarget = file.getCanonicalPath();
        String canonicalBase = new File(UI_DIR).getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalBase)) {
            sendResponse(exchange, 403, "text/plain", "Forbidden");
            return;
        }

        if (!file.exists() || file.isDirectory()) {
            sendResponse(exchange, 404, "text/plain", "Not Found");
            return;
        }

        String contentType = getContentType(path);
        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    // ============================================
    // API: REGISTRY & COVERAGE
    // ============================================

    private static void handleRegistry(HttpExchange exchange) throws IOException {
        serveJsonFile(exchange, REGISTRY_FILE);
    }

    private static void handleCoverage(HttpExchange exchange) throws IOException {
        // Re-generate coverage before serving
        CoverageAnalyzer.analyze();
        serveJsonFile(exchange, COVERAGE_FILE);
    }

    private static void handleFeatures(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        File featuresDir = new File(FEATURES_DIR);
        if (!featuresDir.exists()) {
            sendResponse(exchange, 200, "application/json", "[]");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        File[] files = featuresDir.listFiles((d, name) -> name.endsWith(".feature"));
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                String content = Files.readString(files[i].toPath(), StandardCharsets.UTF_8);
                String escaped = escapeJson(content);
                json.append("{\"name\":\"").append(files[i].getName())
                        .append("\",\"content\":\"").append(escaped).append("\"}");
                if (i < files.length - 1)
                    json.append(",");
            }
        }
        json.append("]");
        sendResponse(exchange, 200, "application/json", json.toString());
    }

    // ============================================
    // API: SAVE FEATURE
    // ============================================

    private static void handleSaveFeature(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        // Parse simple JSON: { "filename": "xxx.feature", "content": "..." }
        String filename = extractJsonField(body, "filename");
        String content = extractJsonField(body, "content");
        boolean append = body.contains("\"append\":true") || body.contains("\"append\": true");

        if (filename == null || content == null) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing filename or content\"}");
            return;
        }

        // Sanitize filename
        filename = filename.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (!filename.endsWith(".feature"))
            filename += ".feature";

        Path featurePath = Paths.get(FEATURES_DIR, filename);
        Files.createDirectories(featurePath.getParent());

        if (append && Files.exists(featurePath)) {
            Files.writeString(featurePath, "\n" + content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } else {
            Files.writeString(featurePath, content, StandardCharsets.UTF_8);
        }

        // Invalidate test state (new file added, tests need re-run)
        testsRan = false;
        allTestsPassed = false;

        logger.info("Feature file saved: {}", featurePath);
        sendResponse(exchange, 200, "application/json",
                "{\"success\":true,\"file\":\"" + featurePath + "\"}");
    }

    // ============================================
    // API: RUN TESTS
    // ============================================

    private static void handleRunTests(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "test");
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            testsRan = true;
            allTestsPassed = (exitCode == 0);
            lastTestOutput = output;

            // Extract test summary from output
            String summary = extractTestSummary(output);

            String result = String.format(
                    "{\"success\":%s,\"exitCode\":%d,\"summary\":\"%s\",\"output\":\"%s\"}",
                    allTestsPassed, exitCode, escapeJson(summary), escapeJson(lastTestOutput));
            sendResponse(exchange, 200, "application/json", result);

        } catch (Exception e) {
            testsRan = true;
            allTestsPassed = false;
            lastTestOutput = e.getMessage();
            sendResponse(exchange, 500, "application/json",
                    "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleTestResults(HttpExchange exchange) throws IOException {
        String json = String.format(
                "{\"testsRan\":%s,\"allPassed\":%s,\"output\":\"%s\"}",
                testsRan, allTestsPassed, escapeJson(lastTestOutput));
        sendResponse(exchange, 200, "application/json", json);
    }

    // ============================================
    // API: GIT OPERATIONS
    // ============================================

    private static void handleGitStatus(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        try {
            String branch = runGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
            String status = runGitCommand("git", "status", "--porcelain");
            int changedFiles = status.isEmpty() ? 0 : status.split("\n").length;

            String json = String.format(
                    "{\"branch\":\"%s\",\"changedFiles\":%d,\"testsRan\":%s,\"allPassed\":%s}",
                    escapeJson(branch), changedFiles, testsRan, allTestsPassed);
            sendResponse(exchange, 200, "application/json", json);
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleNewBranch(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String branchName = extractJsonField(body, "branchName");
        if (branchName == null || branchName.isBlank()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Branch name required\"}");
            return;
        }

        try {
            runGitCommand("git", "checkout", "-b", branchName);
            sendResponse(exchange, 200, "application/json",
                    "{\"success\":true,\"branch\":\"" + escapeJson(branchName) + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void handleCommitPush(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        // Gate: tests must have run and passed
        if (!testsRan || !allTestsPassed) {
            sendResponse(exchange, 403, "application/json",
                    "{\"error\":\"Tests must run and all pass before commit & push\",\"testsRan\":"
                            + testsRan + ",\"allPassed\":" + allTestsPassed + "}");
            return;
        }

        String body = readBody(exchange);
        String message = extractJsonField(body, "message");
        if (message == null || message.isBlank()) {
            message = "feat: add new API test scenarios";
        }

        try {
            runGitCommand("git", "add", ".");
            runGitCommand("git", "commit", "-m", message);

            String branch = runGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
            runGitCommand("git", "push", "-u", "origin", branch);

            sendResponse(exchange, 200, "application/json",
                    "{\"success\":true,\"message\":\"" + escapeJson(message)
                            + "\",\"branch\":\"" + escapeJson(branch) + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // API: COMMENT/UNCOMMENT SCENARIO
    // ============================================

    private static void handleCommentScenario(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String filename = extractJsonField(body, "filename");
        String scenarioName = extractJsonField(body, "scenario");
        String action = extractJsonField(body, "action"); // "comment" or "uncomment"

        if (filename == null || scenarioName == null || action == null) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing fields\"}");
            return;
        }

        Path featurePath = Paths.get(FEATURES_DIR, filename);
        if (!Files.exists(featurePath)) {
            sendResponse(exchange, 404, "application/json", "{\"error\":\"Feature file not found\"}");
            return;
        }

        try {
            java.util.List<String> lines = Files.readAllLines(featurePath, StandardCharsets.UTF_8);
            java.util.List<String> result = new java.util.ArrayList<>();
            boolean inTargetScenario = false;
            boolean done = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // Detect scenario start (also match commented scenario)
                String cleanLine = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
                boolean isScenarioLine = cleanLine.startsWith("Scenario:") || cleanLine.startsWith("Scenario Outline:");

                if (isScenarioLine && cleanLine.contains(scenarioName)) {
                    inTargetScenario = true;
                } else if (inTargetScenario && isScenarioLine) {
                    // Reached next scenario, stop modifying
                    inTargetScenario = false;
                    done = true;
                }

                if (inTargetScenario && !done) {
                    if ("comment".equals(action)) {
                        // Comment out: add # prefix if not already commented
                        if (!trimmed.startsWith("#") && !trimmed.isEmpty()) {
                            result.add("# " + line);
                        } else {
                            result.add(line);
                        }
                    } else {
                        // Uncomment: remove leading # (preserve indentation)
                        if (trimmed.startsWith("# ")) {
                            result.add(line.replaceFirst("# ", ""));
                        } else if (trimmed.startsWith("#")) {
                            result.add(line.replaceFirst("#", ""));
                        } else {
                            result.add(line);
                        }
                    }
                } else {
                    result.add(line);
                }
            }

            Files.write(featurePath, result, StandardCharsets.UTF_8);

            // Invalidate test state
            testsRan = false;
            allTestsPassed = false;

            sendResponse(exchange, 200, "application/json",
                    "{\"success\":true,\"action\":\"" + action + "\",\"scenario\":\"" + escapeJson(scenarioName)
                            + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // API: DELETE SCENARIO
    // ============================================

    private static void handleDeleteScenario(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String filename = extractJsonField(body, "filename");
        String scenarioName = extractJsonField(body, "scenario");

        if (filename == null || scenarioName == null || filename.isBlank() || scenarioName.isBlank()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing filename or scenario name\"}");
            return;
        }

        Path featurePath = Paths.get(FEATURES_DIR, filename);
        if (!Files.exists(featurePath)) {
            sendResponse(exchange, 404, "application/json", "{\"error\":\"Feature file not found: " + filename + "\"}");
            return;
        }

        try {
            java.util.List<String> lines = Files.readAllLines(featurePath, StandardCharsets.UTF_8);
            FeatureData data = parseFeature(lines);

            if (data.orderedNames.contains(scenarioName)) {
                data.orderedNames.remove(scenarioName);

                java.util.List<String> updatedLines = new java.util.ArrayList<>(data.headerBlock);
                for (String name : data.orderedNames) {
                    updatedLines.addAll(data.scenarioMap.get(name));
                }

                Files.write(featurePath, updatedLines, StandardCharsets.UTF_8);

                testsRan = false;
                allTestsPassed = false;

                sendResponse(exchange, 200, "application/json",
                        "{\"success\":true,\"scenario\":\"" + escapeJson(scenarioName) + "\"}");
            } else {
                sendResponse(exchange, 404, "application/json",
                        "{\"error\":\"Scenario not found in file: \" + escapeJson(scenarioName)}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // API: REORDER SCENARIO (DRAG & DROP)
    // ============================================

    private static void handleReorderScenario(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String filename = extractJsonField(body, "filename");
        String sourceScenario = extractJsonField(body, "sourceScenario");
        String targetScenario = extractJsonField(body, "targetScenario");

        if (filename == null || sourceScenario == null || targetScenario == null ||
                filename.isBlank() || sourceScenario.isBlank() || targetScenario.isBlank()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing parameters\"}");
            return;
        }

        Path featurePath = Paths.get(FEATURES_DIR, filename);
        if (!Files.exists(featurePath)) {
            sendResponse(exchange, 404, "application/json", "{\"error\":\"Feature file not found\"}");
            return;
        }

        try {
            java.util.List<String> lines = Files.readAllLines(featurePath, StandardCharsets.UTF_8);
            FeatureData data = parseFeature(lines);

            // Reorder
            if (data.orderedNames.contains(sourceScenario) && data.orderedNames.contains(targetScenario)) {
                int sourceIndex = data.orderedNames.indexOf(sourceScenario);
                int targetIndex = data.orderedNames.indexOf(targetScenario);

                data.orderedNames.remove(sourceIndex);
                data.orderedNames.add(targetIndex, sourceScenario);

                // Reconstruct file
                java.util.List<String> updatedLines = new java.util.ArrayList<>(data.headerBlock);
                for (String name : data.orderedNames) {
                    updatedLines.addAll(data.scenarioMap.get(name));
                }

                Files.write(featurePath, updatedLines, StandardCharsets.UTF_8);

                testsRan = false;
                allTestsPassed = false;

                sendResponse(exchange, 200, "application/json", "{\"success\":true}");
            } else {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Scenario not found in file\"}");
            }

        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // API: RUN SINGLE TEST
    // ============================================

    private static void handleRunSingle(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String filename = extractJsonField(body, "filename");
        String scenarioName = extractJsonField(body, "scenario");

        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("mvn");
            command.add("test");
            command.add("-Dtest=!TestRunner");
            if (filename != null && !filename.isBlank()) {
                command.add("-Dcucumber.features=src/test/resources/features/" + filename);
            }
            if (scenarioName != null && !scenarioName.isBlank()) {
                scenarioName = scenarioName.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
                command.add("-Dcucumber.filter.name=" + scenarioName);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            boolean passed = (exitCode == 0);

            testsRan = true;
            allTestsPassed = passed;
            lastTestOutput = output;

            String summary = extractTestSummary(output);
            String result = String.format("{\"success\":%s,\"exitCode\":%d,\"summary\":\"%s\",\"output\":\"%s\"}",
                    passed, exitCode, escapeJson(summary), escapeJson(lastTestOutput));
            sendResponse(exchange, 200, "application/json", result);

        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // API: RUN TEMP SCENARIO
    // ============================================

    private static void handleRunTemp(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        String content = extractJsonField(body, "content");

        if (content == null || content.isBlank()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing content\"}");
            return;
        }

        try {
            String runContent = content;
            if (!runContent.contains("Feature:")) {
                runContent = "@temp\nFeature: Temporary Test Feature\n\n" + runContent;
            }

            Path tempFeature = Paths.get("target", "temp_run", "temp.feature");
            Files.createDirectories(tempFeature.getParent());
            Files.writeString(tempFeature, runContent, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("mvn", "test", "-Dtest=!TestRunner",
                    "-Dcucumber.features=" + tempFeature.toString());
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            boolean passed = (exitCode == 0);

            String summary = extractTestSummary(output);
            String result = String.format("{\"success\":%s,\"exitCode\":%d,\"summary\":\"%s\",\"output\":\"%s\"}",
                    passed, exitCode, escapeJson(summary), escapeJson(output));
            sendResponse(exchange, 200, "application/json", result);

        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private static void serveJsonFile(HttpExchange exchange, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            sendResponse(exchange, 404, "application/json", "{\"error\":\"File not found: " + filePath + "\"}");
            return;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static void sendResponse(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String runGitCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }

    private static String extractTestSummary(String output) {
        for (String line : output.split("\n")) {
            if (line.contains("Tests run:") && line.contains("Failures:")) {
                return line.trim();
            }
        }
        return "No test summary found";
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0)
            return null;

        int colonIndex = json.indexOf(':', keyIndex + key.length());
        if (colonIndex < 0)
            return null;

        int valueStart = json.indexOf('"', colonIndex);
        if (valueStart < 0)
            return null;
        valueStart++; // skip quote

        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            if (json.charAt(valueEnd) == '"') {
                int backslashes = 0;
                int pos = valueEnd - 1;
                while (pos >= 0 && json.charAt(pos) == '\\') {
                    backslashes++;
                    pos--;
                }
                if (backslashes % 2 == 0) {
                    break;
                }
            }
            valueEnd++;
        }
        if (valueEnd >= json.length())
            return null;

        return json.substring(valueStart, valueEnd)
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html"))
            return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))
            return "text/css; charset=UTF-8";
        if (path.endsWith(".js"))
            return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json"))
            return "application/json; charset=UTF-8";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".svg"))
            return "image/svg+xml";
        return "text/plain; charset=UTF-8";
    }

    private static void handleGitPull(HttpExchange exchange) throws IOException {
        if (!isLocalhostOnly(exchange))
            return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        try {
            // Ensure we are on main
            ProcessBuilder pbCheckout = new ProcessBuilder("git", "checkout", "main");
            pbCheckout.directory(new File("."));
            Process pCheckout = pbCheckout.start();
            pCheckout.waitFor();

            // Pull from origin
            ProcessBuilder pbPull = new ProcessBuilder("git", "pull", "origin", "main");
            pbPull.directory(new File("."));
            pbPull.redirectErrorStream(true);
            Process pPull = pbPull.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pPull.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = pPull.waitFor();

            String result = String.format("{\"success\":%s,\"output\":\"%s\"}",
                    (exitCode == 0), escapeJson(output));
            sendResponse(exchange, 200, "application/json", result);

        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json",
                    "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    static class FeatureData {
        java.util.List<String> headerBlock = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<String>> scenarioMap = new java.util.LinkedHashMap<>();
        java.util.List<String> orderedNames = new java.util.ArrayList<>();
    }

    private static FeatureData parseFeature(java.util.List<String> lines) {
        FeatureData data = new FeatureData();
        java.util.List<String> currentBlock = new java.util.ArrayList<>();
        java.util.List<String> gapBuffer = new java.util.ArrayList<>();
        String currentScenarioName = null;

        for (String line : lines) {
            String clean = line.trim();
            String checkClean = clean.startsWith("#") ? clean.substring(1).trim() : clean;

            if (checkClean.startsWith("Scenario:") || checkClean.startsWith("Scenario Outline:")) {
                if (currentScenarioName == null) {
                    data.headerBlock.addAll(currentBlock);
                } else {
                    data.scenarioMap.put(currentScenarioName, currentBlock);
                }
                currentScenarioName = checkClean.replaceFirst("^Scenario(\\s+Outline)?:\\s*", "").trim();
                data.orderedNames.add(currentScenarioName);

                currentBlock = new java.util.ArrayList<>(gapBuffer);
                currentBlock.add(line);
                gapBuffer.clear();
            } else {
                if (clean.isEmpty() || clean.startsWith("@") || clean.startsWith("#")) {
                    gapBuffer.add(line);
                } else {
                    currentBlock.addAll(gapBuffer);
                    gapBuffer.clear();
                    currentBlock.add(line);
                }
            }
        }

        if (currentScenarioName != null) {
            currentBlock.addAll(gapBuffer);
            data.scenarioMap.put(currentScenarioName, currentBlock);
        } else {
            data.headerBlock.addAll(currentBlock);
            data.headerBlock.addAll(gapBuffer);
        }

        return data;
    }
}
