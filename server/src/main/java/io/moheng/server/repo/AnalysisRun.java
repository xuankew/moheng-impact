package io.moheng.server.repo;

public record AnalysisRun(
        String id,
        String projectId,
        String baseRev,
        String headRev,
        String headMode,
        String status,
        String phase,
        int progress,
        String riskLevel,
        Integer riskScore,
        boolean safeMode,
        String summaryJson,
        String resultJson,
        String errorCode,
        String errorMessage,
        String warningsJson,
        String startedAt,
        String finishedAt
) {
}
