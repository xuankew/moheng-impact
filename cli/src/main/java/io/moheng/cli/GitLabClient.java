package io.moheng.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.moheng.core.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class GitLabClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 20;

    private final HttpClient http;
    private final String apiUrl;
    private final String token;

    GitLabClient(String apiUrl, String token) {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new GitLabException("缺少 GitLab API 地址（CI_API_V4_URL）");
        }
        if (token == null || token.isBlank()) {
            throw new GitLabException("缺少 GitLab 令牌，请设置 MOHENG_GITLAB_TOKEN");
        }
        String trimmed = apiUrl.trim();
        this.apiUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        this.token = token.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    NoteResult upsertMergeRequestNote(String projectId, String iid, String body) {
        if (projectId == null || projectId.isBlank()) {
            throw new GitLabException("缺少 GitLab 项目 ID（CI_PROJECT_ID）");
        }
        if (iid == null || !iid.matches("[0-9]+")) {
            throw new GitLabException("缺少有效的 Merge Request IID（CI_MERGE_REQUEST_IID）");
        }
        if (body == null || body.isBlank()) {
            throw new GitLabException("MR 评论内容为空");
        }
        String notesUrl = apiUrl + "/projects/" + encodeSegment(projectId.trim())
                + "/merge_requests/" + iid + "/notes";
        Long existing = findNoteId(notesUrl);
        if (existing != null) {
            send(request(URI.create(notesUrl + "/" + existing)).PUT(formBody(body)));
            return new NoteResult(existing, false);
        }
        HttpResponse<String> response = send(request(URI.create(notesUrl)).POST(formBody(body)));
        JsonNode created = parseObject(response.body());
        return new NoteResult(created.path("id").asLong(), true);
    }

    private Long findNoteId(String notesUrl) {
        String page = "1";
        for (int i = 0; i < MAX_PAGES; i++) {
            URI uri = URI.create(notesUrl + "?per_page=" + PER_PAGE + "&page=" + page);
            HttpResponse<String> response = send(request(uri).GET());
            JsonNode notes = parseArray(response.body());
            for (JsonNode note : notes) {
                if (note.path("body").asText("").contains(MarkdownReport.MARKER)) {
                    long id = note.path("id").asLong();
                    if (id > 0) {
                        return id;
                    }
                }
            }
            String next = response.headers().firstValue("X-Next-Page").orElse("").trim();
            if (next.isEmpty() || next.equals(page)) {
                return null;
            }
            page = next;
        }
        return null;
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("PRIVATE-TOKEN", token)
                .header("Accept", "application/json");
    }

    private static HttpRequest.BodyPublisher formBody(String body) {
        return HttpRequest.BodyPublishers.ofString(
                "body=" + URLEncoder.encode(body, StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        HttpRequest request = builder
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw statusError(status, response.body());
            }
            return response;
        } catch (IOException e) {
            throw new GitLabException("无法连接 GitLab API: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabException("GitLab API 请求被中断");
        }
    }

    private GitLabException statusError(int status, String body) {
        String detail = snippet(body);
        return switch (status) {
            case 401, 403 -> new GitLabException("GitLab 鉴权失败（HTTP " + status
                    + "），请确认令牌具有 api 权限且未过期");
            case 404 -> new GitLabException("GitLab 未找到项目或 Merge Request（HTTP 404）");
            default -> new GitLabException("GitLab 请求失败（HTTP " + status + "）"
                    + (detail.isEmpty() ? "" : ": " + detail));
        };
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String cleaned = body.replaceAll("\\s+", " ").trim().replace(token, "***");
        return cleaned.length() <= 200 ? cleaned : cleaned.substring(0, 200) + "…";
    }

    private static JsonNode parseArray(String body) {
        try {
            JsonNode node = Json.mapper().readTree(body);
            if (node == null || !node.isArray()) {
                throw new GitLabException("GitLab 返回了非预期的响应");
            }
            return node;
        } catch (IOException e) {
            throw new GitLabException("无法解析 GitLab 响应: " + e.getMessage());
        }
    }

    private static JsonNode parseObject(String body) {
        try {
            JsonNode node = Json.mapper().readTree(body);
            if (node == null || !node.isObject()) {
                throw new GitLabException("GitLab 返回了非预期的响应");
            }
            return node;
        } catch (IOException e) {
            throw new GitLabException("无法解析 GitLab 响应: " + e.getMessage());
        }
    }

    private static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    record NoteResult(long noteId, boolean created) {
    }

    static final class GitLabException extends RuntimeException {
        GitLabException(String message) {
            super(message);
        }
    }
}
