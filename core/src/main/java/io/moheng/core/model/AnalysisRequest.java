package io.moheng.core.model;

import java.util.List;

public record AnalysisRequest(
        String repoPath,
        String base,
        String head,
        HeadMode headMode,
        Options options
) {

    public enum HeadMode { AUTO, REV_COMMIT, WORKTREE }

    public AnalysisRequest {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        base = base == null || base.isBlank() ? "HEAD~1" : base;
        head = head == null || head.isBlank() ? "WORKTREE" : head;
        headMode = headMode == null ? HeadMode.AUTO : headMode;
        options = options == null ? Options.defaults() : options.normalized();
    }

    public record Options(
            int maxDepth,
            String apiPrefix,
            int javaVersion,
            List<String> includePackages,
            List<String> excludePackages,
            List<String> sourceRoots,
            boolean includeTests,
            boolean highConfidenceOnly
    ) {

        public Options {
            maxDepth = maxDepth <= 0 ? 5 : Math.min(maxDepth, 10);
            apiPrefix = apiPrefix == null ? "" : apiPrefix.trim();
            javaVersion = javaVersion <= 0 ? 21 : javaVersion;
            includePackages = includePackages == null ? List.of() : List.copyOf(includePackages);
            excludePackages = excludePackages == null ? List.of() : List.copyOf(excludePackages);
            sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
        }

        public static Options defaults() {
            return new Options(5, "", 21, List.of(), List.of(), List.of(), false, false);
        }

        public Options normalized() {
            return new Options(maxDepth, apiPrefix, javaVersion, includePackages, excludePackages,
                    sourceRoots, includeTests, highConfidenceOnly);
        }
    }

    public static AnalysisRequest of(String repoPath, String base, String head) {
        return new AnalysisRequest(repoPath, base, head, HeadMode.AUTO, Options.defaults());
    }
}
