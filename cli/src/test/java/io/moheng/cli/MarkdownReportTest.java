package io.moheng.cli;

import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.ApiInfo;
import io.moheng.core.model.FileChange;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.RiskReport;
import io.moheng.core.model.SafeMode;
import io.moheng.core.model.Warning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReportTest {

    private static final AnalysisResult.Metadata METADATA = new AnalysisResult.Metadata(
            "/workspace/repo-x",
            "1111111111111111111111111111111111111111",
            "2222222222222222222222222222222222222222",
            "REV_COMMIT", "2026-11-03T10:00:00Z", "2026-11-03T10:00:05Z");

    @Test
    void rendersMarkerRiskAndEscapesTableCells() {
        List<FileChange> changes = List.of(
                new FileChange("src/a|b.java", null, FileChange.Status.MODIFIED, List.of(), false, true),
                new FileChange("src/new\nline.java", null, FileChange.Status.ADDED, List.of(), false, true),
                new FileChange("src/renamed.java", "src/old.java", FileChange.Status.RENAMED, List.of(), false, true));

        AnalysisResult result = new AnalysisResult("1", METADATA,
                new AnalysisResult.Summary(3, 3, 2, 5, 1, "HIGH", 45, true),
                changes, List.of(), List.of(), List.of(),
                List.of(impact("GET", "/orders/{id}", 30, "HIGH")),
                List.of(), ImpactGraph.empty(),
                new RiskReport("HIGH", 45, List.of(
                        new RiskReason("PUBLIC_API", 20, "命中公开 API", Map.of()),
                        new RiskReason("PUBLIC_API", 20, "命中公开 API", Map.of())),
                        new SafeMode(true, List.of(), "建议人工复核")),
                List.of(Warning.of("NO_JAVA_CHANGES", "没有 Java 变更")), Map.of());

        String markdown = MarkdownReport.render(result);

        assertThat(markdown).startsWith(MarkdownReport.MARKER);
        assertThat(markdown).contains("**风险：HIGH / 45** · Safe Mode 已启用");
        assertThat(markdown).contains("`11111111` → `22222222`");
        assertThat(markdown).contains("`repo-x`");
        assertThat(markdown).contains("`src/a\\|b.java`");
        assertThat(markdown).contains("`src/new line.java`");
        assertThat(markdown).contains("src/renamed.java（来自 src/old.java）");
        assertThat(markdown).contains("| `GET /orders/{id}` | HIGH | 30 | 2 | 90% |");
        assertThat(count(markdown, "命中公开 API")).isEqualTo(1);
        assertThat(markdown).contains("`NO_JAVA_CHANGES` 没有 Java 变更");
    }

    @Test
    void truncatesLongListsWithFingerprint() {
        List<FileChange> changes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            changes.add(new FileChange("src/File" + i + ".java", null,
                    FileChange.Status.MODIFIED, List.of(), false, true));
        }
        List<ApiImpact> impacts = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            impacts.add(impact("POST", "/api/" + i, 100 - i, "HIGH"));
        }
        impacts.add(impact("GET", "/hot", 500, "CRITICAL"));
        AnalysisResult result = new AnalysisResult("1", METADATA,
                new AnalysisResult.Summary(12, 12, 4, 3, 12, "CRITICAL", 90, false),
                changes, List.of(), List.of(), List.of(), impacts, List.of(), ImpactGraph.empty(),
                new RiskReport("CRITICAL", 90, List.of(), SafeMode.disabled()),
                sixWarnings(), Map.of());

        String markdown = MarkdownReport.render(result);

        assertThat(markdown).contains("另有 2 个变更文件");
        assertThat(markdown).contains("另有 2 个受影响 API");
        assertThat(markdown).contains("另有 1 条警告");
        assertThat(markdown.indexOf("`GET /hot`")).isLessThan(markdown.indexOf("`POST /api/0`"));
        assertThat(markdown).doesNotContain("src/File10.java");
    }

    @Test
    void rendersEmptyAnalysis() {
        String markdown = MarkdownReport.render(AnalysisResult.empty(METADATA));

        assertThat(markdown).startsWith(MarkdownReport.MARKER);
        assertThat(markdown).contains("**风险：LOW / 0**");
        assertThat(markdown).contains("没有变更文件。");
        assertThat(markdown).contains("未发现受影响的 REST API。");
    }

    private static List<Warning> sixWarnings() {
        List<Warning> warnings = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            warnings.add(Warning.of("W" + i, "警告 " + i));
        }
        return warnings;
    }

    private static ApiImpact impact(String verb, String path, int score, String level) {
        String key = ApiInfo.apiKey(verb, path);
        ApiInfo api = new ApiInfo(ApiInfo.apiId(key), key, verb, path, "DemoController",
                "DemoController#handle", "com.example.DemoController", "handle",
                "src/main/java/com/example/DemoController.java", 20,
                List.of(), List.of(), Map.of(), "FQN", false);
        return new ApiImpact(api, level, score, 0.9, 2, List.of(), List.of());
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
