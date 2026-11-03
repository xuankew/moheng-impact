package io.moheng.server.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import io.moheng.core.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;

    private final RowMapper<Project> mapper = (rs, rowNum) -> new Project(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("repo_path"),
            rs.getString("api_prefix"),
            readList(rs.getString("include_packages")),
            readList(rs.getString("exclude_packages")),
            readList(rs.getString("source_roots")),
            rs.getString("default_base"),
            rs.getString("created_at"),
            rs.getString("updated_at"));

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Project> findAll() {
        return jdbc.query("SELECT * FROM project ORDER BY created_at", mapper);
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM project", Long.class);
        return value == null ? 0 : value;
    }

    public Optional<Project> find(String id) {
        List<Project> rows = jdbc.query("SELECT * FROM project WHERE id = ?", mapper, id);
        return rows.stream().findFirst();
    }

    public Optional<Project> findByRepoPath(String repoPath) {
        List<Project> rows = jdbc.query("SELECT * FROM project WHERE repo_path = ?", mapper, repoPath);
        return rows.stream().findFirst();
    }

    public void insert(Project project) {
        jdbc.update("""
                        INSERT INTO project (id, name, repo_path, api_prefix, include_packages,
                                             exclude_packages, source_roots, default_base,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                project.id(), project.name(), project.repoPath(), project.apiPrefix(),
                Json.write(project.includePackages()), Json.write(project.excludePackages()),
                Json.write(project.sourceRoots()), project.defaultBase(),
                project.createdAt(), project.updatedAt());
    }

    public void update(Project project) {
        jdbc.update("""
                        UPDATE project SET name = ?, repo_path = ?, api_prefix = ?, include_packages = ?,
                                           exclude_packages = ?, source_roots = ?, default_base = ?,
                                           updated_at = ?
                        WHERE id = ?
                        """,
                project.name(), project.repoPath(), project.apiPrefix(),
                Json.write(project.includePackages()), Json.write(project.excludePackages()),
                Json.write(project.sourceRoots()), project.defaultBase(), project.updatedAt(),
                project.id());
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM project WHERE id = ?", id);
    }

    private static List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = Json.mapper().readValue(json, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception e) {
            return List.of();
        }
    }
}
