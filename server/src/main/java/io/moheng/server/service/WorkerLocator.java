package io.moheng.server.service;

import io.moheng.server.config.MohengProperties;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class WorkerLocator {

    private final MohengProperties properties;

    public WorkerLocator(MohengProperties properties) {
        this.properties = properties;
    }

    public Optional<Path> locate() {
        String configured = properties.workerJar();
        if (configured != null) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("MOHENG_WORKER_JAR 指向的文件不存在: " + path);
            }
            return Optional.of(path);
        }
        String env = System.getenv("MOHENG_WORKER_JAR");
        if (env != null && !env.isBlank()) {
            Path path = Path.of(env).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        Path location = serverLocation();
        if (location != null) {
            Path lib = location.resolve("lib").resolve("moheng-worker.jar");
            if (Files.isRegularFile(lib)) {
                return Optional.of(lib);
            }
            Path sibling = location.resolve("moheng-worker.jar");
            if (Files.isRegularFile(sibling)) {
                return Optional.of(sibling);
            }
        }
        Path appLib = Path.of("/app/lib/moheng-worker.jar");
        if (Files.isRegularFile(appLib)) {
            return Optional.of(appLib);
        }
        Path fromCwd = searchUp(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            return Optional.of(fromCwd);
        }
        return location == null ? Optional.empty() : Optional.ofNullable(searchUp(location));
    }

    private static Path searchUp(Path start) {
        Path dir = start;
        while (dir != null) {
            Path candidate = dir.resolve("worker").resolve("target").resolve("moheng-worker.jar");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static Path serverLocation() {
        try {
            Path location = Path.of(WorkerLocator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }
}
