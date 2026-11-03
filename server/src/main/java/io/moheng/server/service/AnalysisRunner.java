package io.moheng.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.moheng.core.AnalysisException;
import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;
import io.moheng.server.config.MohengProperties;
import io.moheng.server.repo.AnalysisRun;
import io.moheng.server.repo.Project;
import io.moheng.server.repo.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);
    private static final Pattern PROGRESS = Pattern.compile("^\\[(\\d+)%]\\s+(\\S+)\\s*(.*)$");

    private final MohengProperties properties;
    private final RunRepository runs;
    private final WorkerLocator workerLocator;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "moheng-analysis"));
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public AnalysisRunner(MohengProperties properties, RunRepository runs, WorkerLocator workerLocator) {
        this.properties = properties;
        this.runs = runs;
        this.workerLocator = workerLocator;
    }

    public AnalysisRun start(Project project, String base, String head,
                             AnalysisRequest.HeadMode headMode, AnalysisRequest.Options options) {
        String id = UUID.randomUUID().toString();
        String resolvedHead = head == null || head.isBlank() ? "WORKTREE" : head;
        AnalysisRun run = new AnalysisRun(id, project.id(), base, resolvedHead, headMode.name(),
                "QUEUED", "QUEUED", 0, null, null, false, null, null,
                null, null, "[]", Instant.now().toString(), null);
        runs.insert(run);
        executor.submit(() -> execute(run, project, options));
        return run;
    }

    public boolean cancel(String runId) {
        cancelled.add(runId);
        Process process = processes.get(runId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            return true;
        }
        return false;
    }

    public boolean isCancelled(String runId) {
        return cancelled.contains(runId);
    }

    private void execute(AnalysisRun run, Project project, AnalysisRequest.Options options) {
        if (cancelled.remove(run.id())) {
            runs.markCancelled(run.id(), Instant.now().toString());
            return;
        }
        Path tempDir = null;
        Process process = null;
        try {
            Path worker = workerLocator.locate().orElse(null);
            if (worker == null) {
                fail(run.id(), "WORKER_START_FAILED", "未找到 moheng-worker.jar", Instant.now().toString());
                return;
            }
            runs.markRunning(run.id());
            tempDir = Files.createDirectories(properties.temporaryDirectory().resolve(run.id()));
            Path requestFile = tempDir.resolve("request.json");
            Path resultFile = tempDir.resolve("result.json");
            AnalysisRequest request = new AnalysisRequest(project.repoPath(), run.baseRev(),
                    run.headRev(), AnalysisRequest.HeadMode.valueOf(run.headMode()), options);
            Files.writeString(requestFile, Json.write(request), StandardCharsets.UTF_8);

            String java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
            ProcessBuilder builder = new ProcessBuilder(List.of(
                    java,
                    "-Xmx" + properties.heap(),
                    "-Dfile.encoding=UTF-8",
                    "-jar", worker.toString(),
                    "--request", requestFile.toString(),
                    "--run-id", run.id()));
            builder.redirectOutput(resultFile.toFile());
            process = builder.start();
            processes.put(run.id(), process);
            Process started = process;
            StringBuilder stderr = new StringBuilder();
            Thread reader = new Thread(() -> readProgress(run.id(), started, stderr), "moheng-worker-stderr");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
            processes.remove(run.id());
            if (!finished) {
                process.destroyForcibly();
                fail(run.id(), "WORKER_TIMEOUT", "分析超时（" + properties.timeoutSeconds() + " 秒）",
                        Instant.now().toString());
                return;
            }
            reader.join(2_000);
            if (cancelled.remove(run.id())) {
                runs.markCancelled(run.id(), Instant.now().toString());
                return;
            }
            int exit = process.exitValue();
            String output = Files.exists(resultFile) ? Files.readString(resultFile, StandardCharsets.UTF_8) : "";
            if (exit != 0) {
                failFromWorker(run.id(), exit, output, stderr.toString());
                return;
            }
            AnalysisResult result = Json.read(output, AnalysisResult.class);
            runs.markSucceeded(run.id(), result);
            log.info("analysis finished: runId={}, apis={}, changes={}",
                    run.id(), result.apiImpacts().size(), result.changes().size());
        } catch (AnalysisException e) {
            fail(run.id(), e.code(), e.getMessage(), Instant.now().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(run.id(), "ANALYSIS_FAILED", "分析被中断", Instant.now().toString());
        } catch (IOException e) {
            fail(run.id(), "WORKER_START_FAILED", "无法启动 worker: " + e.getMessage(), Instant.now().toString());
        } catch (RuntimeException e) {
            log.error("analysis failed: runId={}, error={}", run.id(), e.getClass().getSimpleName());
            fail(run.id(), "ANALYSIS_FAILED",
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()),
                    Instant.now().toString());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            processes.remove(run.id());
            deleteRecursively(tempDir);
        }
    }

    private void readProgress(String runId, Process process, StringBuilder stderr) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (stderr.length() < 4_000) {
                    stderr.append(line).append('\n');
                }
                Matcher matcher = PROGRESS.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                try {
                    int percent = Integer.parseInt(matcher.group(1));
                    runs.updateProgress(runId, matcher.group(2), percent);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void failFromWorker(String runId, int exit, String output, String stderr) {
        String code = null;
        String message = null;
        try {
            JsonNode node = Json.mapper().readTree(output);
            if (node != null && node.hasNonNull("error")) {
                code = node.get("error").path("code").asText(null);
                message = node.get("error").path("message").asText(null);
            }
        } catch (Exception ignored) {
        }
        if (stderr.contains("OutOfMemoryError") || exit == 6) {
            code = "WORKER_OOM";
        } else if (exit == 2) {
            code = "INVALID_REQUEST";
        } else if (code == null || code.isBlank()) {
            code = exit == 3 ? "REPOSITORY_NOT_FOUND" : "ANALYSIS_FAILED";
        }
        if (message == null || message.isBlank()) {
            message = "worker 退出码 " + exit;
        }
        fail(runId, code, message, Instant.now().toString());
    }

    private void fail(String runId, String code, String message, String finishedAt) {
        try {
            runs.markFailed(runId, code, message, "[]", finishedAt);
            log.warn("analysis failed: runId={}, code={}", runId, code);
        } catch (RuntimeException e) {
            log.error("cannot persist failure: runId={}, error={}", runId, e.getClass().getSimpleName());
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
