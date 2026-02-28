package com.apiautomation.hooks;

import com.apiautomation.context.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber lifecycle hooks for setup and teardown.
 */
public class Hooks {

    private static final Logger logger = LoggerFactory.getLogger(Hooks.class);

    private final TestContext context;

    public Hooks(TestContext context) {
        this.context = context;
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        logger.info("========================================");
        logger.info("Starting scenario: {}", scenario.getName());
        logger.info("Tags: {}", scenario.getSourceTagNames());
        logger.info("========================================");
    }

    @After
    public void afterScenario(Scenario scenario) {
        // Attach last response to Allure report if scenario failed
        if (scenario.isFailed() && context.getLastResponse() != null) {
            try {
                String responseBody = context.getLastResponse().getBody().asString();
                int statusCode = context.getLastResponse().getStatusCode();
                String attachment = "Status: " + statusCode + "\n\n" + responseBody;
                Allure.addAttachment("Last Response (on failure)", "application/json", attachment, ".json");
            } catch (Exception e) {
                logger.warn("Could not attach response on failure: {}", e.getMessage());
            }
        }

        // Log result
        logger.info("========================================");
        logger.info("Scenario '{}' - Status: {}", scenario.getName(), scenario.getStatus());
        logger.info("========================================");

        // Clear context for next scenario
        context.clear();
    }
}
