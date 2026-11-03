package io.moheng.core.model;

import java.util.List;
import java.util.Map;

public record AnalysisResult(
        String schemaVersion,
        Metadata analysis,
        Summary summary,
        List<FileChange> changes,
        List<SymbolChange> changedSymbols,
        List<ApiContractChange> apiContractChanges,
        List<ApiInfo> apis,
        List<ApiImpact> apiImpacts,
        List<ImpactedMethod> impactedMethods,
        ImpactGraph graph,
        RiskReport risk,
        List<Warning> warnings,
        Map<String, Object> stats
) {

    public record Metadata(
            String repo,
            String base,
            String head,
            String headMode,
            String startedAt,
            String finishedAt
    ) {
    }

    public record Summary(
            int changedFiles,
            int changedJavaFiles,
            int changedSymbols,
            int impactedMethods,
            int impactedApis,
            String riskLevel,
            int riskScore,
            boolean safeMode
    ) {
    }

    public record ImpactedMethod(
            String key,
            String name,
            String file,
            int line,
            int depth,
            double confidence,
            List<String> impactedApis
    ) {
        public ImpactedMethod {
            impactedApis = impactedApis == null ? List.of() : List.copyOf(impactedApis);
        }
    }

    public AnalysisResult {
        schemaVersion = schemaVersion == null ? "1" : schemaVersion;
        changes = changes == null ? List.of() : List.copyOf(changes);
        changedSymbols = changedSymbols == null ? List.of() : List.copyOf(changedSymbols);
        apiContractChanges = apiContractChanges == null ? List.of() : List.copyOf(apiContractChanges);
        apis = apis == null ? List.of() : List.copyOf(apis);
        apiImpacts = apiImpacts == null ? List.of() : List.copyOf(apiImpacts);
        impactedMethods = impactedMethods == null ? List.of() : List.copyOf(impactedMethods);
        graph = graph == null ? ImpactGraph.empty() : graph;
        risk = risk == null ? new RiskReport("LOW", 0, List.of(), SafeMode.disabled()) : risk;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }

    public static AnalysisResult empty(Metadata metadata) {
        return new AnalysisResult("1", metadata, new Summary(0, 0, 0, 0, 0, "LOW", 0, false),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ImpactGraph.empty(), new RiskReport("LOW", 0, List.of(), SafeMode.disabled()),
                List.of(), Map.of());
    }
}
