package io.moheng.server.api;

import io.moheng.server.repo.AnalysisRun;
import io.moheng.server.repo.Project;
import io.moheng.server.repo.RunRepository;
import io.moheng.server.service.ProjectService;
import io.moheng.server.service.SourceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
public class SourceController {

    private final RunRepository runs;
    private final ProjectService projects;
    private final SourceService sources;

    public SourceController(RunRepository runs, ProjectService projects, SourceService sources) {
        this.runs = runs;
        this.projects = projects;
        this.sources = sources;
    }

    @GetMapping(value = "/{id}/source", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> source(@PathVariable String id,
                                         @RequestParam("file") String file,
                                         @RequestParam(name = "start", required = false) Integer start,
                                         @RequestParam(name = "end", required = false) Integer end) {
        AnalysisRun run = runs.find(id).orElseThrow(() -> ApiException.notFound("分析不存在: " + id));
        Project project = projects.require(run.projectId());
        return ResponseEntity.ok(sources.read(project, run, file, start, end));
    }
}
