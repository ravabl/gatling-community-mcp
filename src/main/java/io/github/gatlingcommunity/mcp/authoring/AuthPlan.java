package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record AuthPlan(
        String type,
        String username,
        String password,
        String token,
        String headerName
) {
    public AuthPlan {
        type = type == null ? "" : type;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        token = token == null ? "" : token;
        headerName = headerName == null || headerName.isBlank() ? "Authorization" : headerName;
    }

    public static AuthPlan none() {
        return new AuthPlan("", "", "", "", "Authorization");
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", type);
        values.put("username", username);
        values.put("password", password);
        values.put("token", token);
        values.put("headerName", headerName);
        return values;
    }
}
