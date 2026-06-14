package run.halo.gallery.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared utilities for gallery endpoints to avoid duplication.
 */
public final class EndpointUtils {

    private EndpointUtils() {
    }

    public static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    public static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /**
     * Parse a positive integer from a string, returning defaultValue if null/blank/invalid.
     *
     * @param value the string to parse
     * @param defaultValue fallback when value is null, blank, or non-positive
     * @return the parsed positive integer
     * @throws ResponseStatusException with 400 if value is non-blank but not a valid integer
     */
    public static int positiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(value);
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "page/size 必须是正整数");
        }
    }

    /**
     * Cap a page size to the given maximum.
     */
    public static int capSize(int size, int maxSize) {
        return Math.min(size, maxSize);
    }
}
