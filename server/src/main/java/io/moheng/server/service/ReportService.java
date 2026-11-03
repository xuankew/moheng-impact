package io.moheng.server.service;

import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.Edge;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.Node;
import io.moheng.server.api.ApiException;
import io.moheng.server.repo.AnalysisRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportService {

    private static final int MAX_GRAPH_NODES = 200;

    public AnalysisResult result(AnalysisRun run) {
        if (run.resultJson() == null || run.resultJson().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS",
                    "分析尚未完成，当前状态: " + run.status());
        }
        return Json.read(run.resultJson(), AnalysisResult.class);
    }

    public Map<String, Object> status(AnalysisRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", run.id());
        data.put("projectId", run.projectId());
        data.put("base", run.baseRev());
        data.put("head", run.headRev());
        data.put("headMode", run.headMode());
        data.put("status", run.status());
        data.put("phase", run.phase());
        data.put("progress", run.progress());
        data.put("riskLevel", run.riskLevel());
        data.put("riskScore", run.riskScore());
        data.put("safeMode", run.safeMode());
        data.put("summary", readJson(run.summaryJson()));
        data.put("startedAt", run.startedAt());
        data.put("finishedAt", run.finishedAt());
        if (run.errorCode() != null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", run.errorCode());
            error.put("message", run.errorMessage() == null ? "" : run.errorMessage());
            data.put("error", error);
        }
        return data;
    }

    public Map<String, Object> apiDetail(AnalysisRun run, String apiId) {
        AnalysisResult result = result(run);
        for (ApiImpact impact : result.apiImpacts()) {
            if (impact.api().id().equals(apiId) || impact.api().key().equals(apiId)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("api", impact.api());
                data.put("level", impact.level());
                data.put("score", impact.score());
                data.put("confidence", impact.confidence());
                data.put("depth", impact.depth());
                data.put("reasons", impact.reasons());
                data.put("paths", impact.paths());
                data.put("risk", result.risk());
                return data;
            }
        }
        throw ApiException.notFound("未找到 API: " + apiId);
    }

    public Map<String, Object> changes(AnalysisRun run) {
        AnalysisResult result = result(run);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files", result.changes());
        data.put("symbols", result.changedSymbols());
        data.put("contractChanges", result.apiContractChanges());
        data.put("warnings", result.warnings());
        return data;
    }

    public Map<String, Object> graph(AnalysisRun run, String apiKey, boolean changedOnly, int maxDepth) {
        AnalysisResult result = result(run);
        List<ImpactPath> paths = new ArrayList<>();
        for (ApiImpact impact : result.apiImpacts()) {
            if (apiKey != null && !apiKey.isBlank() && !impact.api().key().equals(apiKey)) {
                continue;
            }
            for (ImpactPath path : impact.paths()) {
                if (maxDepth > 0 && path.depth() > maxDepth) {
                    continue;
                }
                if (changedOnly) {
                    Node changed = result.graph().node(path.changedKey());
                    if (changed == null || !changed.changed()) {
                        continue;
                    }
                }
                paths.add(path);
            }
        }
        Set<String> edgeIds = new LinkedHashSet<>();
        List<Edge> edges = new ArrayList<>();
        boolean truncated = false;
        for (ImpactPath path : paths) {
            for (Edge edge : path.edges()) {
                if (!edgeIds.add(edge.id())) {
                    continue;
                }
                edges.add(edge);
            }
        }
        Set<String> nodeKeys = new LinkedHashSet<>();
        for (Edge edge : edges) {
            if (result.graph().node(edge.from()) != null) {
                nodeKeys.add(edge.from());
            }
            if (result.graph().node(edge.to()) != null) {
                nodeKeys.add(edge.to());
            }
        }
        for (ImpactPath path : paths) {
            if (result.graph().node(path.changedKey()) != null) {
                nodeKeys.add(path.changedKey());
            }
        }
        List<Node> nodes = new ArrayList<>();
        boolean skipped = false;
        for (String key : nodeKeys) {
            if (nodes.size() >= MAX_GRAPH_NODES) {
                truncated = true;
                skipped = true;
                break;
            }
            nodes.add(result.graph().node(key));
        }
        if (skipped) {
            Set<String> kept = nodes.stream().map(Node::key).collect(java.util.stream.Collectors.toSet());
            edges.removeIf(edge -> !kept.contains(edge.from()) || !kept.contains(edge.to()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", nodes);
        data.put("edges", edges);
        data.put("truncated", truncated);
        data.put("maxDepth", maxDepth);
        return data;
    }

    private static Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return Json.mapper().readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
