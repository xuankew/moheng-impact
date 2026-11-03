package io.moheng.core.model;

import java.util.List;

public record ApiImpact(
        ApiInfo api,
        String level,
        int score,
        double confidence,
        int depth,
        List<RiskReason> reasons,
        List<ImpactPath> paths
) {

    public ApiImpact {
        level = level == null ? "LOW" : level;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        paths = paths == null ? List.of() : List.copyOf(paths);
    }
}
