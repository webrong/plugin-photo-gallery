package run.halo.gallery.util;

import java.util.Set;

public final class UrlSanitizer {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "data");

    private UrlSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("//")) {
            return "";
        }
        if (lower.startsWith("/")) {
            return trimmed;
        }
        int colon = lower.indexOf(':');
        if (colon <= 0) {
            return trimmed;
        }
        String scheme = lower.substring(0, colon);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return "";
        }
        if ("data".equals(scheme) && !lower.startsWith("data:image/")) {
            return "";
        }
        return trimmed;
    }

    public static boolean isAllowed(String raw) {
        return !sanitize(raw).isEmpty();
    }
}
