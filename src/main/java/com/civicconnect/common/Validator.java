package com.civicconnect.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects field errors and throws one {@link ApiException.Validation} with all of them,
 * so the user sees every problem at once. The server re-validates everything the React
 * form validates: client-side checks are for usability, not security (OWASP ASVS V5).
 */
public final class Validator {

    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    /** South African 10-digit number, e.g. 0821234567 (same rule as the frontend). */
    public static final Pattern SA_PHONE = Pattern.compile("^0\\d{9}$");

    private final Map<String, String> errors = new LinkedHashMap<>();

    public static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public Validator require(boolean condition, String field, String message) {
        if (!condition && !errors.containsKey(field)) errors.put(field, message);
        return this;
    }

    public Validator length(String field, String value, int min, int max, String label) {
        int n = clean(value).length();
        return require(n >= min && n <= max, field,
                min > 0 && n < min ? label + " must be at least " + min + " characters."
                                   : label + " must be at most " + max + " characters.");
    }

    public Validator matches(String field, String value, Pattern pattern, String message) {
        return require(pattern.matcher(clean(value)).matches(), field, message);
    }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) throw new ApiException.Validation(errors);
    }
}
