package io.moheng.cli;

import java.nio.file.Path;
import java.util.Map;

record GitLabCi(
        String apiUrl,
        String projectId,
        String mergeRequestIid,
        String token,
        Path repo,
        String base,
        String head
) {

    static GitLabCi detect(Map<String, String> env) {
        String projectDir = blankToNull(env.get("CI_PROJECT_DIR"));
        String base = blankToNull(env.get("CI_MERGE_REQUEST_DIFF_BASE_SHA"));
        if (base == null) {
            base = usableCommit(env.get("CI_COMMIT_BEFORE_SHA"));
        }
        String head = firstNonBlank(
                env.get("CI_COMMIT_SHA"),
                env.get("CI_MERGE_REQUEST_SOURCE_BRANCH_SHA"));
        return new GitLabCi(
                blankToNull(env.get("CI_API_V4_URL")),
                blankToNull(env.get("CI_PROJECT_ID")),
                blankToNull(env.get("CI_MERGE_REQUEST_IID")),
                firstNonBlank(env.get("MOHENG_GITLAB_TOKEN"), env.get("GITLAB_TOKEN")),
                projectDir == null ? null : Path.of(projectDir),
                base,
                head);
    }

    boolean hasMergeRequest() {
        return apiUrl != null && projectId != null && mergeRequestIid != null;
    }

    @Override
    public String toString() {
        return "GitLabCi[projectId=" + projectId + ", mergeRequestIid=" + mergeRequestIid + "]";
    }

    private static String usableCommit(String sha) {
        if (sha == null || sha.isBlank() || sha.chars().allMatch(ch -> ch == '0')) {
            return null;
        }
        return sha;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
