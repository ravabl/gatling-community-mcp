package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record MultipartPartPlan(
        String name,
        String value,
        String fileName,
        String contentType,
        String filePath
) {
    public MultipartPartPlan {
        name = name == null ? "" : name;
        value = value == null ? "" : value;
        fileName = fileName == null ? "" : fileName;
        contentType = contentType == null ? "" : contentType;
        filePath = filePath == null ? "" : filePath;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("value", value);
        values.put("fileName", fileName);
        values.put("contentType", contentType);
        values.put("filePath", filePath);
        return values;
    }
}
