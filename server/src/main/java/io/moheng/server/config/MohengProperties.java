package io.moheng.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "moheng")
public record MohengProperties(
        String workspaceRoot,
        String dataDir,
        String workerJar,
        String workerHeap,
        Integer analysisTimeoutSeconds,
        String demoRepo
) {

    public MohengProperties {
        workspaceRoot = blankToNull(workspaceRoot);
        dataDir = blankToNull(dataDir);
        workerJar = blankToNull(workerJar);
        workerHeap = blankToNull(workerHeap);
        analysisTimeoutSeconds = analysisTimeoutSeconds == null || analysisTimeoutSeconds <= 0
                ? 600 : analysisTimeoutSeconds;
        demoRepo = blankToNull(demoRepo);
    }

    public Path workspace() {
        String configured = workspaceRoot == null ? System.getProperty("user.dir") : workspaceRoot;
        return Path.of(configured).toAbsolutePath().normalize();
    }

    public Path dataDirectory() {
        String configured = dataDir == null ? "data" : dataDir;
        return Path.of(configured).toAbsolutePath().normalize();
    }

    public Path temporaryDirectory() {
        return dataDirectory().resolve("tmp");
    }

    public String heap() {
        return workerHeap == null ? "1g" : workerHeap;
    }

    public int timeoutSeconds() {
        return analysisTimeoutSeconds;
    }

    public Path demoRepository() {
        if (demoRepo == null) {
            return null;
        }
        Path path = Path.of(demoRepo).toAbsolutePath().normalize();
        return Files.isDirectory(path.resolve(".git")) ? path : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
