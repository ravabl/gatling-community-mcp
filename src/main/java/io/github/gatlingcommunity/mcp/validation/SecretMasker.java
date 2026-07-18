package io.github.gatlingcommunity.mcp.validation;

import java.util.regex.Pattern;

public final class SecretMasker {
    private static final Pattern PASSWORD_ASSIGNMENT =
            Pattern.compile("(?i)(password|secret|apiKey|api_key|credential)\\s*=\\s*\"[^\"]+\"");
    private static final Pattern AUTHORIZATION_BEARER =
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\",}\\]]+");
    private static final Pattern TOKEN_ASSIGNMENT =
            Pattern.compile("(?i)(token|access_token|secret|password)\\s*[:=]\\s*[^\\s\",}\\]]+");

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var masked = PASSWORD_ASSIGNMENT.matcher(value).replaceAll("$1 = \"***\"");
        masked = AUTHORIZATION_BEARER.matcher(masked).replaceAll("$1<redacted>");
        return TOKEN_ASSIGNMENT.matcher(masked).replaceAll("$1=<redacted>");
    }
}
