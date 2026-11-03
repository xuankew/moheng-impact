package io.moheng.analyzer.java;

import io.moheng.core.model.AnalysisRequest;
import io.moheng.core.report.ProgressSink;
import io.moheng.core.source.AnalyzedProject;
import io.moheng.core.source.ProjectAnalyzer;
import io.moheng.core.source.SourceSet;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NestedTypeTest {

    @Test
    void indexesNestedTypesAndTheirMembers() {
        String source = """
                package com.example;

                public class Outer {
                    public enum Kind {
                        A(1),
                        B(2);

                        private final int code;

                        Kind(int code) {
                            this.code = code;
                        }
                    }

                    public static class Inner {
                        public void run() {
                        }
                    }
                }
                """;
        AnalyzedProject project = new JavaProjectAnalyzer().analyze(
                SourceSet.of(Map.of("src/main/java/com/example/Outer.java", source)),
                AnalysisRequest.Options.defaults(),
                ProjectAnalyzer.Mode.HEAD,
                ProgressSink.NOOP);

        assertThat(project.symbols()).containsKeys(
                "CLASS:com.example.Outer$Kind",
                "CLASS:com.example.Outer$Inner",
                "FIELD:com.example.Outer$Kind#A",
                "METHOD:com.example.Outer$Kind#<init>(int):void",
                "METHOD:com.example.Outer$Inner#run():void");
        assertThat(project.stats().get("modelTypes")).isEqualTo(3);
    }
}
