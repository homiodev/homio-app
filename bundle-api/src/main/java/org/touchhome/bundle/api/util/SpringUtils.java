package org.touchhome.bundle.api.util;

import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringUtils {

    private static final Pattern PATTERN = Pattern.compile("\\$\\{.*?}");
    private static final int VALUE_PREFIX_LENGTH = "${".length();
    private static final int VALUE_SUFFIX_LENGTH = "}".length();

    // Expose useful method to apps
    public static String replaceEnvValues(String notes, BiFunction<String, String, String> propertyGetter) {
        Matcher matcher = PATTERN.matcher(notes);
        StringBuffer noteBuffer = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(noteBuffer, getEnvProperty(group, propertyGetter));
        }
        matcher.appendTail(noteBuffer);
        return noteBuffer.length() == 0 ? notes : noteBuffer.toString();
    }

    public static String getEnvProperty(String value, BiFunction<String, String, String> propertyGetter) {
        String[] array = getSpringValuesPattern(value);
        return propertyGetter.apply(array[0], array[1]);
    }

    public static String[] getSpringValuesPattern(String value) {
        String valuePattern = value.substring(VALUE_PREFIX_LENGTH, value.length() - VALUE_SUFFIX_LENGTH);
        return valuePattern.contains(":") ? valuePattern.split(":") : new String[]{valuePattern, ""};
    }
}
