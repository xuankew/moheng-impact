package io.moheng.server.repo;

import java.util.List;

public record Project(
        String id,
        String name,
        String repoPath,
        String apiPrefix,
        List<String> includePackages,
        List<String> excludePackages,
        List<String> sourceRoots,
        String defaultBase,
        String createdAt,
        String updatedAt
) {

    public Project {
        apiPrefix = apiPrefix == null ? "" : apiPrefix;
        includePackages = includePackages == null ? List.of() : List.copyOf(includePackages);
        excludePackages = excludePackages == null ? List.of() : List.copyOf(excludePackages);
        sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
    }
}
