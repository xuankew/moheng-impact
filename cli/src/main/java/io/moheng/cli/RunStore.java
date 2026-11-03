package io.moheng.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

final class RunStore {

    private static final String DIR = ".moheng/runs";

    private RunStore() {
    }

    static Path save(Path repo, String runId, AnalysisResult result) {
        Path directory = repo.resolve(DIR);
        try {
            Files.createDirectories(directory);
            Path ignore = directory.getParent().resolve(".gitignore");
            if (!Files.exists(ignore)) {
                Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
            }
            ObjectNode node = Json.mapper().valueToTree(result);
            node.put("runId", runId);
            node.put("status", "SUCCEEDED");
            Path file = directory.resolve(runId + ".json");
            Files.writeString(file, Json.mapper().writeValueAsString(node) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new CliException(ExitCodes.ANALYSIS_FAILED, "无法保存分析结果: " + e.getMessage(), e);
        }
    }

    static Path latest(Path repo) {
        Path directory = repo.resolve(DIR);
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparingLong(RunStore::modified))
                    .orElse(null);
        } catch (IOException e) {
            throw new CliException(ExitCodes.USAGE, "无法读取历史结果: " + e.getMessage(), e);
        }
    }

    static Path find(Path repo, String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]+")) {
            throw new CliException(ExitCodes.USAGE, "无效的 run id: " + runId);
        }
        Path file = repo.resolve(DIR).resolve(runId.endsWith(".json") ? runId : runId + ".json");
        if (!Files.isRegularFile(file)) {
            throw new CliException(ExitCodes.USAGE, "未找到分析结果: " + runId);
        }
        return file;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
