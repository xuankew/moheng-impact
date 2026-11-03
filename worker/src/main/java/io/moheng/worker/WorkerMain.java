package io.moheng.worker;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.moheng.analyzer.java.JavaProjectAnalyzer;
import io.moheng.core.AnalysisEngine;
import io.moheng.core.AnalysisException;
import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.AnalysisResult;
import io.moheng.core.report.ProgressSink;
import io.moheng.core.model.Warning;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class WorkerMain {

    private static final String PROTOCOL_VERSION = "1";

    private WorkerMain() {
    }

    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        String runId = UUID.randomUUID().toString();
        String requestPath = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--request") && i + 1 < args.length) {
                requestPath = args[++i];
            } else if (args[i].equals("--run-id") && i + 1 < args.length) {
                runId = args[++i];
            }
        }
        try {
            String payload = requestPath == null
                    ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Path.of(requestPath), StandardCharsets.UTF_8);
            AnalysisRequest request = Json.read(payload, AnalysisRequest.class);
            ProgressSink progress = new StderrProgress();
            AnalysisResult result = new AnalysisEngine(new JavaProjectAnalyzer()).analyze(request, progress);
            ObjectNode output = Json.mapper().valueToTree(result);
            output.put("protocolVersion", PROTOCOL_VERSION);
            output.put("runId", runId);
            output.put("status", "SUCCEEDED");
            System.out.println(Json.mapper().writeValueAsString(output));
            System.out.flush();
            System.exit(0);
        } catch (OutOfMemoryError e) {
            fail(runId, "OUT_OF_MEMORY", "worker 内存不足", 6);
        } catch (AnalysisException e) {
            if ("1".equals(System.getenv("MOHENG_DEBUG"))) {
                e.printStackTrace();
            }
            int exit = switch (e.code()) {
                case "REPO_NOT_FOUND", "NOT_A_GIT_REPOSITORY", "REVISION_NOT_FOUND" -> 3;
                case "INVALID_REQUEST" -> 2;
                default -> 4;
            };
            fail(runId, e.code(), e.getMessage(), exit);
        } catch (Throwable e) {
            if ("1".equals(System.getenv("MOHENG_DEBUG"))) {
                e.printStackTrace();
            }
            fail(runId, "ANALYSIS_FAILED",
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()), 4);
        }
    }

    private static void fail(String runId, String code, String message, int exitCode) {
        ObjectNode error = Json.mapper().createObjectNode();
        error.put("protocolVersion", PROTOCOL_VERSION);
        error.put("runId", runId);
        error.put("status", "FAILED");
        ObjectNode detail = error.putObject("error");
        detail.put("code", code);
        detail.put("message", message == null ? "" : message);
        try {
            System.out.println(Json.mapper().writeValueAsString(error));
            System.out.flush();
        } catch (Exception ignored) {
        }
        System.err.println("[" + code + "] " + (message == null ? "" : message));
        System.exit(exitCode);
    }

    private static final class StderrProgress implements ProgressSink {
        @Override
        public void progress(String phase, int percent, String message) {
            System.err.println("[" + percent + "%] " + phase + " " + message);
        }

        @Override
        public void warning(Warning warning) {
            System.err.println("[WARN] " + warning.code() + " " + warning.message());
        }
    }
}
