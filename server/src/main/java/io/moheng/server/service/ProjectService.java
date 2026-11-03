package io.moheng.server.service;

import io.moheng.server.api.ApiException;
import io.moheng.server.api.ProjectPatch;
import io.moheng.server.api.ProjectRequest;
import io.moheng.server.repo.Project;
import io.moheng.server.repo.ProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final WorkspaceService workspace;

    public ProjectService(ProjectRepository projects, WorkspaceService workspace) {
        this.projects = projects;
        this.workspace = workspace;
    }

    public List<Project> list() {
        return projects.findAll();
    }

    public Project require(String id) {
        return projects.find(id).orElseThrow(() -> ApiException.notFound("项目不存在: " + id));
    }

    public Project create(ProjectRequest request) {
        Path repo = workspace.requireRepository(request.repoPath());
        projects.findByRepoPath(repo.toString()).ifPresent(existing -> {
            throw ApiException.conflict("REPO_ALREADY_REGISTERED", "该仓库已注册: " + repo);
        });
        String now = Instant.now().toString();
        String name = blank(request.name()) ? repo.getFileName().toString() : request.name().trim();
        Project project = new Project(UUID.randomUUID().toString(), name, repo.toString(),
                normalizePrefix(request.apiPrefix()), safeList(request.includePackages()),
                safeList(request.excludePackages()), safeList(request.sourceRoots()),
                blankToNull(request.defaultBase()), now, now);
        projects.insert(project);
        return project;
    }

    public Project update(String id, ProjectPatch patch) {
        Project current = require(id);
        String repoPath = current.repoPath();
        if (patch.repoPath() != null) {
            String updatedRepoPath = workspace.requireRepository(patch.repoPath()).toString();
            if (!updatedRepoPath.equals(current.repoPath())) {
                projects.findByRepoPath(updatedRepoPath).ifPresent(existing -> {
                    if (!existing.id().equals(id)) {
                        throw ApiException.conflict("REPO_ALREADY_REGISTERED", "该仓库已注册: " + updatedRepoPath);
                    }
                });
            }
            repoPath = updatedRepoPath;
        }
        Project updated = new Project(current.id(),
                patch.name() == null || blank(patch.name()) ? current.name() : patch.name().trim(),
                repoPath,
                patch.apiPrefix() == null ? current.apiPrefix() : normalizePrefix(patch.apiPrefix()),
                patch.includePackages() == null ? current.includePackages() : safeList(patch.includePackages()),
                patch.excludePackages() == null ? current.excludePackages() : safeList(patch.excludePackages()),
                patch.sourceRoots() == null ? current.sourceRoots() : safeList(patch.sourceRoots()),
                patch.defaultBase() == null ? current.defaultBase() : blankToNull(patch.defaultBase()),
                current.createdAt(), Instant.now().toString());
        projects.update(updated);
        return updated;
    }

    public void delete(String id) {
        require(id);
        projects.delete(id);
    }

    private static String normalizePrefix(String prefix) {
        if (blank(prefix)) {
            return "";
        }
        String value = prefix.trim().replaceAll("/+", "/");
        return value.startsWith("/") ? value : "/" + value;
    }

    private static List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
