package io.moheng.core.model;

public record SymbolInfo(
        SymbolKey key,
        String file,
        int line,
        int endLine,
        String modifiers,
        String annotationFingerprint,
        String bodyFingerprint,
        String structuralFingerprint,
        boolean publicDto,
        boolean controllerType,
        boolean controllerHandler,
        boolean interfaceType,
        boolean abstractType
) {

    public SymbolInfo {
        annotationFingerprint = annotationFingerprint == null ? "" : annotationFingerprint;
        bodyFingerprint = bodyFingerprint == null ? "" : bodyFingerprint;
        structuralFingerprint = structuralFingerprint == null ? "" : structuralFingerprint;
    }

    public String effectiveFingerprint() {
        if (!bodyFingerprint.isBlank()) {
            return bodyFingerprint;
        }
        return structuralFingerprint;
    }
}
