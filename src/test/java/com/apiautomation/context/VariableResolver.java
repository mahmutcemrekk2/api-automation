package com.apiautomation.context;

import com.apiautomation.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves placeholder variables in strings.
 * <p>
 * Supports two patterns:
 * <ul>
 * <li>{@code {{variableName}}} — double brace pattern</li>
 * <li>{@code ${variableName}} — dollar brace pattern</li>
 * </ul>
 * Variables are first looked up in the TestContext store,
 * then in the EnvironmentConfig.
 */
public class VariableResolver {

    private static final Logger logger = LoggerFactory.getLogger(VariableResolver.class);

    private static final Pattern DOUBLE_BRACE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern DOLLAR_BRACE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final TestContext context;

    public VariableResolver(TestContext context) {
        this.context = context;
    }

    /**
     * Resolves all placeholders in the input string.
     */
    public String resolve(String input) {
        if (input == null)
            return "";

        // First resolve {{variable}} pattern
        String result = resolvePattern(input, DOUBLE_BRACE_PATTERN);
        // Then resolve ${variable} pattern
        result = resolvePattern(result, DOLLAR_BRACE_PATTERN);

        return result;
    }

    private String resolvePattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = resolveVariable(variableName, matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String resolveVariable(String variableName, String originalPlaceholder) {
        String value = context.getString(variableName);

        if (value.isEmpty() && !context.has(variableName)) {
            // Check in EnvironmentConfig
            String configValue = EnvironmentConfig.load().get(variableName);
            if (configValue != null) {
                logger.debug("Resolved '{}' from EnvironmentConfig to '{}'", originalPlaceholder, configValue);
                return configValue;
            }

            logger.warn("Variable '{}' not found in context or config, keeping placeholder", variableName);
            return originalPlaceholder;
        }

        logger.debug("Resolved '{}' to '{}'", originalPlaceholder, value);
        return value;
    }

    /**
     * Resolves all values in a map.
     */
    public Map<String, String> resolve(Map<String, String> params) {
        Map<String, String> resolved = new HashMap<>();
        params.forEach((key, value) -> resolved.put(key, resolve(value)));
        return resolved;
    }

    /**
     * Checks if the input contains any placeholders.
     */
    public boolean hasPlaceholders(String input) {
        return DOUBLE_BRACE_PATTERN.matcher(input).find()
                || DOLLAR_BRACE_PATTERN.matcher(input).find();
    }

    /**
     * Extracts all placeholder names from the input.
     */
    public List<String> extractPlaceholderNames(String input) {
        List<String> names = new ArrayList<>();
        addMatchedGroups(input, DOUBLE_BRACE_PATTERN, names);
        addMatchedGroups(input, DOLLAR_BRACE_PATTERN, names);
        return names;
    }

    private void addMatchedGroups(String input, Pattern pattern, List<String> names) {
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
    }
}
