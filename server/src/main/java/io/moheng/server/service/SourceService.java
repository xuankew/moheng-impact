package io.moheng.server.service;

import io.moheng.core.git.GitRepository;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.server.api.ApiException;
import io.moheng.server.repo.AnalysisRun;
import io.moheng.server.repo.Project;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class SourceService {

    private static final int MAX_LINES = 200;

    private final WorkspaceService workspace;

    public SourceService(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    public String read(Project project, AnalysisRun run, String file, Integer start, Integer end) {
        if (file == null || file.isBlank()) {
            throw ApiException.invalid("file 不能为空");
        }
        String relative = file.replace('\\', '/');
        if (Path.of(relative).isAbsolute()) {
            throw ApiException.outsideWorkspace(file);
        }
        Path repo = workspace.requireRepository(project.repoPath());
        Path candidate = repo.resolve(relative).normalize();
        if (!candidate.startsWith(repo)) {
            throw ApiException.outsideWorkspace(file);
        }
        AnalysisRequest.HeadMode mode = headMode(run);
        String content;
        if (mode == AnalysisRequest.HeadMode.WORKTREE) {
            Path realRepo = real(repo);
            Path realFile = real(candidate);
            if (!realFile.startsWith(realRepo)) {
                throw ApiException.outsideWorkspace(file);
            }
            try {
                content = Files.readString(realFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw ApiException.notFound("文件不存在: " + relative);
            }
        } else {
            try (GitRepository git = GitRepository.open(repo)) {
                content = git.readFile(run.headRev(), relative, AnalysisRequest.HeadMode.REV_COMMIT);
            } catch (RuntimeException e) {
                throw ApiException.notFound("文件不存在: " + relative);
            }
        }
        List<String> lines = content.lines().toList();
        int total = lines.size();
        int from = start == null || start <= 0 ? 1 : Math.min(start, Math.max(1, total));
        int to = end == null || end <= 0 ? from + MAX_LINES - 1 : Math.min(end, total);
        if (to - from + 1 > MAX_LINES) {
            to = from + MAX_LINES - 1;
        }
        if (from > total) {
            from = Math.max(1, total);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i <= Math.min(to, total); i++) {
            builder.append(lines.get(i - 1)).append('\n');
        }
        return builder.toString();
    }

    private static AnalysisRequest.HeadMode headMode(AnalysisRun run) {
        try {
            return AnalysisRequest.HeadMode.valueOf(run.headMode());
        } catch (RuntimeException e) {
            return "WORKTREE".equals(run.headRev())
                    ? AnalysisRequest.HeadMode.WORKTREE : AnalysisRequest.HeadMode.REV_COMMIT;
        }
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
