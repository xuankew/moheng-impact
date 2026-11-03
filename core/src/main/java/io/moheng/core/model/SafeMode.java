package io.moheng.core.model;

import java.util.List;

public record SafeMode(
        boolean enabled,
        List<RiskReason> triggers,
        String recommendation
) {

    public SafeMode {
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        recommendation = recommendation == null ? "" : recommendation;
    }

    public static SafeMode disabled() {
        return new SafeMode(false, List.of(), "");
    }
}
