package io.moheng.cli;

import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "analyze", mixinStandardHelpOptions = true,
        description = "分析 base 到 head 的变更影响")
final class AnalyzeCommand implements Callable<Integer> {

    @Option(names = "--repo", paramLabel = "<path>", description = "仓库路径，默认当前目录")
    private Path repoOption;

    @Option(names = "--base", paramLabel = "<rev>", description = "基线 revision（分支/标签/SHA）")
    private String baseOption;

    @Option(names = "--head", paramLabel = "<rev|WORKTREE>", description = "目标 revision，默认 WORKTREE")
    private String headOption;

    @Option(names = "--format", paramLabel = "text|json|markdown", description = "输出格式")
    private String formatOption;

    @Option(names = "--out", paramLabel = "<file>", description = "输出到文件")
    private Path outOption;

    @Option(names = "--max-depth", paramLabel = "<1-10>", description = "反向影响最大深度")
    private Integer maxDepthOption;

    @Option(names = "--api-prefix", paramLabel = "<path>", description = "运行时 API 前缀")
    private String apiPrefixOption;

    @Option(names = "--include", paramLabel = "<package>", description = "仅分析指定包前缀，可重复")
    private List<String> includeOption = new ArrayList<>();

    @Option(names = "--exclude", paramLabel = "<package>", description = "排除包前缀，可重复")
    private List<String> excludeOption = new ArrayList<>();

    @Option(names = "--source-root", paramLabel = "<path>", description = "额外源码根，可重复")
    private List<String> sourceRootOption = new ArrayList<>();

    @Option(names = "--include-tests", arity = "0..1", fallbackValue = "true",
            description = "包含 src/test/java")
    private Boolean includeTestsOption;

    @Option(names = "--high-confidence-only", arity = "0..1", fallbackValue = "true",
            description = "仅保留高置信度边")
    private Boolean highConfidenceOption;

    @Option(names = "--java-version", paramLabel = "<n>", description = "源码 Java 版本，默认 21")
    private Integer javaVersionOption;

    @Option(names = "--fail-on-risk", paramLabel = "<level>", description = "达到风险等级时返回退出码 5")
    private String failOnRiskOption;

    @Option(names = "--timeout", paramLabel = "<seconds>", description = "worker 超时，默认 600 秒")
    private int timeoutSeconds = 600;

    @Option(names = "--worker-jar", paramLabel = "<file>", description = "指定 moheng-worker.jar")
    private Path workerJarOption;

    @Option(names = "--json-schema", description = "打印结果 JSON Schema 后退出")
    private boolean jsonSchema;

    @Option(names = "--no-save", description = "不写入 <repo>/.moheng/runs")
    private boolean noSave;

    @Option(names = "--gitlab-comment", description = "将摘要写入 GitLab MR 评论（需 MR 流水线与 MOHENG_GITLAB_TOKEN）")
    private boolean gitlabComment;

    @Override
    public Integer call() {
        if (jsonSchema) {
            System.out.print(readSchema());
            return ExitCodes.OK;
        }
        GitLabCi gitlab = GitLabCi.detect(System.getenv());
        if (gitlabComment) {
            validateGitLabComment(gitlab);
        }
        Path repo = resolveRepo(gitlab.repo());
        ConfigFile config = ConfigFile.load(repo);

        String base = firstNonBlank(baseOption, gitlab.base(), config.string("base"));
        if (base == null) {
            throw new CliException(ExitCodes.USAGE,
                    "缺少 --base（或在 .moheng.yml 中配置 base，或在 GitLab MR 流水线中运行）");
        }
        String head = firstNonBlank(headOption, gitlab.head(), config.string("head"));
        String format = normalizeFormat(firstNonBlank(formatOption, config.string("output", "format")));
        String failOnRisk = normalizeRisk(firstNonBlank(failOnRiskOption, config.string("risk", "failOn")));

        int maxDepth = maxDepthOption != null ? maxDepthOption
                : config.integer("maxDepth") != null ? config.integer("maxDepth") : 5;
        int javaVersion = javaVersionOption != null ? javaVersionOption
                : config.integer("javaVersion") != null ? config.integer("javaVersion") : 21;
        String apiPrefix = apiPrefixOption != null ? apiPrefixOption
                : config.string("apiPrefix") != null ? config.string("apiPrefix") : "";
        List<String> include = includeOption.isEmpty()
                ? config.strings("includePackages") : includeOption;
        List<String> exclude = excludeOption.isEmpty()
                ? config.strings("excludePackages") : excludeOption;
        List<String> sourceRoots = sourceRootOption.isEmpty()
                ? config.strings("sourceRoots") : sourceRootOption;
        boolean includeTests = includeTestsOption != null ? includeTestsOption
                : Boolean.TRUE.equals(config.bool("includeTests"));
        boolean highConfidenceOnly = highConfidenceOption != null ? highConfidenceOption
                : Boolean.TRUE.equals(config.bool("highConfidenceOnly"));

        AnalysisRequest request = new AnalysisRequest(
                repo.toString(), base, head, AnalysisRequest.HeadMode.AUTO,
                new AnalysisRequest.Options(maxDepth, apiPrefix, javaVersion, include, exclude,
                        sourceRoots, includeTests, highConfidenceOnly));

        String runId = UUID.randomUUID().toString();
        AnalysisResult result = WorkerClient.analyze(request, runId,
                Duration.ofSeconds(Math.max(timeoutSeconds, 1)), workerJarOption);
        Path saved = noSave ? null : RunStore.save(repo, runId, result);

        String output = switch (format) {
            case "json" -> JsonOutput.render(result);
            case "markdown" -> MarkdownReport.render(result);
            default -> TextReport.render(result);
        };
        if (outOption != null) {
            Path target = outOption.toAbsolutePath().normalize();
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, output, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CliException(ExitCodes.ANALYSIS_FAILED, "无法写入 " + target + ": " + e.getMessage(), e);
            }
            System.err.println("结果已保存: " + target);
        } else {
            System.out.print(output);
        }
        if (saved != null) {
            System.err.println("分析记录: " + saved);
        }
        if (gitlabComment) {
            postMergeRequestComment(gitlab, result);
        }

