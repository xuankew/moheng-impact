package io.moheng.core;

import io.moheng.core.change.ApiDiff;
import io.moheng.core.change.ChangeDetector;
import io.moheng.core.git.GitRepository;
import io.moheng.core.impact.ImpactEngine;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiContractChange;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.FileChange;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.Node;
import io.moheng.core.model.RiskReport;
import io.moheng.core.model.SafeMode;
import io.moheng.core.model.SymbolChange;
import io.moheng.core.model.SymbolKey;
import io.moheng.core.model.Warning;
import io.moheng.core.report.ProgressSink;
import io.moheng.core.risk.RiskEngine;
import io.moheng.core.risk.SafeModeDetector;
import io.moheng.core.source.AnalyzedProject;
import io.moheng.core.source.ProjectAnalyzer;
import io.moheng.core.source.SourceSet;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnalysisEngine {

    private static final String SCHEMA_VERSION = "1";

    private final ProjectAnalyzer analyzer;

    public AnalysisEngine(ProjectAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public AnalysisResult analyze(AnalysisRequest request, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.NOOP : progress;
        String startedAt = Instant.now().toString();
        long start = System.currentTimeMillis();
        Path repoPath = Path.of(request.repoPath()).toAbsolutePath().normalize();

        try (GitRepository git = GitRepository.open(repoPath)) {
            AnalysisRequest.HeadMode headMode = git.resolveHeadMode(request);
            String baseId = git.resolveCommit(request.base());
            String headLabel = headMode == AnalysisRequest.HeadMode.WORKTREE
                    ? "WORKTREE" : git.resolveCommit(request.head());

            sink.progress("DIFF", 3, "计算 Git 变更");
            long diffStart = System.currentTimeMillis();
            List<FileChange> changes = git.diff(baseId, request.head(), headMode, request.options());
            long diffMillis = System.currentTimeMillis() - diffStart;

            List<SymbolChange> changedSymbols = List.of();
            List<ApiContractChange> contractChanges = List.of();
            List<ApiInfo> apis = List.of();
            ImpactEngine.Result impact = new ImpactEngine.Result(
                    List.of(), List.of(), ImpactGraph.empty(), Map.of());
            SafeMode safeMode = SafeMode.disabled();
            List<Warning> warnings = new ArrayList<>();
            Map<String, Object> stats = new LinkedHashMap<>();
            AnalyzedProject headProject = AnalyzedProject.empty();
            RiskEngine.Result risk;

            boolean hasJavaChanges = changes.stream().anyMatch(FileChange::javaFile);
            if (hasJavaChanges) {
                sink.progress("BASE_READ", 8, "读取 base 源码");
                SourceSet baseSources = git.readAllSources(baseId,
                        AnalysisRequest.HeadMode.REV_COMMIT, request.options());

                sink.progress("BASE_PARSE", 12, "解析 base 源码");
                AnalyzedProject baseProject;
                try {
                    baseProject = analyzer.analyze(baseSources, request.options(),
                            ProjectAnalyzer.Mode.BASE, sink);
                } catch (RuntimeException e) {
                    baseProject = AnalyzedProject.empty();
                    warnings.add(new Warning("BASE_ANALYSIS_FAILED",
                            "base 源码分析失败，契约对比能力降级", Map.of("error", e.getClass().getSimpleName())));
                }
                warnings.addAll(baseProject.warnings());

                sink.progress("HEAD_READ", 30, "读取 head 源码");
                SourceSet headSources = git.readAllSources(headLabel, headMode, request.options());
                sink.progress("HEAD_PARSE", 35, "解析 head 源码");
                headProject = analyzer.analyze(headSources, request.options(),
                        ProjectAnalyzer.Mode.HEAD, sink);
                warnings.addAll(headProject.warnings());

                sink.progress("CHANGE", 65, "比对变更符号");
                changedSymbols = new ChangeDetector().detect(baseProject, headProject, changes);
                contractChanges = new ApiDiff().diff(baseProject.apis(), headProject.apis());
                apis = headProject.apis();

                sink.progress("IMPACT", 75, "反向计算 API 影响");
                Set<String> seeds = seeds(changedSymbols, headProject.graph());
                Map<String, ApiInfo> apiByKey = new LinkedHashMap<>();
                for (ApiInfo api : headProject.apis()) {
                    apiByKey.put(api.key(), api);
                }
                impact = new ImpactEngine().analyze(headProject.graph(), seeds, apiByKey,
                        request.options().maxDepth(),
                        request.options().highConfidenceOnly() ? 0.8 : 0.0,
                        10);
                impact = markChanged(impact, seeds);

                sink.progress("RISK", 88, "计算风险与 Safe Mode");
                safeMode = new SafeModeDetector().detect(changes, changedSymbols, headProject);
                risk = new RiskEngine().evaluate(headProject, changedSymbols, contractChanges,
                        impact, safeMode);

                stats.put("base", baseProject.stats());
                stats.put("head", headProject.stats());
                stats.put("parseErrors", Map.of(
                        "base", baseProject.parseErrors().size(),
                        "head", headProject.parseErrors().size()));
            } else {
                sink.progress("NO_JAVA", 80, "没有 Java 源码变更");
                warnings.add(Warning.of("NO_JAVA_CHANGES", "本次变更不包含 Java 源码，未执行符号与 API 影响分析"));
                safeMode = new SafeModeDetector().detect(changes, List.of(), AnalyzedProject.empty());
                risk = new RiskEngine().evaluate(AnalyzedProject.empty(), List.of(), List.of(),
                        impact, safeMode);
            }

            stats.put("diffMillis", diffMillis);
            stats.put("totalMillis", System.currentTimeMillis() - start);
            stats.put("impact", impact.stats());
            stats.put("changedFiles", changes.size());
            stats.put("changedJavaFiles", changes.stream().filter(FileChange::javaFile).count());

            List<AnalysisResult.ImpactedMethod> impactedMethods = toImpactedMethods(impact, headProject);
            AnalysisResult.Summary summary = new AnalysisResult.Summary(
                    changes.size(),
                    (int) changes.stream().filter(FileChange::javaFile).count(),
                    changedSymbols.size(),
                    impactedMethods.size(),
                    risk.apiImpacts().size(),
                    risk.report().level(),
                    risk.report().score(),
                    safeMode.enabled());

            AnalysisResult.Metadata metadata = new AnalysisResult.Metadata(
                    git.workTree().toString(), request.base(), headLabel, headMode.name(),
                    startedAt, Instant.now().toString());

            sink.progress("DONE", 100, "分析完成");
            return new AnalysisResult(SCHEMA_VERSION, metadata, summary, changes, changedSymbols,
                    contractChanges, apis, risk.apiImpacts(), impactedMethods, impact.subgraph(),
                    risk.report(), dedupeWarnings(warnings), stats);
        }
    }

    private ImpactEngine.Result markChanged(ImpactEngine.Result impact, Set<String> seeds) {
        ImpactGraph graph = impact.subgraph();
        Map<String, Node> nodes = new LinkedHashMap<>();
        boolean changed = false;
        for (Node node : graph.nodes().values()) {
            if (seeds.contains(node.key()) && !node.changed()) {
                node = new Node(node.key(), node.kind(), node.name(), node.file(), node.line(),
                        true, node.meta());
                changed = true;
            }
            nodes.put(node.key(), node);
        }
        if (!changed) {
            return impact;
        }
        return new ImpactEngine.Result(impact.paths(), impact.methods(),
                new ImpactGraph(nodes, graph.edges()), impact.stats());
    }

    private List<AnalysisResult.ImpactedMethod> toImpactedMethods(ImpactEngine.Result impact,
                                                                  AnalyzedProject head) {
        List<AnalysisResult.ImpactedMethod> methods = new ArrayList<>();
        for (ImpactEngine.MethodImpact method : impact.methods()) {
            Node node = head.graph().node(method.key());
            if (node == null) {
                continue;
            }
            methods.add(new AnalysisResult.ImpactedMethod(method.key(), node.name(),
                    node.file(), node.line(), method.depth(), method.confidence(),
                    new ArrayList<>(method.apiIds())));
        }
        methods.sort(Comparator.comparingInt(AnalysisResult.ImpactedMethod::depth)
                .thenComparing(AnalysisResult.ImpactedMethod::key));
        return methods;
    }

    private Set<String> seeds(List<SymbolChange> changes, ImpactGraph graph) {
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (Node node : graph.nodes().values()) {
            if (node.kind() != Node.Kind.METHOD && node.kind() != Node.Kind.FIELD) {
                continue;
            }
            String owner = owner(node.key());
            if (!owner.isEmpty()) {
                members.computeIfAbsent(owner, key -> new ArrayList<>()).add(node.key());
            }
        }
        Set<String> seeds = new LinkedHashSet<>();
        for (SymbolChange change : changes) {
            seeds.add(change.key().id());
            if (change.key().kind() == SymbolKey.Kind.CLASS) {
                switch (change.changeType()) {
                    case ANNOTATION_CHANGED, STRUCTURE_CHANGED, CONTRACT_CHANGED ->
                            seeds.addAll(members.getOrDefault(change.key().owner(), List.of()));
                    default -> {
                    }
                }
            }
            if (change.key().kind() == SymbolKey.Kind.FIELD && change.publicDto()
                    && isContractLike(change.changeType())) {
                seeds.add(SymbolKey.type(change.key().owner()).id());
            }
        }
        return seeds;
    }

    private boolean isContractLike(SymbolChange.ChangeType type) {
        return type == SymbolChange.ChangeType.ADDED
                || type == SymbolChange.ChangeType.REMOVED
                || type == SymbolChange.ChangeType.STRUCTURE_CHANGED
                || type == SymbolChange.ChangeType.CONTRACT_CHANGED;
    }

    private String owner(String key) {
        int hash = key.indexOf('#');
        if (hash < 0) {
            return "";
        }
        int colon = key.indexOf(':');
        return colon < 0 ? "" : key.substring(colon + 1, hash);
    }

    private List<Warning> dedupeWarnings(List<Warning> warnings) {
        Map<String, Warning> unique = new LinkedHashMap<>();
        for (Warning warning : warnings) {
            unique.putIfAbsent(warning.code() + "|" + warning.message(), warning);
        }
        return new ArrayList<>(unique.values());
    }
}
