package io.moheng.core.model;

import java.util.List;

public record FileChange(
        String path,
        String oldPath,
        Status status,
        List<LineRange> ranges,
        boolean binary,
        boolean javaFile
) {

    public enum Status { ADDED, MODIFIED, DELETED, RENAMED }

    public record LineRange(int start, int end) {
        public LineRange {
            if (start > end) {
                int tmp = start;
                start = end;
                end = tmp;
            }
        }
    }

    public FileChange {
        ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }

    public String basePath() {
        return oldPath == null || oldPath.isBlank() ? path : oldPath;
    }
}
