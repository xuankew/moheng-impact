package io.moheng.server.service;

import io.moheng.core.git.GitRepository;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.server.config.MohengProperties;
import io.moheng.server.repo.Project;
import io.moheng.server.repo.ProjectRepository;
import io.moheng.server.repo.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final MohengProperties properties;
    private final ProjectRepository projects;
    private final RunRepository runs;
    private final AnalysisRunner runner;

    public StartupRunner(MohengProperties properties, ProjectRepository projects,
                         RunRepository runs, AnalysisRunner runner) {
        this.properties = properties;
        this.projects = projects;
        this.runs = runs;
        this.runner = runner;
    }

    @Override
    public void run(ApplicationArguments args) {
        runs.failInterrupted();
        if (projects.count() > 0) {
            return;
        }
        Path demo = properties.demoRepository();
        if (demo == null || !hasParentCommit(demo)) {
            return;
        }
        String now = Instant.now().toString();
        Project project = new Project(UUID.randomUUID().toString(), "内置演示",
                demo.toString(), "", List.of(), List.of(), List.of(), "HEAD~1", now, now);
        projects.insert(project);
        runner.start(project, "HEAD~1", "HEAD", AnalysisRequest.HeadMode.REV_COMMIT,
                AnalysisRequest.Options.defaults());
        log.info("demo project registered: {}", demo.getFileName());
    }

    private static boolean hasParentCommit(Path repo) {
        try (GitRepository git = GitRepository.open(repo)) {
            git.resolveCommit("HEAD~1");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
