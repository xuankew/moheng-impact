package io.moheng.server.api;

import java.util.List;

public record AnalysisCreateRequest(
        String base,
        String head,
        String headMode,
        Integer maxDepth,
        String apiPrefix,
        List<String> includePackages,
        List<String> excludePackages,
        List<String> sourceRoots,
        Boolean includeTests,
        Boolean highConfidenceOnly,
        Integer javaVersion
) {
}
