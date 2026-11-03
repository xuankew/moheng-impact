package io.moheng.core.model;

public record SymbolChange(
        SymbolKey key,
        ChangeType changeType,
        String file,
        int line,
        String previousFile,
        boolean publicDto,
        boolean controllerHandler,
        String detail
) {

    public enum ChangeType {
        ADDED,
        REMOVED,
        MODIFIED_BODY,
        SIGNATURE_CHANGED,
        ANNOTATION_CHANGED,
        CONTRACT_CHANGED,
        STRUCTURE_CHANGED
    }

    public SymbolChange {
        detail = detail == null ? "" : detail;
    }

    public boolean isContainerChange() {
        return key.kind() == SymbolKey.Kind.CLASS || key.kind() == SymbolKey.Kind.FIELD;
    }
}
