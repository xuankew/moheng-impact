package io.moheng.core.source;

import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.report.ProgressSink;

public interface ProjectAnalyzer {

    AnalyzedProject analyze(SourceSet sources, AnalysisRequest.Options options, Mode mode, ProgressSink progress);

    enum Mode { HEAD, BASE }
}
