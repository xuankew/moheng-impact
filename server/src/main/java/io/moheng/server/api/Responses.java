package io.moheng.server.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Responses {

    private Responses() {
    }

    public static Map<String, Object> data(Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", value);
        return body;
    }
}
