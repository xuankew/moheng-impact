package io.moheng.cli;

import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.Edge;
import io.moheng.core.model.ImpactPath;
import io.moheng.core.model.RiskReason;
import io.moheng.core.model.Warning;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class TextReport {

    private TextReport() {
    }

    static String render(AnalysisResult result) {
        StringBuilder out = new StringBuilder();
        AnalysisResult.Metadata metadata = result.analysis();
        AnalysisResult.Summary summary = result.summary();

        out.append("墨衡·见微 影响分析\n");
        out.append("仓库   ").append(metadata.repo()).append('\n');
        out.append("基线   ").append(metadata.base()).append(" → ").append(metadata.head())
                .append(" (").append(metadata.headMode()).append(")\n");
        out.append("变更   ").append(summary.changedFiles()).append(" 文件 / ")
                .append(summary.changedJavaFiles()).append(" Java / ")
                .append(summary.changedSymbols()).append(" 符号\n");
        out.append("影响   ").append(summary.impactedMethods()).append(" 方法 / ")
                .append(summary.impactedApis()).append(" API\n");
        out.append("风险   ").append(label(summary.riskLevel())).append("（")
                .append(summary.riskLevel()).append(' ').append(summary.riskScore()).append("）\n");

        if (result.risk().safeMode().enabled()) {
            out.append("\n[Safe Mode] 已启用：").append(result.risk().safeMode().recommendation()).append('\n');
            for (RiskReason trigger : result.risk().safeMode().triggers()) {
                out.append("  - ").append(trigger.message());
                Object file = trigger.evidence().get("file");
                if (file != null) {
                    out.append("（").append(file).append("）");
                }
                out.append('\n');
            }
        }

        List<ApiImpact> impacts = result.apiImpacts().stream()
                .sorted(Comparator.comparingInt(ApiImpact::score).reversed())
                .toList();
        if (!impacts.isEmpty()) {
            out.append("\nAPI 影响\n");
            int limit = Math.min(impacts.size(), 50);
            for (int i = 0; i < limit; i++) {
                ApiImpact impact = impacts.get(i);
                out.append("  [").append(impact.level()).append(' ').append(impact.score()).append("] ")
                        .append(impact.api().verb()).append(' ').append(impact.api().path()).append('\n');
                out.append("      ").append(impact.api().controller()).append('#')
                        .append(impact.api().handlerName())
                        .append("  深度 ").append(impact.depth())
                        .append("  置信度 ").append(confidence(impact.confidence())).append('\n');
                for (RiskReason reason : impact.reasons()) {
                    out.append("      +").append(reason.weight()).append(' ')
                            .append(reason.message()).append('\n');
                }
                ImpactPath path = impact.paths().isEmpty() ? null : impact.paths().get(0);
                if (path != null) {
                    out.append("      路径 ").append(pathText(path)).append('\n');
                }
            }
            if (impacts.size() > limit) {
                out.append("  …… 另有 ").append(impacts.size() - limit).append(" 个 API\n");
            }
        }

        if (!result.apiContractChanges().isEmpty()) {
            out.append("\nAPI 契约变更\n");
            result.apiContractChanges().stream().limit(20).forEach(change ->
                    out.append("  - ").append(change.type()).append(' ')
                            .append(change.after() == null ? change.before().key() : change.after().key())
                            .append('\n'));
        }

        if (!result.warnings().isEmpty()) {
            out.append("\n警告\n");
            int limit = Math.min(result.warnings().size(), 20);
            for (int i = 0; i < limit; i++) {
                Warning warning = result.warnings().get(i);
                out.append("  - [").append(warning.code()).append("] ").append(warning.message()).append('\n');
            }
            if (result.warnings().size() > limit) {
                out.append("  …… 另有 ").append(result.warnings().size() - limit).append(" 条\n");
            }
        }
        return out.toString();
    }

    private static String pathText(ImpactPath path) {
        StringBuilder text = new StringBuilder();
        for (Edge edge : path.edges()) {
            if (!text.isEmpty()) {
                text.append(" → ");
            }
            text.append(shortName(edge.from())).append(" --")
                    .append(edge.type()).append(' ').append(confidence(edge.confidence()))
                    .append("--> ").append(shortName(edge.to()));
        }
        if (text.isEmpty()) {
            text.append(path.changedKey());
        }
        return text.toString();
    }

    private static String shortName(String key) {
        String value = key;
        int marker = value.indexOf(':');
        if (marker >= 0) {
            value = value.substring(marker + 1);
        }
        int hash = value.lastIndexOf('#');
        if (hash >= 0) {
            return value.substring(hash + 1);
        }
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private static String confidence(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String label(String level) {
        return switch (level) {
            case "CRITICAL" -> "严重";
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            default -> "低";
        };
    }
}
