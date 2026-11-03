package io.moheng.core.model;

import java.util.List;

public record RiskReport(
        String level,
        int score,
        List<RiskReason> reasons,
        SafeMode safeMode
) {

    public RiskReport {
        level = level == null ? "LOW" : level;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        safeMode = safeMode == null ? SafeMode.disabled() : safeMode;
    }
}
