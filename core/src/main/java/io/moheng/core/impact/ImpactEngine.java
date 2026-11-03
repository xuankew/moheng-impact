package io.moheng.core.impact;

import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.Edge;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

public final class ImpactEngine {

    private static final double EPSILON = 1e-9;
    private static final int MAX_GRAPH_NODES = 5_000;

    public record MethodImpact(String key, int depth, double confidence, Set<String> apiIds) {
        public MethodImpact {
            apiIds = apiIds == null ? Set.of() : Set.copyOf(apiIds);
        }
    }

    public record Result(
            List<ImpactPath> paths,
            List<MethodImpact> methods,
            ImpactGraph subgraph,
            Map<String, Object> stats
    ) {
        public Result {
            paths = paths == null ? List.of() : List.copyOf(paths);
            methods = methods == null ? List.of() : List.copyOf(methods);
            subgraph = subgraph == null ? ImpactGraph.empty() : subgraph;
            stats = stats == null ? Map.of() : Map.copyOf(stats);
        }
    }

    public Result analyze(ImpactGraph graph, Set<String> seedKeys,
                          Map<String, ApiInfo> apiByNodeKey,
                          int maxDepth, double minConfidence, int maxPathsPerApi) {
        Map<String, Best> globalMethods = new HashMap<>();
        Map<String, Set<String>> methodApis = new HashMap<>();
        List<ImpactPath> allPaths = new ArrayList<>();
        Map<String, String> apiIdByNodeKey = new HashMap<>();
        apiByNodeKey.forEach((key, api) -> apiIdByNodeKey.put(key, api.id()));
        int unmatchedSeeds = 0;

        for (String seed : seedKeys) {
            if (graph.node(seed) == null) {
                unmatchedSeeds++;
                continue;
            }
            allPaths.addAll(traverseSeed(graph, seed, maxDepth, minConfidence,
                    apiIdByNodeKey, globalMethods));
        }

        Map<String, List<ImpactPath>> byApi = new LinkedHashMap<>();
        for (ImpactPath path : allPaths) {
            byApi.computeIfAbsent(path.apiId(), key -> new ArrayList<>()).add(path);
        }
        Comparator<ImpactPath> comparator = Comparator
                .comparingDouble(ImpactPath::confidence).reversed()
                .thenComparingInt(ImpactPath::depth)
                .thenComparing(ImpactPath::changedKey);
        List<ImpactPath> selected = new ArrayList<>();
        int truncated = 0;
        for (List<ImpactPath> paths : byApi.values()) {
            paths.sort(comparator);
            int keep = Math.min(paths.size(), Math.max(1, maxPathsPerApi));
            selected.addAll(paths.subList(0, keep));
            truncated += paths.size() - keep;
        }
        selected.sort(Comparator.comparing(ImpactPath::apiId)
                .thenComparingInt(ImpactPath::depth)
                .thenComparing(ImpactPath::changedKey));

        for (ImpactPath path : selected) {
            for (Edge edge : path.edges()) {
                if (isMethod(edge.from())) {
                    methodApis.computeIfAbsent(edge.from(), key -> new TreeSet<>()).add(path.apiId());
                }
                if (isMethod(edge.to())) {
                    methodApis.computeIfAbsent(edge.to(), key -> new TreeSet<>()).add(path.apiId());
                }
            }
        }

        List<MethodImpact> methods = new ArrayList<>();
        for (Map.Entry<String, Best> entry : globalMethods.entrySet()) {
            if (!isMethod(entry.getKey())) {
                continue;
            }
            methods.add(new MethodImpact(entry.getKey(), entry.getValue().depth,
                    entry.getValue().confidence,
                    methodApis.getOrDefault(entry.getKey(), Set.of())));
        }
        methods.sort(Comparator.comparingInt(MethodImpact::depth)
                .thenComparing(Comparator.comparingDouble(MethodImpact::confidence).reversed())
                .thenComparing(MethodImpact::key));

        ImpactGraph subgraph = buildSubgraph(graph, selected, seedKeys);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("seeds", seedKeys.size());
        stats.put("unmatchedSeeds", unmatchedSeeds);
        stats.put("paths", selected.size());
        stats.put("truncatedPaths", truncated);
        stats.put("impactedMethods", methods.size());
        stats.put("impactedApis", byApi.size());
        stats.put("graphNodes", subgraph.nodes().size());
        stats.put("graphEdges", subgraph.edges().size());
        stats.put("maxDepth", maxDepth);
        stats.put("minConfidence", minConfidence);
        return new Result(selected, methods, subgraph, stats);
    }

