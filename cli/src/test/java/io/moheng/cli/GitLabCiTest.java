package io.moheng.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabCiTest {

    @Test
    void detectsMergeRequestContext() {
        GitLabCi ci = GitLabCi.detect(Map.of(
                "CI_API_V4_URL", "https://gitlab.example.com/api/v4",
                "CI_PROJECT_ID", "123",
                "CI_MERGE_REQUEST_IID", "7",
                "CI_MERGE_REQUEST_DIFF_BASE_SHA", "1111111111111111111111111111111111111111",
                "CI_COMMIT_SHA", "2222222222222222222222222222222222222222",
                "CI_PROJECT_DIR", "/builds/group/repo",
                "MOHENG_GITLAB_TOKEN", "secret-token"));

        assertThat(ci.hasMergeRequest()).isTrue();
        assertThat(ci.apiUrl()).isEqualTo("https://gitlab.example.com/api/v4");
        assertThat(ci.projectId()).isEqualTo("123");
        assertThat(ci.mergeRequestIid()).isEqualTo("7");
        assertThat(ci.base()).isEqualTo("1111111111111111111111111111111111111111");
        assertThat(ci.head()).isEqualTo("2222222222222222222222222222222222222222");
        assertThat(ci.repo()).isEqualTo(Path.of("/builds/group/repo"));
        assertThat(ci.token()).isEqualTo("secret-token");
    }

    @Test
    void fallsBackToCommitBeforeShaButRejectsZeroSha() {
        GitLabCi branch = GitLabCi.detect(Map.of(
                "CI_COMMIT_BEFORE_SHA", "abcdefabcdefabcdefabcdefabcdefabcdefabcd",
                "CI_COMMIT_SHA", "1234123412341234123412341234123412341234"));
        assertThat(branch.base()).isEqualTo("abcdefabcdefabcdefabcdefabcdefabcdefabcd");
        assertThat(branch.hasMergeRequest()).isFalse();

        GitLabCi firstPush = GitLabCi.detect(Map.of(
                "CI_COMMIT_BEFORE_SHA", "0000000000000000000000000000000000000000",
                "CI_COMMIT_SHA", "1234123412341234123412341234123412341234"));
        assertThat(firstPush.base()).isNull();
    }

    @Test
    void prefersMohangTokenOverGenericToken() {
        GitLabCi ci = GitLabCi.detect(Map.of(
                "GITLAB_TOKEN", "generic",
                "MOHENG_GITLAB_TOKEN", "dedicated"));
        assertThat(ci.token()).isEqualTo("dedicated");
    }

    @Test
    void toStringDoesNotLeakToken() {
        GitLabCi ci = GitLabCi.detect(Map.of(
                "CI_API_V4_URL", "https://gitlab.example.com/api/v4",
                "CI_PROJECT_ID", "123",
                "CI_MERGE_REQUEST_IID", "7",
                "MOHENG_GITLAB_TOKEN", "top-secret"));
        assertThat(ci.toString()).doesNotContain("top-secret");
    }
}
