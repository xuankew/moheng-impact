package io.moheng.core.model;

import java.util.Map;

public record Warning(String code, String message, Map<String, Object> details) {

    public Warning {
        code = code == null ? "WARNING" : code;
        message = message == null ? "" : message;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static Warning of(String code, String message) {
        return new Warning(code, message, Map.of());
    }
}
