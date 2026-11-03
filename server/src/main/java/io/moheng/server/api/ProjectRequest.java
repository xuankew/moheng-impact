package io.moheng.server.api;

import java.util.List;

public record ProjectRequest(
        String name,
        String repoPath,
        String apiPrefix,
        List<String> includePackages,
        List<String> excludePackages,
        List<String> sourceRoots,
        String defaultBase
) {
}
