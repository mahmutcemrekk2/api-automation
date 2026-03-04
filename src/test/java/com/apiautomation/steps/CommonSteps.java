package com.apiautomation.steps;

import com.apiautomation.client.ApiClient;
import com.apiautomation.context.GlobalTestState;
import com.apiautomation.context.TestContext;
import com.apiautomation.context.VariableResolver;
import com.apiautomation.utils.RandomDataGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generic, reusable Cucumber step definitions for API testing.
 * <p>
 * These steps are completely API-agnostic — they work with any REST API.
 * Modeled after Karate's approach but powered by Cucumber's Gherkin syntax.
 */
public class CommonSteps {

    private static final Logger logger = LoggerFactory.getLogger(CommonSteps.class);

    private final TestContext context;
    private final ApiClient apiClient;
    private final VariableResolver resolver;
    private final ObjectMapper objectMapper;

    public CommonSteps(TestContext context) {
        this.context = context;
        this.apiClient = new ApiClient(context);
        this.resolver = new VariableResolver(context);
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ============================================
    // SETUP & CONFIGURATION
    // ============================================

    @Given("system uses {string} service")
    public void systemUsesService(String serviceName) {
        apiClient.setBaseUrl(serviceName);
    }

    @Given("user sets header {string} to {string}")
    public void userSetsHeader(String headerName, String headerValue) {
        apiClient.addHeader(headerName, headerValue);
    }

    @Given("user sets the following headers:")
    public void userSetsTheFollowingHeaders(DataTable dataTable) {
        Map<String, String> headers = dataTable.asMap(String.class, String.class);
        headers.forEach((key, value) -> apiClient.addHeader(key, resolver.resolve(value)));
    }

    @Given("user is authenticated with token {string}")
    public void userIsAuthenticatedWithToken(String tokenKey) {
        String token = context.getString(tokenKey);
        if (token.isEmpty()) {
            throw new IllegalStateException("Token '" + tokenKey + "' not found in context");
        }
        apiClient.setAuthHeader(token);
    }

    /**
     * Shortcut step: performs full login flow using config credentials.
     * Stores: authToken, userId, firstName in context + sets auth header.
     */
    @Given("user is logged in as default")
    public void userIsLoggedInAsDefaultUser() {
        var config = com.apiautomation.config.EnvironmentConfig.load();
        String username = config.getDefaultUsername();
        String password = config.getDefaultPassword();
        performLogin(username, password);
    }

    /**
     * Shortcut step: performs full login flow with custom credentials.
     */
    @Given("user is logged in with username {string} and password {string}")
    public void userIsLoggedInWithCredentials(String username, String password) {
        String resolvedUser = resolver.resolve(username);
        String resolvedPass = resolver.resolve(password);
        performLogin(resolvedUser, resolvedPass);
    }

    private void performLogin(String username, String password) {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        apiClient.post("/auth/login", body);

        var response = context.getLastResponse();
        int status = response.getStatusCode();
        if (status != 200) {
            throw new IllegalStateException(
                    "Login failed with status " + status + ": " + response.getBody().asString());
        }

        context.extractAndStore("$.accessToken", "authToken");
        context.extractAndStore("$.id", "userId");
        context.extractAndStore("$.firstName", "firstName");
        apiClient.setAuthHeader(context.getString("authToken"));

        logger.info("Logged in as '{}' (userId: {})", username, context.getString("userId"));
    }

    @Given("user has stored {string} as {string}")
    public void userHasStored(String value, String key) {
        context.set(key, resolver.resolve(value));
    }

    // ============================================
    // REQUEST STEPS (IntelliJ Index Refresh)
    // ============================================

    @When("user sends {string} request to {string}")
    public void userSendsRequestTo(String method, String endpoint) {
        executeRequest(method, endpoint, Collections.emptyMap());
    }

    @When("user sends {string} request to {string} with query params:")
    public void userSendsRequestWithQueryParams(String method, String endpoint, DataTable dataTable) {
        Map<String, String> params = dataTable.asMap(String.class, String.class);
        executeRequest(method, endpoint, params);
    }

    @When("user sends {string} request to {string} with body:")
    public void userSendsRequestWithBody(String method, String endpoint, String body) {
        String resolvedBody = resolver.resolve(body);
        switch (method.toUpperCase()) {
            case "POST" -> apiClient.post(endpoint, resolvedBody);
            case "PUT" -> apiClient.put(endpoint, resolvedBody);
            case "PATCH" -> apiClient.patch(endpoint, resolvedBody);
            default -> throw new IllegalArgumentException("Method " + method + " does not support body");
        }
    }

    @When("user sends form {string} request to {string} with params:")
    public void userSendsFormRequest(String method, String endpoint, DataTable dataTable) {
        Map<String, String> params = dataTable.asMap(String.class, String.class);
        Map<String, String> resolvedParams = resolver.resolve(params);
        if ("POST".equalsIgnoreCase(method)) {
            apiClient.postForm(endpoint, resolvedParams);
        } else {
            throw new IllegalArgumentException("Form request only supports POST");
        }
    }

    @When("user sends {string} request to {string} with payload:")
    public void userSendsRequestWithPayload(String method, String endpoint, DataTable dataTable) {
        Map<String, String> payloadMap = dataTable.asMap(String.class, String.class);
        Map<String, String> resolvedPayload = resolver.resolve(payloadMap);
        try {
            String jsonBody = objectMapper.writeValueAsString(resolvedPayload);
            switch (method.toUpperCase()) {
                case "POST" -> apiClient.post(endpoint, jsonBody);
                case "PUT" -> apiClient.put(endpoint, jsonBody);
                case "PATCH" -> apiClient.patch(endpoint, jsonBody);
                default -> throw new IllegalArgumentException("Method " + method + " does not support body payload");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    private void executeRequest(String method, String endpoint, Map<String, String> params) {
        Map<String, String> resolvedParams = resolver.resolve(params);
        switch (method.toUpperCase()) {
            case "GET" -> apiClient.get(endpoint, resolvedParams);
            case "POST" -> apiClient.post(endpoint);
            case "PUT" -> apiClient.put(endpoint);
            case "DELETE" -> apiClient.delete(endpoint);
            case "PATCH" -> apiClient.patch(endpoint);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }

    // ============================================
    // RESPONSE STATUS VALIDATION
    // ============================================

    @Then("response status code should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        int actualStatus = context.getLastResponse().getStatusCode();
        assertEquals(expectedStatus, actualStatus,
                "Expected status " + expectedStatus + " but got " + actualStatus);
    }

    // ============================================
    // RESPONSE VALUE VALIDATION
    // ============================================

    @Then("the response {string} should be {string}")
    public void theResponseValueShouldBe(String jsonPath, String expectedValue) {
        String resolvedExpected = resolver.resolve(expectedValue);
        Object actualValue = context.extract(jsonPath);
        String actualStr = actualValue != null ? actualValue.toString() : null;
        attachAsJson(jsonPath + " (Actual)", actualStr);
        assertEquals(resolvedExpected, actualStr, "Value at '" + jsonPath + "' mismatch");
    }

    @Then("the response {string} should be {int}")
    public void theResponseValueShouldBeInt(String jsonPath, int expectedValue) {
        Number actualValue = context.extract(jsonPath);
        attachAsJson(jsonPath + " (Actual)", actualValue);
        assertNotNull(actualValue, "Expected '" + jsonPath + "' to exist");
        assertEquals(expectedValue, actualValue.intValue(), "Value at '" + jsonPath + "' mismatch");
    }

    @Then("the response {string} should be one of:")
    public void theResponseValueShouldBeOneOf(String jsonPath, DataTable dataTable) {
        List<String> allowedValues = dataTable.asList();
        Object actualValue = context.extract(jsonPath);
        String actualStr = actualValue != null ? actualValue.toString() : null;
        attachAsJson(jsonPath + " (Actual)", actualStr);
        assertTrue(allowedValues.contains(actualStr),
                "Expected '" + jsonPath + "' to be one of " + allowedValues + " but was '" + actualStr + "'");
    }

    @Then("the response {string} should match regex {string}")
    public void theResponseValueShouldMatchRegex(String jsonPath, String regex) {
        Object actualValue = context.extract(jsonPath);
        assertNotNull(actualValue, "Expected '" + jsonPath + "' to exist");
        String actualStr = actualValue.toString();
        attachAsJson(jsonPath + " (Actual)", actualStr);
        assertTrue(actualStr.matches(regex),
                "Expected '" + jsonPath + "' to match regex '" + regex + "' but was '" + actualStr + "'");
    }

    @Then("the response {string} should contain {string}")
    public void theResponseValueShouldContain(String jsonPath, String expectedSubstring) {
        String resolvedExpected = resolver.resolve(expectedSubstring);
        Object actualValue = context.extract(jsonPath);
        assertNotNull(actualValue, "Expected '" + jsonPath + "' to exist");
        String actualStr = actualValue.toString();
        attachAsJson(jsonPath + " (Actual)", actualStr);
        assertTrue(actualStr.contains(resolvedExpected),
                "Expected '" + jsonPath + "' to contain '" + resolvedExpected + "' but was '" + actualStr + "'");
    }

    // ============================================
    // BULK VALIDATION
    // ============================================

    @Then("the response should contain the following:")
    public void theResponseShouldContainTheFollowing(DataTable dataTable) {
        Map<String, String> expectations = dataTable.asMap(String.class, String.class);
        expectations.forEach((jsonPath, expectedSubstring) -> {
            String resolvedExpected = resolver.resolve(expectedSubstring);
            Object actualValue = context.extract(jsonPath);
            assertNotNull(actualValue, "Expected '" + jsonPath + "' to exist");
            String actualStr = actualValue.toString();
            attachAsJson(jsonPath + " (Actual)", actualStr);
            assertTrue(actualStr.contains(resolvedExpected),
                    "Expected '" + jsonPath + "' to contain '" + resolvedExpected + "' but was '" + actualStr + "'");
            logger.info("Verified '{}' contains '{}'", jsonPath, resolvedExpected);
        });
    }

    @Then("the response should not contain the following:")
    public void theResponseShouldNotContainTheFollowing(DataTable dataTable) {
        Map<String, String> expectations = dataTable.asMap(String.class, String.class);
        expectations.forEach((jsonPath, forbiddenSubstring) -> {
            String resolvedForbidden = resolver.resolve(forbiddenSubstring);
            Object actualValue = context.extract(jsonPath);
            assertNotNull(actualValue, "Expected '" + jsonPath + "' to exist");
            String actualStr = actualValue.toString();
            attachAsJson(jsonPath + " (Actual)", actualStr);
            assertFalse(actualStr.contains(resolvedForbidden),
                    "Expected '" + jsonPath + "' to NOT contain '" + resolvedForbidden + "' but was '" + actualStr
                            + "'");
            logger.info("Verified '{}' does not contain '{}'", jsonPath, resolvedForbidden);
        });
    }

    // ============================================
    // ADVANCED FIELD VALIDATION
    // ============================================

    @Then("response should match:")
    public void theResponseShouldContainTheFollowingFields(DataTable dataTable) {
        Map<String, String> fields = dataTable.asMap(String.class, String.class);
        fields.forEach((jsonPath, condition) -> {
            Object value = context.extract(jsonPath);
            attachAsJson(jsonPath, value);

            switch (condition.toLowerCase()) {
                case "exists", "present", "not null" ->
                    assertNotNull(value, "Expected '" + jsonPath + "' to exist (not be null)");
                case "field exists" -> {
                    String responseBody = context.getLastResponse().getBody().asString();
                    try {
                        JsonNode rootNode = objectMapper.readTree(responseBody);
                        assertTrue(checkFieldExists(rootNode, jsonPath),
                                "Expected field '" + jsonPath + "' to exist in response (value can be null)");
                    } catch (Exception e) {
                        fail("Failed to parse response body: " + e.getMessage());
                    }
                }
                case "field not exists" -> {
                    String responseBody = context.getLastResponse().getBody().asString();
                    try {
                        JsonNode rootNode = objectMapper.readTree(responseBody);
                        assertFalse(checkFieldExists(rootNode, jsonPath),
                                "Expected field '" + jsonPath + "' to NOT exist in response but it was found");
                    } catch (Exception e) {
                        fail("Failed to parse response body: " + e.getMessage());
                    }
                }
                case "not empty" -> {
                    assertNotNull(value, "Expected '" + jsonPath + "' to exist");
                    if (value instanceof String s) {
                        assertFalse(s.isEmpty(), "Expected '" + jsonPath + "' to not be empty");
                    } else if (value instanceof List<?> l) {
                        assertFalse(l.isEmpty(), "Expected '" + jsonPath + "' to not be empty");
                    } else if (value instanceof Collection<?> c) {
                        assertFalse(c.isEmpty(), "Expected '" + jsonPath + "' to not be empty");
                    }
                }
                case "null" ->
                    assertNull(value, "Expected '" + jsonPath + "' to be null");
                default -> {
                    String resolvedExpected = resolver.resolve(condition);
                    assertNotNull(value, "Expected '" + jsonPath + "' to exist for value comparison");

                    if (resolvedExpected.startsWith("contains ")) {
                        String substr = resolvedExpected.substring("contains ".length());
                        assertTrue(value.toString().contains(substr),
                                "Expected '" + jsonPath + "' to contain '" + substr + "' but was '" + value + "'");
                    } else if (resolvedExpected.startsWith("matches ")) {
                        String regex = resolvedExpected.substring("matches ".length());
                        assertTrue(value.toString().matches(regex),
                                "Expected '" + jsonPath + "' to match regex '" + regex + "' but was '" + value + "'");
                    } else {
                        assertEquals(resolvedExpected, value.toString(),
                                "Expected '" + jsonPath + "' to be '" + resolvedExpected + "' but was '" + value + "'");
                    }
                }
            }
        });
    }

    @Then("the response should contain the following fields equal to:")
    public void theResponseFieldsShouldBeEqualTo(DataTable dataTable) {
        Map<String, String> mappings = dataTable.asMap(String.class, String.class);

        mappings.forEach((jsonPath, expectedRawValue) -> {
            String expectedValue = resolver.resolve(expectedRawValue);
            List<Object> actualValues = context.extract(jsonPath);

            assertNotNull(actualValues, "Path '" + jsonPath + "' did not return a list");
            assertFalse(actualValues.isEmpty(), "Expected '" + jsonPath + "' to be not empty");

            safeAllureAttachment("Assertion Path", jsonPath);
            safeAllureAttachment("Expected Value", expectedValue);
            safeAllureAttachment("Actual Values", actualValues.toString());

            logger.info("ASSERTION START - Path: {}, Expected: {}, ActualCnt: {}", jsonPath, expectedValue,
                    actualValues.size());

            List<String> errors = new ArrayList<>();
            for (int i = 0; i < actualValues.size(); i++) {
                try {
                    assertValueEquals(expectedValue, actualValues.get(i), jsonPath, i);
                    logger.debug("[{}][{}] OK → {}", jsonPath, i, actualValues.get(i));
                } catch (AssertionError e) {
                    String error = "[" + jsonPath + "][" + i + "] expected='" + expectedValue + "' actual='"
                            + actualValues.get(i) + "'";
                    errors.add(error);
                    safeAllureAttachment("Mismatch at index " + i, error);
                    logger.error(error);
                }
            }

            if (!errors.isEmpty()) {
                String message = "Assertion failed for path: " + jsonPath + "\n" +
                        "Expected value: " + expectedValue + "\n" +
                        "Mismatch count: " + errors.size() + "\n" +
                        "Details:\n" + String.join("\n", errors);
                safeAllureAttachment("Assertion Summary", message);
                fail(message);
            }

            logger.info("ASSERTION PASSED for path: {}", jsonPath);
        });
    }

    // ============================================
    // DATA STORE STEPS
    // ============================================

    @And("user stores response {string} as {string}")
    public void userStoresAs(String jsonPath, String key) {
        context.extractAndStore(jsonPath, key);
    }

    @And("user stores response body as {string}")
    public void userStoresResponseBodyAs(String key) {
        String body = context.getLastResponse().getBody().asString();
        if (body == null)
            throw new IllegalStateException("No response body available");
        context.set(key, body);
    }

    @And("user stores the following values from the response:")
    public void userStoresTheFollowingValuesFromTheResponse(DataTable dataTable) {
        Map<String, String> mapping = dataTable.asMap(String.class, String.class);
        mapping.forEach((jsonPath, key) -> context.extractAndStore(jsonPath, key));
    }

    @And("user stores the cookie {string} as {string}")
    public void userStoresTheCookieAs(String cookieName, String key) {
        var response = context.getLastResponse();
        if (response == null)
            throw new IllegalStateException("No response available to extract cookie from");
        String cookieValue = response.getCookie(cookieName);
        if (cookieValue == null)
            throw new IllegalStateException("Cookie '" + cookieName + "' not found in response");
        context.set(key, cookieValue);
        logger.info("Stored cookie '{}' as '{}'", cookieName, key);
    }

    @And("with headers:")
    public void withHeaders(DataTable dataTable) {
        Map<String, String> headers = dataTable.asMap(String.class, String.class);
        headers.forEach((key, value) -> apiClient.addHeader(key, resolver.resolve(value)));
    }

    // ============================================
    // ARRAY / FILTER STEPS
    // ============================================

    @Then("the response item in {string} is {string} should have:")
    public void theResponseArrayItemShouldHave(String fieldPath, String filterValue, DataTable dataTable) {
        String[] parsed = parseArrayFilterPath(fieldPath);
        String arrayPath = parsed[0];
        String filterField = parsed[1];
        String resolvedFilterValue = resolver.resolve(filterValue);
        Map<String, String> expectations = dataTable.asMap(String.class, String.class);

        expectations.forEach((checkField, expectedValue) -> {
            String resolvedExpected = resolver.resolve(expectedValue);
            String jsonPath = arrayPath + "[?(@." + filterField + "=='" + resolvedFilterValue + "')]." + checkField;
            List<Object> values = context.extract(jsonPath);
            assertNotNull(values, "No items found matching " + filterField + "='" + resolvedFilterValue + "'");
            assertFalse(values.isEmpty(), "No items found matching " + filterField + "='" + resolvedFilterValue + "'");
            assertEquals(resolvedExpected, values.get(0).toString(),
                    "Expected '" + checkField + "' to be '" + resolvedExpected + "' but was '" + values.get(0) + "'");
            logger.info("Verified {}='{}' for item with {}='{}'", checkField, resolvedExpected, filterField,
                    resolvedFilterValue);
        });
    }

    @And("user stores {string} from {string} is {string} as {string}")
    public void userStoresFieldFromArrayWhere(String targetField, String fieldPath, String filterValue,
            String storeKey) {
        String[] parsed = parseArrayFilterPath(fieldPath);
        String arrayPath = parsed[0];
        String filterField = parsed[1];
        String resolvedFilterValue = resolver.resolve(filterValue);
        String jsonPath = arrayPath + "[?(@." + filterField + "=='" + resolvedFilterValue + "')]." + targetField;
        List<Object> values = context.extract(jsonPath);

        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(
                    "No items found matching " + filterField + "='" + resolvedFilterValue + "'");
        }

        int randomIndex = new Random().nextInt(values.size());
        String storedValue = values.get(randomIndex).toString();
        context.set(storeKey, storedValue);
        logger.info("Stored {}='{}' (random pick {}/{}) from item where {}='{}' as '{}'",
                targetField, storedValue, randomIndex + 1, values.size(), filterField, resolvedFilterValue, storeKey);
    }

    @And("user stores from {string} is {string}:")
    public void userStoresFieldsFromArrayWhere(String fieldPath, String filterValue, DataTable dataTable) {
        String[] parsed = parseArrayFilterPath(fieldPath);
        String arrayPath = parsed[0];
        String filterField = parsed[1];
        String resolvedFilterValue = resolver.resolve(filterValue);
        Map<String, String> mapping = dataTable.asMap(String.class, String.class);

        // Determine total matching items count using the first target field
        String firstField = mapping.keySet().iterator().next();
        String countPath = arrayPath + "[?(@." + filterField + "=='" + resolvedFilterValue + "')]." + firstField;
        List<Object> countValues = context.extract(countPath);

        if (countValues == null || countValues.isEmpty()) {
            throw new IllegalStateException(
                    "No items found matching " + filterField + "='" + resolvedFilterValue + "'");
        }

        // Pick one random index and use it for all fields (same item)
        int randomIndex = new Random().nextInt(countValues.size());
        logger.info("Randomly selected item {}/{} where {}='{}'",
                randomIndex + 1, countValues.size(), filterField, resolvedFilterValue);

        mapping.forEach((targetField, storeKey) -> {
            String jsonPath = arrayPath + "[?(@." + filterField + "=='" + resolvedFilterValue + "')]." + targetField;
            List<Object> values = context.extract(jsonPath);

            if (values == null || values.size() <= randomIndex) {
                throw new IllegalStateException(
                        "Item at index " + randomIndex + " not found for field '" + targetField + "'");
            }

            String storedValue = values.get(randomIndex).toString();
            context.set(storeKey, storedValue);
            logger.info("Stored {}='{}' as '{}'", targetField, storedValue, storeKey);
        });
    }

    // ============================================
    // GLOBAL STATE STEPS
    // ============================================

    @Then("user stores response {string} as global variable {string}")
    public void userSavesAsGlobalVariable(String jsonPath, String globalKey) {
        Object extractedValue = context.extract(jsonPath);
        if (extractedValue == null) {
            throw new IllegalStateException("Value for jsonPath '" + jsonPath + "' not found in response");
        }
        String value = extractedValue.toString();
        if (value.isEmpty()) {
            throw new IllegalStateException("Extracted value for jsonPath '" + jsonPath + "' is empty");
        }
        GlobalTestState.set(globalKey, value);
        logger.info("Saved '{}' (from '{}') as global variable '{}'", value, jsonPath, globalKey);
    }

    @And("user stores value {string} as global variable {string}")
    public void userStoresValueAsGlobalVariable(String value, String globalKey) {
        String resolvedValue = resolver.resolve(value);
        GlobalTestState.set(globalKey, resolvedValue);
        logger.info("Stored value '{}' as global variable '{}'", resolvedValue, globalKey);
    }

    @Given("system loads global variable {string} as {string}")
    public void systemLoadsGlobalVariableAs(String globalKey, String contextKey) {
        String value = GlobalTestState.get(globalKey);
        if (value == null) {
            throw new IllegalStateException("Global variable '" + globalKey + "' not found");
        }
        context.set(contextKey, value);
        logger.info("Loaded global variable '{}' ('{}') as '{}'", globalKey, value, contextKey);
    }

    // ============================================
    // SCHEMA VALIDATION
    // ============================================

    @Then("the response should match JSON schema example {string}")
    public void theResponseShouldMatchJsonSchemaExample(String exampleFileName) throws Exception {
        String responseBody = context.getLastResponse().getBody().asString();
        if (responseBody == null || responseBody.isEmpty()) {
            throw new RuntimeException("No response body available for JSON schema example validation");
        }

        try (InputStream exampleStream = getClass().getClassLoader()
                .getResourceAsStream("schemas/" + exampleFileName)) {
            if (exampleStream == null) {
                throw new IllegalArgumentException("Example file not found: schemas/" + exampleFileName);
            }

            JsonNode exampleJson = objectMapper.readTree(exampleStream);
            JsonNode responseJson = objectMapper.readTree(responseBody);

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            validateOpenApiExample(exampleJson, responseJson, "$", errors, warnings);

            if (!errors.isEmpty()) {
                StringBuilder errorMessages = new StringBuilder();
                for (String error : errors) {
                    errorMessages.append("- ").append(error).append("\n");
                }
                Allure.addAttachment("JSON Schema Example Validation Errors", "text/plain", errorMessages.toString(),
                        ".txt");
                attachAsJson("Response Body", responseBody);
                attachAsJson("Expected Example", exampleJson.toString());
                fail("JSON schema example validation failed:\n" + errorMessages.toString());
            }

            if (!warnings.isEmpty()) {
                StringBuilder warningMessages = new StringBuilder();
                for (String warning : warnings) {
                    warningMessages.append("- ").append(warning).append("\n");
                }
                logger.warn("Response has extra properties not in JSON schema example (marking as BROKEN):\n{}",
                        warningMessages);

                Allure.addAttachment("JSON Schema Example Validation Warnings", "text/plain",
                        "Extra properties detected - Example needs update:\n" + warningMessages.toString(), ".txt");
                attachAsJson("Response Body", responseBody);
                attachAsJson("Expected Example", exampleJson.toString());
                throw new RuntimeException("Response has extra properties not in JSON schema example (needs update):\n"
                        + warningMessages.toString());
            }

            logger.info("Response matches JSON schema example: {}", exampleFileName);
            Allure.addAttachment("JSON Schema Example Validation", "text/plain",
                    "✓ Response matches JSON schema example: " + exampleFileName, ".txt");
        }
    }

    // ============================================
    // RANDOM DATA GENERATION STEPS
    // ============================================

    @Given("I generate a random phone number as {string}")
    public void userGeneratesARandomPhoneNumberAs(String variableName) {
        context.set(variableName, RandomDataGenerator.generatePhoneNumber());
    }

    @Given("I generate a random email as {string}")
    public void userGeneratesARandomEmailAs(String variableName) {
        context.set(variableName, RandomDataGenerator.generateEmail());
    }

    @Given("I generate a random slug as {string}")
    public void userGeneratesARandomSlugAs(String variableName) {
        context.set(variableName, RandomDataGenerator.generateSlug());
    }

    @Given("I generate a random timestamp as {string}")
    public void userGeneratesARandomTimestampAs(String variableName) {
        context.set(variableName, RandomDataGenerator.generateTimestamp());
    }

    @Given("I generate a random {int} digit number as {string}")
    public void userGeneratesARandomDigitNumberAs(int length, String variableName) {
        context.set(variableName, RandomDataGenerator.generateRandomNumber(length));
    }

    // ============================================
    // UTILITY STEPS
    // ============================================

    @And("system waits for {long} seconds")
    public void systemWaitsForSeconds(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private void attachAsJson(String name, Object value) {
        if (value == null)
            return;
        try {
            Object objectToSerialize = value;
            if (value instanceof String str) {
                try {
                    objectToSerialize = objectMapper.readTree(str);
                } catch (Exception ignored) {
                    // Keep as string
                }
            }
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectToSerialize);
            InputStream inputStream = new ByteArrayInputStream(prettyJson.getBytes(StandardCharsets.UTF_8));
            Allure.addAttachment(name, "application/json; charset=UTF-8", inputStream, ".json");
        } catch (Exception e) {
            Allure.addAttachment(name, value.toString());
        }
    }

    private void safeAllureAttachment(String name, String content) {
        try {
            Allure.addAttachment(name, content != null ? content : "null");
        } catch (Exception e) {
            logger.debug("Allure attachment skipped: {}", e.getMessage());
        }
    }

    private String[] parseArrayFilterPath(String fieldPath) {
        if (fieldPath.contains("[*].")) {
            String[] parts = fieldPath.split("\\[\\*]\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid field path '" + fieldPath + "', expected format: '$.array[*].filterField'");
            }
            return parts;
        } else {
            int lastDot = fieldPath.lastIndexOf('.');
            if (lastDot <= 0) {
                throw new IllegalArgumentException(
                        "Invalid field path '" + fieldPath + "', expected format: '$.array.field'");
            }
            return new String[] { fieldPath.substring(0, lastDot), fieldPath.substring(lastDot + 1) };
        }
    }

    private boolean checkFieldExists(JsonNode rootNode, String jsonPath) {
        String cleanPath = jsonPath.replaceFirst("^\\$\\.?", "");
        if (cleanPath.isEmpty())
            return true;

        String[] segments = cleanPath.split("\\.");
        return checkFieldExistsRecursive(rootNode, segments, 0);
    }

    private boolean checkFieldExistsRecursive(JsonNode node, String[] segments, int index) {
        if (node == null)
            return false;
        if (index >= segments.length)
            return true;

        String segment = segments[index];

        // Handle wildcard: "items[*]"
        Matcher wildcardMatcher = Pattern.compile("(.+?)\\[\\*]").matcher(segment);
        if (wildcardMatcher.matches()) {
            String fieldName = wildcardMatcher.group(1);
            JsonNode arrayNode = node.get(fieldName);
            if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty())
                return false;

            for (int i = 0; i < arrayNode.size(); i++) {
                if (index == segments.length - 1)
                    continue; // No more segments, array exists
                if (!checkFieldExistsRecursive(arrayNode.get(i), segments, index + 1))
                    return false;
            }
            return true;
        }

        // Handle "[*]" standalone
        if ("[*]".equals(segment)) {
            if (!node.isArray() || node.isEmpty())
                return false;
            for (int i = 0; i < node.size(); i++) {
                if (index == segments.length - 1)
                    continue;
                if (!checkFieldExistsRecursive(node.get(i), segments, index + 1))
                    return false;
            }
            return true;
        }

        // Handle specific index: "content[0]"
        Matcher indexMatcher = Pattern.compile("(.+?)\\[(\\d+)]").matcher(segment);
        if (indexMatcher.matches()) {
            String fieldName = indexMatcher.group(1);
            int idx = Integer.parseInt(indexMatcher.group(2));
            JsonNode arrayNode = node.get(fieldName);
            if (arrayNode == null || !arrayNode.isArray() || idx >= arrayNode.size())
                return false;
            return checkFieldExistsRecursive(arrayNode.get(idx), segments, index + 1);
        }

        // Handle standalone index: "[0]"
        if (segment.startsWith("[") && segment.endsWith("]")) {
            int idx = Integer.parseInt(segment.substring(1, segment.length() - 1));
            if (!node.isArray() || idx >= node.size())
                return false;
            return checkFieldExistsRecursive(node.get(idx), segments, index + 1);
        }

        // Regular field name
        if (index == segments.length - 1) {
            return node.has(segment);
        }
        return checkFieldExistsRecursive(node.get(segment), segments, index + 1);
    }

    private void validateOpenApiExample(JsonNode expected, JsonNode actual, String path, List<String> errors,
            List<String> warnings) {
        if (expected.isObject() && actual.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> expectedFields = expected.fields();
            while (expectedFields.hasNext()) {
                Map.Entry<String, JsonNode> expectedEntry = expectedFields.next();
                String fieldName = expectedEntry.getKey();
                JsonNode expectedFieldNode = expectedEntry.getValue();
                JsonNode actualFieldNode = actual.get(fieldName);

                if (actualFieldNode == null || actualFieldNode.isMissingNode()) {
                    errors.add(path + "." + fieldName + ": missing field in response");
                } else {
                    validateOpenApiExample(expectedFieldNode, actualFieldNode, path + "." + fieldName, errors,
                            warnings);
                }
            }

            Iterator<String> actualFieldNames = actual.fieldNames();
            while (actualFieldNames.hasNext()) {
                String actualFieldName = actualFieldNames.next();
                if (!expected.has(actualFieldName)) {
                    warnings.add(
                            path + "." + actualFieldName + ": extra property found in response but not in example");
                }
            }
        } else if (expected.isArray() && actual.isArray()) {
            if (!expected.isEmpty() && !actual.isEmpty()) {
                JsonNode expectedItem = expected.get(0);
                for (int i = 0; i < actual.size(); i++) {
                    JsonNode actualItem = actual.get(i);
                    // For arrays of objects/arrays, iterate recursively.
                    if (expectedItem.isObject() || expectedItem.isArray()) {
                        validateOpenApiExample(expectedItem, actualItem, path + "[" + i + "]", errors, warnings);
                    } else {
                        // For arrays of primitives (strings, numbers, booleans)
                        if (expectedItem.isTextual() && !actualItem.isTextual()) {
                            errors.add(path + "[" + i + "]: expected string but got " + actualItem.getNodeType());
                        } else if (expectedItem.isNumber() && !actualItem.isNumber()) {
                            errors.add(path + "[" + i + "]: expected number but got " + actualItem.getNodeType());
                        } else if (expectedItem.isBoolean() && !actualItem.isBoolean()) {
                            errors.add(path + "[" + i + "]: expected boolean but got " + actualItem.getNodeType());
                        }
                    }
                }
            }
        } else if (expected.isObject() && !actual.isObject()) {
            errors.add(path + ": expected object but got " + actual.getNodeType());
        } else if (expected.isArray() && !actual.isArray()) {
            errors.add(path + ": expected array but got " + actual.getNodeType());
        } else if (expected.isTextual() && !actual.isTextual()) {
            errors.add(path + ": expected string but got " + actual.getNodeType());
        } else if (expected.isNumber() && !actual.isNumber()) {
            errors.add(path + ": expected number but got " + actual.getNodeType());
        } else if (expected.isBoolean() && !actual.isBoolean()) {
            errors.add(path + ": expected boolean but got " + actual.getNodeType());
        }
    }

    private void assertValueEquals(String expectedRaw, Object actual, String path, int index) {
        if (actual == null) {
            fail("[" + path + "][" + index + "] actual value is null");
        }

        try {
            BigDecimal expectedNumber = new BigDecimal(expectedRaw);
            if (actual instanceof Number) {
                BigDecimal actualNumber = new BigDecimal(actual.toString());
                if (expectedNumber.compareTo(actualNumber) != 0) {
                    fail("[" + path + "][" + index + "] numeric mismatch expected=" + expectedNumber + " actual="
                            + actualNumber);
                }
                return;
            }
        } catch (NumberFormatException ignored) {
            // Not a number, continue with string comparison
        }

        if (!expectedRaw.equals(actual.toString())) {
            fail("[" + path + "][" + index + "] string mismatch expected='" + expectedRaw + "' actual='" + actual
                    + "'");
        }
    }
}
