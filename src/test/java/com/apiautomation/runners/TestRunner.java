package com.apiautomation.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit Platform Suite runner for Cucumber tests.
 * <p>
 * Run all tests: {@code mvn test}
 * Run by tag: {@code mvn test -Dcucumber.filter.tags="@auth"}
 * Run specific feature:
 * {@code mvn test -Dcucumber.features="src/test/resources/features/auth.feature"}
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.apiautomation")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.apiautomation")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class TestRunner {
}
