package io.moheng.cli;

import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(name = "show", mixinStandardHelpOptions = true,
        description = "查看最近或指定的分析结果")
final class ShowCommand implements Callable<Integer> {

    @Option(names = "--repo", paramLabel = "<path>", description = "仓库路径，默认当前目录")
    private Path repoOption;

    @Option(names = "--run", paramLabel = "<id>", description = "指定 run id")
    private String runOption;

    @Option(names = "--latest", description = "查看最近一次分析（默认）")
    private boolean latest;

    @Option(names = "--format", paramLabel = "text|json", description = "输出格式")
    private String formatOption;

    @Override
    public Integer call() {
        Path repo = (repoOption == null ? Path.of(".") : repoOption).toAbsolutePath().normalize();
        Path file = runOption != null ? RunStore.find(repo, runOption) : RunStore.latest(repo);
        if (file == null) {
            throw new CliException(ExitCodes.USAGE, "暂无分析结果；请先执行 mh impact analyze");
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException(ExitCodes.USAGE, "无法读取 " + file + ": " + e.getMessage(), e);
        }
        String format = formatOption == null ? "text" : formatOption.toLowerCase(Locale.ROOT);
        if (format.equals("json")) {
            System.out.print(content);
            if (!content.endsWith(System.lineSeparator())) {
                System.out.println();
            }
            return ExitCodes.OK;
        }
        if (!format.equals("text")) {
            throw new CliException(ExitCodes.USAGE, "无效的输出格式: " + formatOption + "（可选 text/json）");
        }
        AnalysisResult result = Json.read(content, AnalysisResult.class);
        System.out.println("结果文件: " + file);
        System.out.print(TextReport.render(result));
        return ExitCodes.OK;
    }
}