    private List<ImpactPath> traverseSeed(ImpactGraph graph, String seed, int maxDepth,
                                          double minConfidence, Map<String, String> apiIdByNodeKey,
                                          Map<String, Best> globalMethods) {
        Map<String, Best> best = new HashMap<>();
        PriorityQueue<State> queue = new PriorityQueue<>(
                Comparator.comparingDouble(State::confidence).reversed()
                        .thenComparingInt(State::depth));
        best.put(seed, new Best(1.0, 0, null));
        queue.add(new State(seed, 1.0, 0));
        List<ImpactPath> paths = new ArrayList<>();
        Set<String> apiSeen = new HashSet<>();

        while (!queue.isEmpty()) {
            State state = queue.poll();
            Best current = best.get(state.node());
            if (current == null || Math.abs(current.confidence - state.confidence()) > EPSILON
                    || current.depth != state.depth()) {
                continue;
            }
            Node node = graph.node(state.node());
            if (node == null) {
                continue;
            }
            if (node.kind() == Node.Kind.API) {
                String apiId = apiIdByNodeKey.get(state.node());
                if (apiId != null && apiSeen.add(apiId)) {
                    paths.add(new ImpactPath(seed, apiId, state.depth(),
                            state.confidence(), reconstruct(seed, state.node(), best)));
                }
                continue;
            }
            updateGlobal(globalMethods, state);
            if (state.depth() >= maxDepth) {
                continue;
            }
            for (Edge edge : graph.outgoing(state.node())) {
                if (edge.type() != Edge.Type.EXPOSES_API) {
                    continue;
                }
                double confidence = Math.min(state.confidence(), edge.confidence());
                int depth = state.depth() + 1;
                if (depth > maxDepth) {
                    continue;
                }
                if (relax(best, edge.to(), confidence, depth, edge)) {
                    queue.add(new State(edge.to(), confidence, depth));
                }
            }
            for (Edge edge : graph.incoming(state.node())) {
                if (!traversable(edge) || edge.confidence() < minConfidence) {
                    continue;
                }
                double confidence = Math.min(state.confidence(), edge.confidence());
                int depth = state.depth() + 1;
                if (depth > maxDepth) {
                    continue;
                }
                if (relax(best, edge.from(), confidence, depth, edge)) {
                    queue.add(new State(edge.from(), confidence, depth));
                }
            }
        }
        return paths;
    }

    private boolean relax(Map<String, Best> best, String node, double confidence, int depth, Edge edge) {
        Best existing = best.get(node);
        if (existing == null || confidence > existing.confidence + EPSILON
                || (Math.abs(confidence - existing.confidence) <= EPSILON
                && depth < existing.depth)) {
            best.put(node, new Best(confidence, depth, edge));
            return true;
        }
        return false;
    }

    private void updateGlobal(Map<String, Best> global, State state) {
        Best existing = global.get(state.node());
        if (existing == null || state.confidence() > existing.confidence + EPSILON
                || (Math.abs(state.confidence() - existing.confidence) <= EPSILON
                && state.depth() < existing.depth)) {
            global.put(state.node(), new Best(state.confidence(), state.depth(), null));
        }
    }

    private List<Edge> reconstruct(String seed, String apiNode, Map<String, Best> best) {
        Deque<Edge> reversed = new ArrayDeque<>();
        String cursor = apiNode;
        Set<String> seen = new HashSet<>();
        while (!cursor.equals(seed) && seen.add(cursor)) {
            Best current = best.get(cursor);
            if (current == null || current.incoming == null) {
                break;
            }
            reversed.addFirst(current.incoming);
            Edge edge = current.incoming;
            cursor = edge.to().equals(cursor) ? edge.from() : edge.to();
        }
        return new ArrayList<>(reversed);
    }

    private boolean traversable(Edge edge) {
        return edge.type() == Edge.Type.CALLS
                || edge.type() == Edge.Type.REFERENCES
                || edge.type() == Edge.Type.OVERRIDES
                || edge.type() == Edge.Type.IMPLEMENTS
                || edge.type() == Edge.Type.EXTENDS
                || edge.type() == Edge.Type.EXPOSES_API
                || edge.type() == Edge.Type.USES_TYPE;
    }

    private ImpactGraph buildSubgraph(ImpactGraph graph, List<ImpactPath> paths, Set<String> seeds) {
        ImpactGraph.Builder builder = ImpactGraph.builder();
        Set<String> nodes = new LinkedHashSet<>();
        for (ImpactPath path : paths) {
            for (Edge edge : path.edges()) {
                addNode(builder, graph, nodes, edge.from());
                addNode(builder, graph, nodes, edge.to());
                if (nodes.size() <= MAX_GRAPH_NODES) {
                    builder.edge(edge);
                }
            }
        }
        for (String seed : seeds) {
            addNode(builder, graph, nodes, seed);
        }
        return builder.build();
    }

    private void addNode(ImpactGraph.Builder builder, ImpactGraph graph, Set<String> nodes, String key) {
        if (nodes.size() >= MAX_GRAPH_NODES || nodes.contains(key)) {
            return;
        }
        Node node = graph.node(key);
        if (node != null) {
            nodes.add(key);
            builder.node(node);
        }
    }

    private boolean isMethod(String key) {
        return key != null && key.startsWith("METHOD:");
    }

    private record State(String node, double confidence, int depth) {
    }

    private static final class Best {
        private final double confidence;
        private final int depth;
        private final Edge incoming;

        private Best(double confidence, int depth, Edge incoming) {
            this.confidence = confidence;
            this.depth = depth;
            this.incoming = incoming;
        }
    }
}
