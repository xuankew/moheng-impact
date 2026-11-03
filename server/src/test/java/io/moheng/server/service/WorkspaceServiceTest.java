package io.moheng.server.service;

import io.moheng.server.api.ApiException;
import io.moheng.server.config.MohengProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class WorkspaceServiceTest {

    @TempDir
    Path temp;

    @Test
    void acceptsAbsoluteRepositoryOutsideWorkspace() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path repo = Files.createDirectories(temp.resolve("other/repo"));
        Files.createDirectories(repo.resolve(".git"));

        WorkspaceService service = service(workspace);

        assertThat(service.requireRepository(repo.toString())).isEqualTo(repo.toRealPath());
    }

    @Test
    void resolvesRelativeRepositoryInsideWorkspace() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path repo = Files.createDirectories(workspace.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));

        WorkspaceService service = service(workspace);

        assertThat(service.requireRepository("repo")).isEqualTo(repo.toRealPath());
    }

    @Test
    void rejectsRelativePathEscapingWorkspace() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(outside.resolve(".git"));

        WorkspaceService service = service(workspace);

        try {
            service.requireRepository("../outside");
            fail("expected PATH_OUTSIDE_WORKSPACE");
        } catch (ApiException e) {
            assertThat(e.code()).isEqualTo("PATH_OUTSIDE_WORKSPACE");
        }
    }

    @Test
    void rejectsDirectoryWithoutGitMetadata() throws IOException {
        Path workspace = Files.createDirectories(temp.resolve("workspace"));
        Path repo = Files.createDirectories(workspace.resolve("plain"));

        WorkspaceService service = service(workspace);

        try {
            service.requireRepository(repo.toString());
            fail("expected NOT_A_GIT_REPOSITORY");
        } catch (ApiException e) {
            assertThat(e.code()).isEqualTo("NOT_A_GIT_REPOSITORY");
        }
    }

    private WorkspaceService service(Path workspace) {
        MohengProperties properties = new MohengProperties(workspace.toString(),
                temp.resolve("data").toString(), null, null, 600, null);
        return new WorkspaceService(properties);
    }
}
