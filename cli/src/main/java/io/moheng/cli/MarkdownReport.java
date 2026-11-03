package io.moheng.cli;

import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.FileChange;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.Warning;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarkdownReport {

    static final String MARKER = "<!-- moheng-impact -->";
    private static final int MAX_FILES = 10;
    private static final int MAX_APIS = 10;
    private static final int MAX_REASONS = 6;
    private static final int MAX_WARNINGS = 5;

    private MarkdownReport() {
    }

    static String render(AnalysisResult result) {
        AnalysisResult.Metadata metadata = result.analysis();
        AnalysisResult.Summary summary = result.summary();
        StringBuilder out = new StringBuilder();

        out.append(MARKER).append('\n');
        out.append("## 墨衡·见微 · 影响分析\n\n");
        out.append("**风险：").append(summary.riskLevel()).append(" / ").append(summary.riskScore()).append("**");
        if (summary.safeMode()) {
            out.append(" · Safe Mode 已启用");
        }
        out.append("\n\n");
        out.append("`").append(shortRev(metadata.base())).append("` → `")
                .append(shortRev(metadata.head())).append("`");
        String repo = repoName(metadata.repo());
        if (!repo.isBlank()) {
            out.append(" · `").append(cell(repo)).append("`");
        }
        out.append("\n\n");
        out.append("| 变更文件 | 变更符号 | 受影响方法 | 受影响 API | 契约变更 |\n");
        out.append("| ---: | ---: | ---: | ---: | ---: |\n");
        out.append("| ").append(summary.changedFiles())
                .append("（Java ").append(summary.changedJavaFiles()).append("）")
                .append(" | ").append(summary.changedSymbols())
                .append(" | ").append(summary.impactedMethods())
                .append(" | ").append(summary.impactedApis())
                .append(" | ").append(result.apiContractChanges().size())
                .append(" |\n\n");

        appendFiles(out, result.changes());
        appendApis(out, result.apiImpacts());
        appendReasons(out, result.risk().reasons());
        appendWarnings(out, result.warnings());

        out.append("---\n");
        out.append("由墨衡·见微生成 · 静态分析结果，仅用于确定回归范围\n");
        return out.toString();
    }

    private static void appendFiles(StringBuilder out, List<FileChange> changes) {
        out.append("### 变更文件\n\n");
        if (changes.isEmpty()) {
            out.append("没有变更文件。\n\n");
            return;
        }
        out.append("| 文件 | 状态 |\n| --- | --- |\n");
        changes.stream().limit(MAX_FILES).forEach(change -> {
            String path = change.path();
            if (change.oldPath() != null && !change.oldPath().isBlank() && !change.oldPath().equals(path)) {
                path = path + "（来自 " + change.oldPath() + "）";
            }
            out.append("| `").append(cell(path)).append("` | ")
                    .append(statusLabel(change.status())).append(" |\n");
        });
        if (changes.size() > MAX_FILES) {
            out.append("\n> 另有 ").append(changes.size() - MAX_FILES)
                    .append(" 个变更文件，完整结果见流水线产物 moheng-impact.md。\n");
        }
        out.append('\n');
    }

    private static void appendApis(StringBuilder out, List<ApiImpact> impacts) {
        out.append("### 受影响 API\n\n");
        if (impacts.isEmpty()) {
            out.append("未发现受影响的 REST API。\n\n");
            return;
        }
        List<ApiImpact> sorted = impacts.stream()
                .sorted(Comparator.comparingInt(ApiImpact::score).reversed()
                        .thenComparing(impact -> impact.api().path()))
                .toList();
        out.append("| API | 风险 | 分数 | 深度 | 置信度 |\n");
        out.append("| --- | --- | ---: | ---: | ---: |\n");
        sorted.stream().limit(MAX_APIS).forEach(impact -> out.append("| `")
                .append(cell(impact.api().verb() + " " + impact.api().path()))
                .append("` | ").append(impact.level())
                .append(" | ").append(impact.score())
                .append(" | ").append(impact.depth())
                .append(" | ").append(Math.round(impact.confidence() * 100)).append("% |\n"));
        if (sorted.size() > MAX_APIS) {
            out.append("\n> 另有 ").append(sorted.size() - MAX_APIS)
                    .append(" 个受影响 API，完整结果见流水线产物 moheng-impact.md。\n");
        }
        out.append('\n');
    }

    private static void appendReasons(StringBuilder out, List<RiskReason> reasons) {
        if (reasons.isEmpty()) {
            return;
        }
        Map<String, RiskReason> unique = new LinkedHashMap<>();
        for (RiskReason reason : reasons) {
            unique.putIfAbsent(reason.message(), reason);
        }
        out.append("### 风险原因\n\n");
        unique.values().stream().limit(MAX_REASONS).forEach(reason -> out.append("- `")
                .append(cell(reason.code())).append("` ")
                .append(reason.weight() >= 0 ? "+" : "")
                .append(reason.weight()).append(' ')
                .append(cell(reason.message())).append('\n'));
        out.append('\n');
    }

    private static void appendWarnings(StringBuilder out, List<Warning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        out.append("### 警告\n\n");
        warnings.stream().limit(MAX_WARNINGS).forEach(warning -> out.append("- `")
                .append(cell(warning.code())).append("` ")
                .append(cell(warning.message())).append('\n'));
        if (warnings.size() > MAX_WARNINGS) {
            out.append("- 另有 ").append(warnings.size() - MAX_WARNINGS).append(" 条警告\n");
        }
        out.append('\n');
    }

    private static String statusLabel(FileChange.Status status) {
        return switch (status) {
            case ADDED -> "新增";
            case MODIFIED -> "修改";
            case DELETED -> "删除";
            case RENAMED -> "重命名";
        };
    }

    private static String shortRev(String rev) {
        if (rev == null || rev.isBlank()) {
            return "";
        }
        String value = rev.trim();
        if (value.matches("[0-9a-fA-F]{12,}")) {
            return value.substring(0, 8);
        }
        return value;
    }

    private static String repoName(String repo) {
        if (repo == null || repo.isBlank()) {
            return "";
        }
        try {
            Path name = Path.of(repo).getFileName();
            return name == null ? repo : name.toString();
        } catch (InvalidPathException e) {
            return repo;
        }
    }

    private static String cell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r", "")
                .replace("\n", " ");
    }
}
