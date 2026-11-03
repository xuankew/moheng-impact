package io.moheng.core.model;

import java.util.Map;

public record Node(
        String key,
        Kind kind,
        String name,
        String file,
        int line,
        boolean changed,
        Map<String, Object> meta
) {

    public enum Kind { FILE, CLASS, METHOD, FIELD, API }

    public Node {
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public static Node of(String key, Kind kind, String name, String file, int line, boolean changed) {
        return new Node(key, kind, name, file, line, changed, Map.of());
    }
}
