package fun.crashsystem.jdrpc.util;

import java.net.URI;

/**
 * Shared URL validation helpers for Discord activity fields.
 */
public final class UrlValidator {

    private UrlValidator() {}

    /**
     * Validates an absolute HTTPS URL and optionally enforces a maximum length.
     */
    public static void requireAbsoluteHttpsUrl(String url, String fieldName, int maxLength) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (maxLength > 0 && url.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid absolute HTTPS URL: " + url, e);
        }

        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be a valid absolute HTTPS URL: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(fieldName + " must use https://, got: " + url);
        }
    }
}
