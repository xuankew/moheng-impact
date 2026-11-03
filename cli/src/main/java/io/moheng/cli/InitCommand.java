package io.moheng.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "init", mixinStandardHelpOptions = true,
        description = "生成 .moheng.yml 配置")
final class InitCommand implements Callable<Integer> {

    private static final String TEMPLATE = """
            version: 1
            repo: .
            base: main
            head: WORKTREE
            maxDepth: 5
            apiPrefix: ""
            includePackages: []
            excludePackages: []
            risk:
              failOn: HIGH
            output:
              format: text
            """;

    @Option(names = "--repo", paramLabel = "<path>", description = "仓库路径，默认当前目录")
    private Path repoOption;

    @Option(names = "--force", description = "覆盖已存在的配置")
    private boolean force;

    @Override
    public Integer call() {
        Path repo = (repoOption == null ? Path.of(".") : repoOption).toAbsolutePath().normalize();
        if (!Files.isDirectory(repo)) {
            throw new CliException(ExitCodes.REPOSITORY, "仓库目录不存在: " + repo);
        }
        Path file = repo.resolve(".moheng.yml");
        if (Files.exists(file) && !force) {
            throw new CliException(ExitCodes.USAGE, file + " 已存在；使用 --force 覆盖");
        }
        try {
            Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliException(ExitCodes.USAGE, "无法写入 " + file + ": " + e.getMessage(), e);
        }
        System.out.println("已生成配置: " + file);
        return ExitCodes.OK;
    }
}
