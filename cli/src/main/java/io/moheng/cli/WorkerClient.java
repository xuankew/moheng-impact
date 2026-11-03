package io.moheng.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class WorkerClient {

    private WorkerClient() {
    }

    static AnalysisResult analyze(AnalysisRequest request, String runId, Duration timeout, Path explicitJar) {
        Path jar = locateJar(explicitJar);
        if (jar == null) {
            throw new CliException(ExitCodes.USAGE,
                    "未找到 moheng-worker.jar；请先执行 mvn package，或设置 MOHENG_WORKER_JAR 指向 worker jar");
        }
        Path requestFile = null;
        Path outputFile = null;
        try {
            requestFile = Files.createTempFile("moheng-request-", ".json");
            outputFile = Files.createTempFile("moheng-output-", ".json");
            Files.writeString(requestFile, Json.write(request), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(javaBinary());
            command.add("-Xmx" + memory());
            command.add("-Dfile.encoding=UTF-8");
            command.add("-jar");
            command.add(jar.toString());
            command.add("--request");
            command.add(requestFile.toString());
            command.add("--run-id");
            command.add(runId);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(outputFile.toFile());
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new CliException(ExitCodes.WORKER_UNAVAILABLE,
                        "worker 执行超时（" + timeout.toSeconds() + " 秒），可调整 --timeout");
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            int exit = process.exitValue();
            if (exit != 0) {
                throw new CliException(mapExit(exit), workerError(output, exit));
            }
            if (output.isBlank()) {
                throw new CliException(ExitCodes.ANALYSIS_FAILED, "worker 未返回结果");
            }
            return Json.read(output, AnalysisResult.class);
        } catch (IOException e) {
            throw new CliException(ExitCodes.WORKER_UNAVAILABLE, "无法启动 worker: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException(ExitCodes.WORKER_UNAVAILABLE, "worker 执行被中断", e);
        } finally {
            deleteQuietly(requestFile);
            deleteQuietly(outputFile);
        }
    }

    static Path locateJar(Path explicitJar) {
        if (explicitJar != null) {
            Path candidate = explicitJar.toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate)) {
                throw new CliException(ExitCodes.USAGE, "worker jar 不存在: " + candidate);
            }
            return candidate;
        }
        String env = System.getenv("MOHENG_WORKER_JAR");
        if (env != null && !env.isBlank()) {
            Path candidate = Path.of(env).toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate)) {
                throw new CliException(ExitCodes.USAGE, "MOHENG_WORKER_JAR 指向的文件不存在: " + candidate);
            }
            return candidate;
        }
        String property = System.getProperty("moheng.worker.jar");
        if (property != null && !property.isBlank()) {
            Path candidate = Path.of(property).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path sibling = siblingOfCli("moheng-worker.jar");
        if (sibling != null) {
            return sibling;
        }
        return devBuild();
    }

    private static Path siblingOfCli(String name) {
        Path location = cliLocation();
        if (location == null) {
            return null;
        }
        Path candidate = location.resolve(name);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static Path cliLocation() {
        try {
            Path location = Path.of(WorkerClient.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    private static Path devBuild() {
        Path fromCwd = searchUp(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            return fromCwd;
        }
        Path location = cliLocation();
        return location == null ? null : searchUp(location);
    }

    private static Path searchUp(Path start) {
        Path dir = start;
        while (dir != null) {
            Path candidate = dir.resolve("worker").resolve("target").resolve("moheng-worker.jar");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static String javaBinary() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String memory() {
        String memory = System.getenv("MOHENG_WORKER_MEMORY");
        return memory == null || memory.isBlank() ? "1g" : memory.trim();
    }

    private static int mapExit(int workerExit) {
        return switch (workerExit) {
            case ExitCodes.USAGE, ExitCodes.REPOSITORY, ExitCodes.ANALYSIS_FAILED, ExitCodes.WORKER_UNAVAILABLE -> workerExit;
            default -> ExitCodes.ANALYSIS_FAILED;
        };
    }

    private static String workerError(String output, int exit) {
        try {
            JsonNode node = Json.mapper().readTree(output);
            if (node != null && node.hasNonNull("error")) {
                JsonNode error = node.get("error");
                String code = error.path("code").asText("");
                String message = error.path("message").asText("");
                return (code.isBlank() ? "" : "[" + code + "] ") + message;
            }
        } catch (IOException ignored) {
        }
        return "worker 退出码 " + exit;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
