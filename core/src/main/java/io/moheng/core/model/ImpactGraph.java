package io.moheng.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImpactGraph(Map<String, Node> nodes, List<Edge> edges) {

    public ImpactGraph {
        nodes = Map.copyOf(nodes == null ? Map.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
    }

    public static ImpactGraph empty() {
        return new ImpactGraph(Map.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Node node(String key) {
        return nodes.get(key);
    }

    public List<Edge> outgoing(String key) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.from().equals(key)) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<Edge> incoming(String key) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.to().equals(key)) {
                result.add(edge);
            }
        }
        return result;
    }

    public Map<String, Integer> inDegree() {
        Map<String, Integer> degree = new LinkedHashMap<>();
        for (Edge edge : edges) {
            if (edge.type() == Edge.Type.CALLS || edge.type() == Edge.Type.REFERENCES
                    || edge.type() == Edge.Type.OVERRIDES) {
                degree.merge(edge.to(), 1, Integer::sum);
            }
        }
        return degree;
    }

    public static final class Builder {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private final Set<String> edgeIds = new LinkedHashSet<>();

        public Builder node(Node node) {
            nodes.putIfAbsent(node.key(), node);
            return this;
        }

        public Builder edge(Edge edge) {
            if (edgeIds.add(edge.id())) {
                edges.add(edge);
            }
            return this;
        }

        public Builder edges(List<Edge> edges) {
            if (edges != null) {
                edges.forEach(this::edge);
            }
            return this;
        }

        public Map<String, Node> nodes() {
            return nodes;
        }

        public List<Edge> edges() {
            return edges;
        }

        public ImpactGraph build() {
            return new ImpactGraph(nodes, edges);
        }
    }
}
