package io.moheng.server.api;

import io.moheng.core.model.AnalysisRequest;
import io.moheng.server.repo.AnalysisRun;
import io.moheng.server.repo.Project;
import io.moheng.server.repo.RunRepository;
import io.moheng.server.service.AnalysisRunner;
import io.moheng.server.service.ProjectService;
import io.moheng.server.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.moheng.server.api.Responses.data;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final ProjectService projects;
    private final RunRepository runs;
    private final AnalysisRunner runner;
    private final ReportService reports;

    public AnalysisController(ProjectService projects, RunRepository runs,
                              AnalysisRunner runner, ReportService reports) {
        this.projects = projects;
        this.runs = runs;
        this.runner = runner;
        this.reports = reports;
    }

    @PostMapping("/projects/{projectId}/analyses")
    public ResponseEntity<Map<String, Object>> create(@PathVariable String projectId,
                                                      @RequestBody(required = false) AnalysisCreateRequest body) {
        Project project = projects.require(projectId);
        AnalysisCreateRequest request = body == null ? new AnalysisCreateRequest(
                null, null, null, null, null, null, null, null, null, null, null) : body;
        String base = blank(request.base())
                ? (blank(project.defaultBase()) ? "HEAD~1" : project.defaultBase())
                : request.base().trim();
        String head = blank(request.head()) ? "WORKTREE" : request.head().trim();
        AnalysisRequest.HeadMode headMode = headMode(request.headMode());
        AnalysisRequest.Options options = new AnalysisRequest.Options(
                request.maxDepth() == null ? 5 : request.maxDepth(),
                request.apiPrefix() == null ? project.apiPrefix() : request.apiPrefix(),
                request.javaVersion() == null ? 21 : request.javaVersion(),
                request.includePackages() == null ? project.includePackages() : request.includePackages(),
                request.excludePackages() == null ? project.excludePackages() : request.excludePackages(),
                request.sourceRoots() == null ? project.sourceRoots() : request.sourceRoots(),
                Boolean.TRUE.equals(request.includeTests()),
                Boolean.TRUE.equals(request.highConfidenceOnly()));
        AnalysisRun run = runner.start(project, base, head, headMode, options);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(data(reports.status(run)));
    }

    @GetMapping("/analyses")
    public Map<String, Object> list(@RequestParam(name = "projectId", required = false) String projectId,
                                    @RequestParam(name = "limit", defaultValue = "20") int limit) {
        List<AnalysisRun> rows = blank(projectId)
                ? runs.listRecent(limit) : runs.listByProject(projectId, limit);
        return data(rows.stream().map(reports::status).toList());
    }

    @GetMapping("/analyses/{id}")
    public Map<String, Object> result(@PathVariable String id) {
        AnalysisRun run = require(id);
        return data(reports.result(run));
    }

    @GetMapping("/analyses/{id}/status")
    public Map<String, Object> status(@PathVariable String id) {
        return data(reports.status(require(id)));
    }

    @GetMapping("/analyses/{id}/apis")
    public Map<String, Object> apis(@PathVariable String id,
                                    @RequestParam(name = "risk", required = false) String risk,
                                    @RequestParam(name = "q", required = false) String query,
                                    @RequestParam(name = "page", defaultValue = "1") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
        AnalysisRun run = require(id);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        long total = runs.countApiImpacts(run.id(), risk, query);
        List<Map<String, Object>> items = runs.pageApiImpacts(run.id(), risk, query, safePage, safeSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("total", total);
        return data(data);
    }

    @GetMapping("/analyses/{id}/apis/{apiId}")
    public Map<String, Object> api(@PathVariable String id, @PathVariable String apiId) {
        return data(reports.apiDetail(require(id), apiId));
    }

    @GetMapping("/analyses/{id}/changes")
    public Map<String, Object> changes(@PathVariable String id) {
        return data(reports.changes(require(id)));
    }

    @GetMapping("/analyses/{id}/graph")
    public Map<String, Object> graph(@PathVariable String id,
                                     @RequestParam(name = "apiKey", required = false) String apiKey,
                                     @RequestParam(name = "changed", defaultValue = "false") boolean changed,
                                     @RequestParam(name = "maxDepth", defaultValue = "0") int maxDepth) {
        return data(reports.graph(require(id), apiKey, changed, maxDepth));
    }

    @PostMapping("/analyses/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        AnalysisRun run = require(id);
        boolean cancelled = runner.cancel(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cancelled", cancelled);
        data.put("status", reports.status(require(id)));
        return data(data);
    }

    @DeleteMapping("/analyses/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        AnalysisRun run = require(id);
        if ("RUNNING".equals(run.status()) || "QUEUED".equals(run.status())) {
            runner.cancel(id);
        }
        runs.delete(id);
        return data(Map.of("deleted", id));
    }

    private AnalysisRun require(String id) {
        return runs.find(id).orElseThrow(() -> ApiException.notFound("分析不存在: " + id));
    }

    private static AnalysisRequest.HeadMode headMode(String value) {
        if (blank(value)) {
            return AnalysisRequest.HeadMode.AUTO;
        }
        try {
            return AnalysisRequest.HeadMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("headMode 只支持 AUTO、REV_COMMIT、WORKTREE");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
