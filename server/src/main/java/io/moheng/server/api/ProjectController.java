package io.moheng.server.api;

import io.moheng.server.repo.Project;
import io.moheng.server.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static io.moheng.server.api.Responses.data;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public Map<String, Object> list() {
        return data(projects.list());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ProjectRequest request) {
        Project project = projects.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(data(project));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return data(projects.require(id));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody ProjectPatch patch) {
        return data(projects.update(id, patch));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        projects.delete(id);
        return data(Map.of("deleted", id));
    }
}
