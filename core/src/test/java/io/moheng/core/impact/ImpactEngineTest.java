package io.moheng.core.impact;

import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.Edge;
import io.moheng.core.model.Evidence;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ImpactEngineTest {

    private static final String RESOLVE = "METHOD:com.example.order.CouponService#resolve(java.lang.String):java.lang.String";
    private static final String FIND = "METHOD:com.example.order.OrderService#find(java.lang.String):com.example.order.dto.OrderView";
    private static final String GET = "METHOD:com.example.order.OrderController#get(java.lang.String):com.example.order.dto.OrderView";
    private static final String API = "API:GET /api/orders/{id}";

    @Test
    void keepsEveryCallEdgeForDeepReversePath() {
        ImpactGraph graph = ImpactGraph.builder()
                .node(method(RESOLVE, "resolve"))
                .node(method(FIND, "find"))
                .node(method(GET, "get"))
                .node(Node.of(API, Node.Kind.API, "GET /api/orders/{id}", "OrderController.java", 22, false))
                .edge(edge(FIND, RESOLVE))
                .edge(edge(GET, FIND))
                .edge(edge(GET, API, Edge.Type.EXPOSES_API))
                .build();
        ApiInfo api = new ApiInfo(ApiInfo.apiId(API), API, "GET", "/api/orders/{id}",
                "com.example.order.OrderController", GET, "com.example.order.OrderController", "get",
                "OrderController.java", 22, List.of("/api/orders"), List.of("/{id}"),
                Map.of(), "FQN", false);

        ImpactEngine.Result result = new ImpactEngine()
                .analyze(graph, Set.of(RESOLVE), Map.of(API, api), 5, 0.0, 10);

        assertThat(result.paths()).hasSize(1);
        ImpactPath path = result.paths().get(0);
        assertThat(path.depth()).isEqualTo(3);
        assertThat(path.edges()).extracting(Edge::from, Edge::to).containsExactly(
                tuple(FIND, RESOLVE),
                tuple(GET, FIND),
                tuple(GET, API));
    }

    private static Node method(String key, String name) {
        return Node.of(key, Node.Kind.METHOD, name, "Order.java", 1, false);
    }

    private static Edge edge(String from, String to) {
        return edge(from, to, Edge.Type.CALLS);
    }

    private static Edge edge(String from, String to, Edge.Type type) {
        return new Edge(from, to, type, 1.0, "direct",
                new Evidence("SPOON", "Order.java", 1, "", "FQN", ""));
    }
}