        if (failOnRisk != null && severity(result.risk().level()) >= severity(failOnRisk)) {
            return ExitCodes.RISK_THRESHOLD;
        }
        return ExitCodes.OK;
    }

    private Path resolveRepo(Path gitlabRepo) {
        Path repo;
        if (repoOption != null) {
            repo = repoOption;
        } else {
            ConfigFile cwdConfig = ConfigFile.load(Path.of(".").toAbsolutePath().normalize());
            String configured = cwdConfig.string("repo");
            if (configured != null) {
                repo = cwdConfig.directory().resolve(configured);
            } else if (gitlabRepo != null && Files.isDirectory(gitlabRepo)) {
                repo = gitlabRepo;
            } else {
                repo = Path.of(".");
            }
        }
        repo = repo.toAbsolutePath().normalize();
        if (!Files.isDirectory(repo)) {
            throw new CliException(ExitCodes.REPOSITORY, "仓库目录不存在: " + repo);
        }
        return repo;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizeFormat(String format) {
        if (format == null) {
            return "text";
        }
        String value = format.toLowerCase(Locale.ROOT);
        if (!value.equals("text") && !value.equals("json") && !value.equals("markdown")) {
            throw new CliException(ExitCodes.USAGE, "无效的输出格式: " + format + "（可选 text/json/markdown）");
        }
        return value;
    }

    private static void validateGitLabComment(GitLabCi gitlab) {
        if (!gitlab.hasMergeRequest()) {
            throw new CliException(ExitCodes.USAGE,
                    "--gitlab-comment 需要在 GitLab Merge Request 流水线中运行"
                            + "（缺少 CI_API_V4_URL / CI_PROJECT_ID / CI_MERGE_REQUEST_IID）");
        }
        if (gitlab.token() == null || gitlab.token().isBlank()) {
            throw new CliException(ExitCodes.USAGE,
                    "--gitlab-comment 需要 GitLab 令牌（MOHENG_GITLAB_TOKEN 或 GITLAB_TOKEN，需 api 权限）");
        }
    }

    private static void postMergeRequestComment(GitLabCi gitlab, AnalysisResult result) {
        try {
            GitLabClient.NoteResult note = new GitLabClient(gitlab.apiUrl(), gitlab.token())
                    .upsertMergeRequestNote(gitlab.projectId(), gitlab.mergeRequestIid(),
                            MarkdownReport.render(result));
            System.err.println((note.created() ? "已创建" : "已更新")
                    + " MR !" + gitlab.mergeRequestIid() + " 影响分析评论（note " + note.noteId() + "）");
        } catch (GitLabClient.GitLabException e) {
            System.err.println("警告: MR 评论写入失败: " + e.getMessage());
        }
    }

    private static String normalizeRisk(String level) {
        if (level == null) {
            return null;
        }
        String value = level.toUpperCase(Locale.ROOT);
        if (severity(value) < 0) {
            throw new CliException(ExitCodes.USAGE,
                    "无效的风险等级: " + level + "（可选 LOW/MEDIUM/HIGH/CRITICAL）");
        }
        return value;
    }

    private static int severity(String level) {
        return switch (level == null ? "" : level.toUpperCase(Locale.ROOT)) {
            case "LOW" -> 0;
            case "MEDIUM" -> 1;
            case "HIGH" -> 2;
            case "CRITICAL" -> 3;
            default -> -1;
        };
    }

    private static String readSchema() {
        try (InputStream input = AnalyzeCommand.class.getResourceAsStream("/schema/analysis-result.schema.json")) {
            if (input == null) {
                throw new CliException(ExitCodes.ANALYSIS_FAILED, "缺少结果 JSON Schema 资源");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException(ExitCodes.ANALYSIS_FAILED, "无法读取 JSON Schema: " + e.getMessage(), e);
        }
    }
}
