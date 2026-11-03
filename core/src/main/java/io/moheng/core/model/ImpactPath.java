package io.moheng.core.model;

import java.util.List;

public record ImpactPath(
        String changedKey,
        String apiId,
        int depth,
        double confidence,
        List<Edge> edges
) {

    public ImpactPath {
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public String apiKey() {
        for (Edge edge : edges) {
            if (edge.type() == Edge.Type.EXPOSES_API) {
                return edge.to();
            }
        }
        return "";
    }
}
