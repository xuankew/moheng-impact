package io.moheng.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLabClientTest {

    private static final String TOKEN = "glpat-test-token";

    private HttpServer server;
    private String apiUrl;
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v4";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsNoteWhenNoMarkerExists() {
        route(exchange -> {
            Request request = requests.get(requests.size() - 1);
            if ("GET".equals(request.method())) {
                respond(exchange, 200, "[{\"id\":7,\"body\":\"其他评论\"}]");
            } else {
                respond(exchange, 201, "{\"id\":99}");
            }
        });

        GitLabClient.NoteResult result = new GitLabClient(apiUrl, TOKEN)
                .upsertMergeRequestNote("123", "5", "<!-- moheng-impact -->\n报告");

        assertThat(result.created()).isTrue();
        assertThat(result.noteId()).isEqualTo(99);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path()).isEqualTo("/api/v4/projects/123/merge_requests/5/notes");
        assertThat(requests.get(0).query()).isEqualTo("per_page=100&page=1");
        assertThat(requests.get(0).token()).isEqualTo(TOKEN);
        assertThat(requests.get(1).method()).isEqualTo("POST");
        assertThat(requests.get(1).token()).isEqualTo(TOKEN);
        assertThat(decodeBody(requests.get(1).body())).isEqualTo("<!-- moheng-impact -->\n报告");
    }

    @Test
    void updatesExistingNoteFoundOnLaterPage() {
        route(exchange -> {
            Request request = requests.get(requests.size() - 1);
            if ("GET".equals(request.method()) && request.query().endsWith("page=1")) {
                respond(exchange, 200, "[{\"id\":3,\"body\":\"无标记评论\"}]", "2");
            } else if ("GET".equals(request.method())) {
                respond(exchange, 200, "[{\"id\":42,\"body\":\"前缀 <!-- moheng-impact --> 后缀\"}]");
            } else {
                respond(exchange, 200, "{\"id\":42}");
            }
        });

        GitLabClient.NoteResult result = new GitLabClient(apiUrl, TOKEN)
                .upsertMergeRequestNote("group/repo", "5", "<!-- moheng-impact -->\n更新");

        assertThat(result.created()).isFalse();
        assertThat(result.noteId()).isEqualTo(42);
        assertThat(requests).hasSize(3);
        assertThat(requests.get(1).query()).isEqualTo("per_page=100&page=2");
        assertThat(requests.get(2).method()).isEqualTo("PUT");
        assertThat(requests.get(2).path()).isEqualTo("/api/v4/projects/group%2Frepo/merge_requests/5/notes/42");
        assertThat(decodeBody(requests.get(2).body())).isEqualTo("<!-- moheng-impact -->\n更新");
    }

    @Test
    void reportsAuthFailureWithoutLeakingToken() {
        route(exchange -> respond(exchange, 401, "{\"message\":\"401 Unauthorized " + TOKEN + "\"}"));

        assertThatThrownBy(() -> new GitLabClient(apiUrl, TOKEN)
                .upsertMergeRequestNote("123", "5", "<!-- moheng-impact -->"))
                .isInstanceOf(GitLabClient.GitLabException.class)
                .hasMessageContaining("鉴权失败")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void rejectsInvalidMergeRequestIidBeforeRequesting() {
        route(exchange -> respond(exchange, 200, "[]"));

        assertThatThrownBy(() -> new GitLabClient(apiUrl, TOKEN)
                .upsertMergeRequestNote("123", "../5", "body"))
                .isInstanceOf(GitLabClient.GitLabException.class)
                .hasMessageContaining("Merge Request IID");
        assertThat(requests).isEmpty();
    }

    @Test
    void requiresToken() {
        assertThatThrownBy(() -> new GitLabClient(apiUrl, " "))
                .isInstanceOf(GitLabClient.GitLabException.class)
                .hasMessageContaining("令牌");
    }

    private void route(Handler handler) {
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"),
                    new String(body, StandardCharsets.UTF_8)));
            handler.handle(exchange);
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body, null);
    }

    private static void respond(HttpExchange exchange, int status, String body, String nextPage) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (nextPage != null) {
            exchange.getResponseHeaders().add("X-Next-Page", nextPage);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String decodeBody(String body) {
        assertThat(body).startsWith("body=");
        return URLDecoder.decode(body.substring("body=".length()), StandardCharsets.UTF_8);
    }

    private record Request(String method, String path, String query, String token, String body) {
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
