package io.moheng.analyzer.java;

import io.moheng.core.AnalysisException;
import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.model.ImpactGraph;
import io.moheng.core.model.Warning;
import io.moheng.core.report.ProgressSink;
import io.moheng.core.source.AnalyzedProject;
import io.moheng.core.source.ProjectAnalyzer;
import io.moheng.core.source.SourceSet;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaProjectAnalyzer implements ProjectAnalyzer {

    @Override
    public AnalyzedProject analyze(SourceSet sources, AnalysisRequest.Options options,
                                   Mode mode, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.NOOP : progress;
        if (sources == null || sources.isEmpty()) {
            sink.progress("PARSE", 100, "没有可解析的 Java 源码");
            return AnalyzedProject.empty();
        }
        long start = System.currentTimeMillis();
        try {
            Launcher launcher = new Launcher();
            Environment environment = launcher.getEnvironment();
            environment.setNoClasspath(true);
            environment.setComplianceLevel(options.javaVersion());
            environment.setIgnoreSyntaxErrors(true);
            environment.setCommentEnabled(false);
            environment.setLevel("ERROR");
            for (String path : sources.sortedPaths()) {
                launcher.addInputResource(new VirtualFile(sources.content(path), path));
            }
            sink.progress("PARSE", mode == Mode.HEAD ? 40 : 20,
                    "解析 " + sources.size() + " 个 Java 文件");
            launcher.buildModel();
            long parseMillis = System.currentTimeMillis() - start;
            CtModel model = launcher.getModel();
            Collection<CtType<?>> modelTypes = model.getAllTypes();

            SpoonContext ctx = new SpoonContext(sources, options, modelTypes);
            SpringScanner.Result spring = new SpringScanner(ctx).scan();
            SymbolExtractor.Result symbols = SymbolExtractor.extract(ctx, spring);

            List<Warning> warnings = new ArrayList<>(spring.warnings());
            ImpactGraph graph = ImpactGraph.empty();
            Map<String, Object> graphStats = Map.of();
            if (mode == Mode.HEAD) {
                LombokIndex lombok = new LombokIndex(ctx);
                CallGraphBuilder.Result built = new CallGraphBuilder(ctx, symbols, spring, lombok).build();
                graph = built.graph();
                warnings.addAll(built.warnings());
                graphStats = built.stats();
            }

            int errorCount = environment.getErrorCount();
            if (errorCount > 0) {
                warnings.add(new Warning("PARSE_ERRORS",
                        "Spoon 解析报告错误，符号与调用图可能不完整",
                        Map.of("errors", errorCount)));
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("mode", mode.name());
            stats.put("parseMillis", parseMillis);
            stats.put("totalMillis", System.currentTimeMillis() - start);
            stats.put("sourceFiles", sources.size());
            stats.put("modelTypes", ctx.types().size());
            stats.put("projectTypes", ctx.types().size());
            stats.put("symbols", symbols.symbols().size());
            stats.put("apis", spring.apis().size());
            stats.put("parseErrors", errorCount);
            stats.putAll(spring.stats());
            stats.putAll(graphStats);

            sink.progress("PARSE_DONE", mode == Mode.HEAD ? 60 : 30,
                    "解析完成：" + ctx.types().size() + " 个类型");
            return new AnalyzedProject(symbols.symbols(), spring.apis(), graph,
                    spring.apiSourcePaths(), warnings, List.of(), stats);
        } catch (AnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            throw new AnalysisException("ANALYSIS_FAILED", "Java 源码分析失败: " + detail, e);
        }
    }
}
