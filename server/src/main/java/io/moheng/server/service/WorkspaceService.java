package io.moheng.server.service;

import io.moheng.server.api.ApiException;
import io.moheng.server.config.MohengProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceService {

    private final MohengProperties properties;

    public WorkspaceService(MohengProperties properties) {
        this.properties = properties;
    }

    public Path root() {
        return properties.workspace();
    }

    public Map<String, Object> listDirectories(String relative) {
        Path target = resolveInsideWorkspace(relative);
        Path canonicalRoot = realPath(root());
        if (!Files.isDirectory(target)) {
            throw ApiException.invalid("目录不存在: " + relative);
        }
        List<Map<String, Object>> directories = new ArrayList<>();
        try (var stream = Files.list(target)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .forEach(path -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", path.getFileName().toString());
                        item.put("path", canonicalRoot.relativize(path).toString().replace('\\', '/'));
                        item.put("git", Files.exists(path.resolve(".git")));
                        directories.add(item);
                    });
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR", "无法读取目录", Map.of("path", relative));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        String path = canonicalRoot.relativize(target).toString().replace('\\', '/');
        data.put("root", root().toString());
        data.put("path", path.isEmpty() ? "." : path);
        data.put("parent", target.equals(canonicalRoot) ? null
                : canonicalRoot.relativize(target.getParent()).toString().replace('\\', '/'));
        data.put("directories", directories);
        return data;
    }

    public Path resolveInsideWorkspace(String relative) {
        Path root = root();
        Path lexical = relative == null || relative.isBlank()
                ? root
                : root.resolve(relative).normalize();
        if (!lexical.startsWith(root)) {
            throw ApiException.outsideWorkspace(relative);
        }
        Path canonicalRoot = realPath(root);
        return canonicalRoot.resolve(root.relativize(lexical)).normalize();
    }

    public Path requireRepository(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw ApiException.invalid("repoPath 不能为空");
        }
        Path candidate = Path.of(repoPath);
        Path resolved = candidate.isAbsolute()
                ? candidate.normalize()
                : root().resolve(candidate).normalize();
        Path canonicalRoot = realPath(root());
        Path real = realPath(resolved);
        if (!real.startsWith(canonicalRoot)) {
            throw ApiException.outsideWorkspace(repoPath);
        }
        if (!Files.isDirectory(real)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "REPO_NOT_FOUND", "仓库目录不存在", Map.of("path", repoPath));
        }
        if (!Files.exists(real.resolve(".git"))) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "NOT_A_GIT_REPOSITORY", "不是 Git 仓库", Map.of("path", repoPath));
        }
        return real;
    }

    public String relativeWorkspacePath(Path path) {
        Path root = root();
        return path.startsWith(root)
                ? root.relativize(path).toString().replace('\\', '/')
                : path.toString();
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
