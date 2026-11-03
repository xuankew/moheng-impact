package io.moheng.core.model;

public record ApiContractChange(
        Type type,
        String apiKey,
        ApiInfo before,
        ApiInfo after,
        String detail
) {

    public enum Type { ADDED, REMOVED, CHANGED }

    public ApiContractChange {
        detail = detail == null ? "" : detail;
    }
}
