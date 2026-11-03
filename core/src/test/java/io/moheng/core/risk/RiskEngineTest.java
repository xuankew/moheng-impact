package io.moheng.core.risk;

import io.moheng.core.impact.ImpactEngine;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.SafeMode;
import io.moheng.core.model.SymbolChange;
import io.moheng.core.model.SymbolKey;
import io.moheng.core.source.AnalyzedProject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RiskEngineTest {

    private static final String API_KEY = ApiInfo.apiKey("GET", "/items/{id}");
    private static final String API_ID = ApiInfo.apiId(API_KEY);
    private static final String HANDLER = SymbolKey.method(
            "com.example.ItemController", "get", "(java.lang.String):com.example.ItemView").id();
    private static final String SEED = SymbolKey.method(
            "com.example.ItemService", "find", "(java.lang.String):com.example.ItemView").id();

    @Test
    void keepsAtMostTenPathsPerApiSortedByConfidenceThenDepth() {
        List<ImpactPath> paths = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            paths.add(new ImpactPath(SEED, API_ID, i % 4, 0.5 + i * 0.03, List.of()));
        }

        RiskEngine.Result result = evaluate(paths);

        assertThat(result.apiImpacts()).hasSize(1);
        List<ImpactPath> kept = result.apiImpacts().get(0).paths();
        assertThat(kept).hasSize(10);
        assertThat(kept).isSortedAccordingTo((left, right) -> {
            int byConfidence = Double.compare(right.confidence(), left.confidence());
            return byConfidence != 0 ? byConfidence : Integer.compare(left.depth(), right.depth());
        });
        assertThat(kept.get(0).confidence()).isCloseTo(0.83, within(1e-9));
        assertThat(kept.get(9).confidence()).isCloseTo(0.56, within(1e-9));
    }

    @Test
    void scoresUsingTheBestPath() {
        List<ImpactPath> paths = List.of(
                new ImpactPath(SEED, API_ID, 4, 0.9, List.of()),
                new ImpactPath(SEED, API_ID, 2, 0.95, List.of()));

        RiskEngine.Result result = evaluate(paths);

        ApiImpact impact = result.apiImpacts().get(0);
        assertThat(impact.depth()).isEqualTo(2);
        assertThat(impact.confidence()).isEqualTo(0.95);
        assertThat(impact.score()).isEqualTo(10);
        assertThat(impact.reasons()).extracting(RiskReason::code)
                .containsExactly("PATH_DEPTH_NEAR");
        assertThat(impact.paths()).hasSize(2);
    }

    @Test
    void addsLowConfidencePenaltyWhenBestPathIsWeak() {
        RiskEngine.Result result = evaluate(List.of(
                new ImpactPath(SEED, API_ID, 1, 0.7, List.of())));

        ApiImpact impact = result.apiImpacts().get(0);
        assertThat(impact.score()).isEqualTo(15);
        assertThat(impact.reasons()).extracting(RiskReason::code)
                .containsExactly("PATH_DEPTH_NEAR", "LOW_CONFIDENCE_PATH");
    }

    private static RiskEngine.Result evaluate(List<ImpactPath> paths) {
        AnalyzedProject project = new AnalyzedProject(
                Map.of(), List.of(api()), ImpactGraph.empty(), Set.of(), List.of(), List.of(), Map.of());
        SymbolChange seedChange = new SymbolChange(
                SymbolKey.method("com.example.ItemService", "find",
                        "(java.lang.String):com.example.ItemView"),
                SymbolChange.ChangeType.MODIFIED_BODY, "ItemService.java", 12, null, false, false, "");
        ImpactEngine.Result impact = new ImpactEngine.Result(
                paths, List.of(), ImpactGraph.empty(), Map.of());
        return new RiskEngine().evaluate(
                project, List.of(seedChange), List.of(), impact, SafeMode.disabled());
    }

    private static ApiInfo api() {
        return new ApiInfo(API_ID, API_KEY, "GET", "/items/{id}",
                "com.example.ItemController", HANDLER,
                "com.example.ItemController", "get",
                "ItemController.java", 20, List.of("/items"), List.of("/{id}"),
                Map.of(), "FQN", false);
    }
}
