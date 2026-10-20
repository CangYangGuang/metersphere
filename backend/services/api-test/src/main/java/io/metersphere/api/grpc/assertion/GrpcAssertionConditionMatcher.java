package io.metersphere.api.grpc.assertion;

import io.metersphere.sdk.constants.MsAssertionCondition;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public final class GrpcAssertionConditionMatcher {

    private GrpcAssertionConditionMatcher() {
    }

    public static boolean match(String condition, String actualValue, String expectedValue) {
        if (StringUtils.isBlank(condition) || StringUtils.equals(condition, MsAssertionCondition.UNCHECK.name())) {
            return true;
        }
        String actual = StringUtils.defaultString(actualValue);
        if (StringUtils.equals(condition, "EXISTS")) {
            return StringUtils.isNotEmpty(actual);
        }
        if (StringUtils.equals(condition, "NOT_EXISTS")) {
            return StringUtils.isEmpty(actual);
        }
        MsAssertionCondition assertionCondition;
        try {
            assertionCondition = MsAssertionCondition.valueOf(condition);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String expected = StringUtils.defaultString(expectedValue);
        return switch (assertionCondition) {
            case CONTAINS -> actual.contains(expected);
            case NOT_CONTAINS -> !actual.contains(expected);
            case EQUALS -> StringUtils.equals(actual, expected);
            case NOT_EQUALS -> !StringUtils.equals(actual, expected);
            case GT -> compareNumber(actual, expected) > 0;
            case GT_OR_EQUALS -> compareNumber(actual, expected) >= 0;
            case LT -> compareNumber(actual, expected) < 0;
            case LT_OR_EQUALS -> compareNumber(actual, expected) <= 0;
            case START_WITH -> actual.startsWith(expected);
            case END_WITH -> actual.endsWith(expected);
            case EMPTY -> StringUtils.isEmpty(actual);
            case NOT_EMPTY -> StringUtils.isNotEmpty(actual);
            case REGEX -> Pattern.compile(expected).matcher(actual).find();
            case LENGTH_EQUALS -> actual.length() == parseInt(expected, -1);
            case LENGTH_NOT_EQUALS -> actual.length() != parseInt(expected, -1);
            case LENGTH_GT -> actual.length() > parseInt(expected, -1);
            case LENGTH_GT_OR_EQUALS -> actual.length() >= parseInt(expected, -1);
            case LENGTH_LT -> actual.length() < parseInt(expected, -1);
            case LENGTH_LT_OR_EQUALS -> actual.length() <= parseInt(expected, -1);
            default -> false;
        };
    }

    private static int compareNumber(String actual, String expected) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(expected));
        } catch (NumberFormatException exception) {
            return actual.compareTo(expected);
        }
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
