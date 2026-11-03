package io.moheng.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "serve", mixinStandardHelpOptions = true,
        description = "启动本地 Web 服务")
final class ServeCommand implements Callable<Integer> {

    @Option(names = "--port", paramLabel = "<port>", description = "监听端口，默认 8848")
    private int port = 8848;

    @Option(names = "--host", paramLabel = "<host>", description = "监听地址，默认 127.0.0.1")
    private String host = "127.0.0.1";

    @Option(names = "--workspace-root", paramLabel = "<path>", description = "允许访问的仓库根目录")
    private Path workspaceRoot;

    @Option(names = "--server-jar", paramLabel = "<file>", description = "指定 moheng-server.jar")
    private Path serverJarOption;

    @Override
    public Integer call() {
        Path jar = locateServerJar();
        if (jar == null) {
            throw new CliException(ExitCodes.USAGE,
                    "未找到 moheng-server.jar；请先执行 mvn package，或设置 MOHENG_SERVER_JAR");
        }
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        ProcessBuilder builder = new ProcessBuilder(List.of(
                java, "-jar", jar.toString(),
                "--server.port=" + port,
                "--server.address=" + host));
        if (workspaceRoot != null) {
            builder.environment().put("MOHENG_WORKSPACE_ROOT",
                    workspaceRoot.toAbsolutePath().normalize().toString());
        }
        builder.inheritIO();
        try {
            Process process = builder.start();
            return process.waitFor();
        } catch (IOException e) {
            throw new CliException(ExitCodes.WORKER_UNAVAILABLE, "无法启动 server: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException(ExitCodes.ANALYSIS_FAILED, "server 启动被中断", e);
        }
    }

    private Path locateServerJar() {
        if (serverJarOption != null) {
            Path candidate = serverJarOption.toAbsolutePath().normalize();
            if (!Files.isRegularFile(candidate)) {
                throw new CliException(ExitCodes.USAGE, "server jar 不存在: " + candidate);
            }
            return candidate;
        }
        String env = System.getenv("MOHENG_SERVER_JAR");
        if (env != null && !env.isBlank()) {
            Path candidate = Path.of(env).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            throw new CliException(ExitCodes.USAGE, "MOHENG_SERVER_JAR 指向的文件不存在: " + candidate);
        }
        Path sibling = sibling("moheng-server.jar");
        if (sibling != null) {
            return sibling;
        }
        Path fromCwd = searchUp(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            return fromCwd;
        }
        Path location = cliLocation();
        return location == null ? null : searchUp(location);
    }

    private static Path searchUp(Path start) {
        Path dir = start;
        while (dir != null) {
            Path candidate = dir.resolve("server").resolve("target").resolve("moheng-server.jar");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static Path cliLocation() {
        try {
            Path location = Path.of(ServeCommand.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    private static Path sibling(String name) {
        Path location = cliLocation();
        if (location == null) {
            return null;
        }
        Path candidate = location.resolve(name);
        return Files.isRegularFile(candidate) ? candidate : null;
    }
}
