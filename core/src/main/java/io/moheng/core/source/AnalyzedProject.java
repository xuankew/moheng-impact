package io.moheng.core.source;

import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.SymbolInfo;
import io.moheng.core.model.Warning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AnalyzedProject(
        Map<String, SymbolInfo> symbols,
        List<ApiInfo> apis,
        ImpactGraph graph,
        Set<String> apiSourcePaths,
        List<Warning> warnings,
        List<String> parseErrors,
        Map<String, Object> stats
) {

    public AnalyzedProject {
        symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
        apis = apis == null ? List.of() : List.copyOf(apis);
        graph = graph == null ? ImpactGraph.empty() : graph;
        apiSourcePaths = apiSourcePaths == null ? Set.of() : Set.copyOf(apiSourcePaths);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        parseErrors = parseErrors == null ? List.of() : List.copyOf(parseErrors);
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }

    public static AnalyzedProject empty() {
        return new AnalyzedProject(Map.of(), List.of(), ImpactGraph.empty(), Set.of(), List.of(), List.of(), Map.of());
    }

    public Map<String, SymbolInfo> symbolsInFile(String file) {
        String normalized = SourceSet.normalizePath(file);
        Map<String, SymbolInfo> result = new LinkedHashMap<>();
        for (SymbolInfo symbol : symbols.values()) {
            if (SourceSet.normalizePath(symbol.file()).equals(normalized)) {
                result.put(symbol.key().id(), symbol);
            }
        }
        return result;
    }

    public SymbolInfo symbol(String key) {
        return symbols.get(key);
    }
}
