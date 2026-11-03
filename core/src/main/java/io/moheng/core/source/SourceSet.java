package io.moheng.core.source;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SourceSet(Map<String, String> files) {

    public SourceSet {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (files != null) {
            files.forEach((path, content) -> {
                if (path != null && content != null) {
                    normalized.put(normalizePath(path), content);
                }
            });
        }
        files = Map.copyOf(normalized);
    }

    public static SourceSet empty() {
        return new SourceSet(Map.of());
    }

    public static SourceSet of(Map<String, String> files) {
        return new SourceSet(files);
    }

    public Set<String> paths() {
        return files.keySet();
    }

    public List<String> sortedPaths() {
        return files.keySet().stream().sorted().toList();
    }

    public String content(String path) {
        return files.get(normalizePath(path));
    }

    public boolean contains(String path) {
        return files.containsKey(normalizePath(path));
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public int size() {
        return files.size();
    }

    public SourceSet subset(Collection<String> onlyPaths) {
        if (onlyPaths == null || onlyPaths.isEmpty()) {
            return this;
        }
        Map<String, String> selected = new LinkedHashMap<>();
        for (String path : onlyPaths) {
            String normalized = normalizePath(path);
            String content = files.get(normalized);
            if (content != null) {
                selected.put(normalized, content);
            }
        }
        return new SourceSet(selected);
    }

    public static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
