package io.moheng.core.model;

import java.util.List;
import java.util.Map;

public record RiskReason(
        String code,
        int weight,
        String message,
        Map<String, Object> evidence
) {

    public RiskReason {
        message = message == null ? "" : message;
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
