package io.moheng.core.model;

import java.util.Objects;

public record SymbolKey(Kind kind, String owner, String name, String descriptor) implements Comparable<SymbolKey> {

    public enum Kind { CLASS, METHOD, FIELD }

    public SymbolKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        name = name == null ? "" : name;
        descriptor = descriptor == null ? "" : descriptor;
    }

    public static SymbolKey type(String fqn) {
        return new SymbolKey(Kind.CLASS, fqn, "", "");
    }

    public static SymbolKey method(String owner, String name, String descriptor) {
        return new SymbolKey(Kind.METHOD, owner, name, descriptor);
    }

    public static SymbolKey field(String owner, String name) {
        return new SymbolKey(Kind.FIELD, owner, name, "");
    }

    public String id() {
        return switch (kind) {
            case CLASS -> "CLASS:" + owner;
            case METHOD -> "METHOD:" + owner + "#" + name + descriptor;
            case FIELD -> "FIELD:" + owner + "#" + name;
        };
    }

    @Override
    public int compareTo(SymbolKey other) {
        return id().compareTo(other.id());
    }
}
