package io.moheng.worker;

import io.moheng.analyzer.java.JavaProjectAnalyzer;
import io.moheng.core.AnalysisEngine;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.Edge;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.report.ProgressSink;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AnalysisEngineEndToEndTest {

    private static final String CONTROLLER = """
            package com.example.order;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class OrderController {
                private final OrderService service;

                public OrderController(OrderService service) {
                    this.service = service;
                }

                @GetMapping("/api/orders/{id}")
                public OrderView get(@PathVariable String id) {
                    return service.find(id);
                }
            }
            """;

    private static final String SERVICE_BASE = """
            package com.example.order;

            public class OrderService {
                public OrderView find(String id) {
                    return new OrderView(id);
                }
            }
            """;

    private static final String SERVICE_HEAD = """
            package com.example.order;

            public class OrderService {
                public OrderView find(String id) {
                    String normalized = id == null ? "" : id.trim();
                    return new OrderView(normalized);
                }
            }
            """;

    private static final String VIEW = """
            package com.example.order;

            public class OrderView {
                private final String id;

                public OrderView(String id) {
                    this.id = id;
                }

                public String getId() {
                    return id;
                }
            }
            """;

    @Test
    void analyzesSyntheticRepositoryFromDiffToApiRisk(@TempDir Path repo) throws Exception {
        initRepo(repo);
        AnalysisResult result = new AnalysisEngine(new JavaProjectAnalyzer()).analyze(
                new AnalysisRequest(repo.toString(), "HEAD~1", "HEAD",
                        AnalysisRequest.HeadMode.REV_COMMIT, AnalysisRequest.Options.defaults()),
                ProgressSink.NOOP);

        assertThat(result.summary().changedFiles()).isEqualTo(1);
        assertThat(result.summary().changedSymbols()).isEqualTo(1);
        assertThat(result.changedSymbols()).singleElement()
                .satisfies(change -> {
                    assertThat(change.key().name()).isEqualTo("find");
                    assertThat(change.key().owner()).isEqualTo("com.example.order.OrderService");
                    assertThat(change.changeType()).isEqualTo(
                            io.moheng.core.model.SymbolChange.ChangeType.MODIFIED_BODY);
                });

        assertThat(result.summary().impactedApis()).isEqualTo(1);
        ApiImpact impact = result.apiImpacts().get(0);
        assertThat(impact.api().verb()).isEqualTo("GET");
        assertThat(impact.api().path()).isEqualTo("/api/orders/{id}");
        assertThat(impact.depth()).isEqualTo(2);
        assertThat(impact.level()).isEqualTo("MEDIUM");
        assertThat(impact.score()).isEqualTo(20);
        assertThat(impact.reasons()).extracting(reason -> reason.code())
                .containsExactly("PATH_DEPTH_NEAR", "CORE_API_KEYWORD");

        assertThat(impact.paths()).isNotEmpty();
        ImpactPath path = impact.paths().get(0);
        assertThat(path.depth()).isEqualTo(2);
        assertThat(path.edges()).extracting(Edge::type, Edge::from, Edge::to).containsExactly(
                tuple(Edge.Type.CALLS,
                        "METHOD:com.example.order.OrderController#get(java.lang.String):com.example.order.OrderView",
                        "METHOD:com.example.order.OrderService#find(java.lang.String):com.example.order.OrderView"),
                tuple(Edge.Type.EXPOSES_API,
                        "METHOD:com.example.order.OrderController#get(java.lang.String):com.example.order.OrderView",
                        "API:GET /api/orders/{id}"));
        assertThat(result.summary().riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.risk().safeMode().enabled()).isFalse();
    }

    private static void initRepo(Path repo) throws IOException, GitAPIException {
        write(repo, "src/main/java/com/example/order/OrderController.java", CONTROLLER);
        write(repo, "src/main/java/com/example/order/OrderService.java", SERVICE_BASE);
        write(repo, "src/main/java/com/example/order/OrderView.java", VIEW);
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            commit(git, "base");
            write(repo, "src/main/java/com/example/order/OrderService.java", SERVICE_HEAD);
            commit(git, "head");
        }
    }

    private static void commit(Git git, String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        git.commit()
                .setMessage(message)
                .setAuthor("Moheng Test", "test@example.com")
                .setCommitter("Moheng Test", "test@example.com")
                .call();
    }

    private static void write(Path repo, String path, String content) throws IOException {
        Path target = repo.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
