package io.moheng.core.model;

public record Edge(
        String from,
        String to,
        Type type,
        double confidence,
        String detail,
        Evidence evidence
) {

    public enum Type { DEFINES, CALLS, REFERENCES, IMPLEMENTS, EXTENDS, OVERRIDES, EXPOSES_API, USES_TYPE }

    public Edge {
        if (confidence < 0) {
            confidence = 0;
        }
        if (confidence > 1) {
            confidence = 1;
        }
        detail = detail == null ? "" : detail;
        evidence = evidence == null ? new Evidence("STATIC", "", 0, "", "FQN", "") : evidence;
    }

    public String id() {
        return from + "|" + type + "|" + to + "|" + detail;
    }
}
