package com.apiautomation.client;

import com.apiautomation.config.EnvironmentConfig;
import com.apiautomation.context.TestContext;
import com.apiautomation.context.VariableResolver;
import io.restassured.RestAssured;
import io.restassured.config.DecoderConfig;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST Assured wrapper that handles all HTTP operations.
 * <p>
 * Automatically resolves placeholders in endpoints and body,
 * and stores the response in the TestContext.
 */
public class ApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);

    private final TestContext context;
    private final VariableResolver resolver;
    private final EnvironmentConfig config;

    private final RestAssuredConfig restAssuredConfig = RestAssuredConfig.config()
            .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"))
            .decoderConfig(DecoderConfig.decoderConfig().defaultContentCharset("UTF-8"));

    /**
     * Filter that prevents REST Assured from throwing HttpResponseException
     * for non-2xx status codes. This allows us to validate status codes
     * in Cucumber steps instead.
     */
    private static final Filter NO_FAIL_FILTER = new Filter() {
        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            responseSpec.statusCode(org.hamcrest.Matchers.any(Integer.class));
            return ctx.next(requestSpec, responseSpec);
        }
    };

    /**
     * Filter that logs the outgoing request as a cURL command for easy debugging.
     */
    private static final Filter CURL_LOGGING_FILTER = new Filter() {
        @Override
        public Response filter(FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {

            StringBuilder curl = new StringBuilder(
                    "curl -X " + requestSpec.getMethod() + " '" + requestSpec.getURI() + "'");

            requestSpec.getHeaders().forEach(h -> {
                curl.append(" \\\n  -H '").append(h.getName()).append(": ").append(h.getValue().replace("'", "'\\''"))
                        .append("'");
            });

            if (requestSpec.getBody() != null) {
                String body = requestSpec.getBody().toString().replace("'", "'\\''");
                // Avoid logging enormous bodies directly if needed, but for typical API testing
                // it's fine
                curl.append(" \\\n  -d '").append(body).append("'");
            } else if (!requestSpec.getFormParams().isEmpty()) {
                requestSpec.getFormParams().forEach((k, v) -> {
                    String cleanKey = k.replace("'", "'\\''");
                    String cleanVal = v.toString().replace("'", "'\\''");
                    curl.append(" \\\n  -d '").append(cleanKey).append("=").append(cleanVal).append("'");
                });
            }

            logger.info("Executing Request:\n{}\n", curl.toString());

            Response response = ctx.next(requestSpec, responseSpec);

            logger.info("Response Status: {}", response.getStatusCode());
            if (response.getBody() != null) {
                try {
                    logger.info("Response Body:\n{}", response.getBody().asPrettyString());
                } catch (Exception e) {
                    logger.info("Response Body: [Could not parse as pretty string] {}", response.getBody().asString());
                }
            }

            return response;
        }
    };

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    public ApiClient(TestContext context) {
        this.context = context;
        this.resolver = new VariableResolver(context);
        this.config = EnvironmentConfig.load();
    }

    // ========== HTTP Methods ==========

    public Response get(String endpoint, Map<String, String> queryParams) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        Map<String, String> resolvedParams = resolver.resolve(queryParams);

        logger.info("GET {}{} with params: {}", context.getCurrentBaseUrl(), resolvedEndpoint, resolvedParams);

        Response response = buildRequest()
                .queryParams(resolvedParams)
                .get(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response get(String endpoint) {
        return get(endpoint, Map.of());
    }

    public Response post(String endpoint, String body) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        String resolvedBody = resolver.resolve(body);

        logger.info("POST {}{}", context.getCurrentBaseUrl(), resolvedEndpoint);
        logger.debug("Body: {}", resolvedBody);

        Response response = buildRequest()
                .contentType(ContentType.JSON)
                .body(resolvedBody)
                .post(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response post(String endpoint) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        logger.info("POST {}{}", context.getCurrentBaseUrl(), resolvedEndpoint);

        Response response = buildRequest()
                .contentType(ContentType.JSON)
                .post(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response postForm(String endpoint, Map<String, String> formParams) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        Map<String, String> resolvedParams = resolver.resolve(formParams);
        String baseUrl = getBaseUrl();

        logger.info("POST (form) {}{}", baseUrl, resolvedEndpoint);
        logger.debug("Form params: {}", resolvedParams);

        Response response = buildRequest()
                .contentType(ContentType.URLENC)
                .formParams(resolvedParams)
                .post(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response put(String endpoint, String body) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        String resolvedBody = resolver.resolve(body);

        logger.info("PUT {}{}", context.getCurrentBaseUrl(), resolvedEndpoint);

        RequestSpecification request = buildRequest().contentType(ContentType.JSON);
        Response response = (resolvedBody != null && !resolvedBody.isEmpty())
                ? request.body(resolvedBody).put(resolvedEndpoint)
                : request.put(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response put(String endpoint) {
        return put(endpoint, null);
    }

    public Response delete(String endpoint) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        logger.info("DELETE {}{}", context.getCurrentBaseUrl(), resolvedEndpoint);

        Response response = buildRequest().delete(resolvedEndpoint);
        context.setLastResponse(response);
        return response;
    }

    public Response patch(String endpoint, String body) {
        String resolvedEndpoint = resolver.resolve(endpoint);
        String resolvedBody = resolver.resolve(body);

        logger.info("PATCH {}{}", context.getCurrentBaseUrl(), resolvedEndpoint);

        RequestSpecification request = buildRequest().contentType(ContentType.JSON);
        Response response = (resolvedBody != null && !resolvedBody.isEmpty())
                ? request.body(resolvedBody).patch(resolvedEndpoint)
                : request.patch(resolvedEndpoint);

        context.setLastResponse(response);
        return response;
    }

    public Response patch(String endpoint) {
        return patch(endpoint, null);
    }

    // ========== Configuration ==========

    /**
     * Sets the base URL. If the service name matches a known alias, resolves it.
     * Otherwise, uses the value as-is (supports full URLs).
     */
    public void setBaseUrl(String serviceName) {
        String url = switch (serviceName.toLowerCase()) {
            case "dummyjson", "default" -> config.getBaseUrl();
            default -> serviceName; // Allow setting arbitrary base URLs
        };
        context.setCurrentBaseUrl(url);
        logger.debug("Base URL set to: {}", url);
    }

    public void setAuthHeader(String token) {
        context.getHeaders().put("Authorization", "Bearer " + token);
        logger.debug("Set auth header");
    }

    public void addHeader(String key, String value) {
        context.getHeaders().put(key, resolver.resolve(value));
    }

    // ========== Private ==========

    private RequestSpecification buildRequest() {
        String baseUrl = getBaseUrl();

        return RestAssured.given()
                .config(restAssuredConfig)
                .baseUri(baseUrl)
                .headers(context.getHeaders())
                .filter(NO_FAIL_FILTER)
                .filter(CURL_LOGGING_FILTER);
    }

    private String getBaseUrl() {
        String baseUrl = context.getCurrentBaseUrl();
        return (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : config.getBaseUrl();
    }
}
