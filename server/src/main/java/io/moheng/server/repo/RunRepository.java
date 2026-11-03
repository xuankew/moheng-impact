package io.moheng.server.repo;

import io.moheng.core.json.Json;
import io.moheng.core.model.AnalysisResult;
import io.moheng.core.model.ApiImpact;
import io.moheng.core.model.FileChange;
import io.moheng.core.model.SymbolChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RunRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<AnalysisRun> mapper = (rs, rowNum) -> {
        int riskScore = rs.getInt("risk_score");
        boolean riskScoreNull = rs.wasNull();
        return new AnalysisRun(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("base_rev"),
                rs.getString("head_rev"),
                rs.getString("head_mode"),
                rs.getString("status"),
                rs.getString("phase"),
                rs.getInt("progress"),
                rs.getString("risk_level"),
                riskScoreNull ? null : riskScore,
                rs.getInt("safe_mode") != 0,
                rs.getString("summary_json"),
                rs.getString("result_json"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getString("warnings_json"),
                rs.getString("started_at"),
                rs.getString("finished_at"));
    };

    public RunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AnalysisRun run) {
        jdbc.update("""
                        INSERT INTO analysis_run (id, project_id, base_rev, head_rev, head_mode,
                                                  status, phase, progress, started_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                run.id(), run.projectId(), run.baseRev(), run.headRev(), run.headMode(),
                run.status(), run.phase(), run.progress(), run.startedAt());
    }

    public void markRunning(String id) {
        jdbc.update("UPDATE analysis_run SET status = 'RUNNING', phase = 'STARTING', progress = 1 WHERE id = ?", id);
    }

    public void updateProgress(String id, String phase, int progress) {
        jdbc.update("UPDATE analysis_run SET phase = ?, progress = ? WHERE id = ? AND status = 'RUNNING'",
                phase, Math.max(0, Math.min(progress, 99)), id);
    }

    @Transactional
    public void markSucceeded(String id, AnalysisResult result) {
        jdbc.update("""
                        UPDATE analysis_run
                        SET status = 'SUCCEEDED', phase = 'DONE', progress = 100,
                            head_rev = ?, head_mode = ?, risk_level = ?, risk_score = ?, safe_mode = ?,
                            summary_json = ?, result_json = ?, warnings_json = ?,
                            error_code = NULL, error_message = NULL, finished_at = ?
                        WHERE id = ?
                        """,
                result.analysis() == null ? null : result.analysis().head(),
                result.analysis() == null ? null : result.analysis().headMode(),
                result.summary().riskLevel(),
                result.summary().riskScore(),
                result.summary().safeMode() ? 1 : 0,
                Json.write(result.summary()),
                Json.write(result),
                Json.write(result.warnings()),
                result.analysis() == null ? null : result.analysis().finishedAt(),
                id);
        replaceImpacts(id, result.apiImpacts());
        replaceChanges(id, result);
    }

    public void markFailed(String id, String code, String message, String warningsJson, String finishedAt) {
        jdbc.update("""
                        UPDATE analysis_run
                        SET status = 'FAILED', error_code = ?, error_message = ?, warnings_json = ?,
                            finished_at = ?
                        WHERE id = ?
                        """,
                code, limit(message, 2_000), warningsJson, finishedAt, id);
    }

    public void markCancelled(String id, String finishedAt) {
        jdbc.update("""
                        UPDATE analysis_run
                        SET status = 'CANCELLED', error_code = 'CANCELLED', error_message = '分析已取消',
                            finished_at = ?
                        WHERE id = ?
                        """,
                finishedAt, id);
    }

    public void failInterrupted() {
        jdbc.update("""
                        UPDATE analysis_run
                        SET status = 'FAILED', error_code = 'SERVER_RESTARTED', error_message = '服务重启，分析未完成',
                            finished_at = COALESCE(finished_at, started_at)
                        WHERE status IN ('QUEUED', 'RUNNING')
                        """);
    }

    public Optional<AnalysisRun> find(String id) {
        List<AnalysisRun> rows = jdbc.query("SELECT * FROM analysis_run WHERE id = ?", mapper, id);
        return rows.stream().findFirst();
    }

    public List<AnalysisRun> listByProject(String projectId, int limit) {
        return jdbc.query("""
                        SELECT * FROM analysis_run WHERE project_id = ?
                        ORDER BY started_at DESC LIMIT ?
                        """,
                mapper, projectId, clampLimit(limit));
    }

    public List<AnalysisRun> listRecent(int limit) {
        return jdbc.query("""
                        SELECT * FROM analysis_run ORDER BY started_at DESC LIMIT ?
                        """,
                mapper, clampLimit(limit));
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM analysis_run WHERE id = ?", id);
    }

    public long countApiImpacts(String runId, String risk, String query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM api_impact WHERE run_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(runId);
        appendApiFilters(sql, args, risk, query);
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public List<Map<String, Object>> pageApiImpacts(String runId, String risk, String query,
                                                    int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT api_id, api_key, verb, path, controller, handler,
                       risk_level, risk_score, confidence, depth, reasons_json
                FROM api_impact WHERE run_id = ?
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(runId);
        appendApiFilters(sql, args, risk, query);
        sql.append(" ORDER BY risk_score DESC, depth ASC, path ASC LIMIT ? OFFSET ?");
        args.add(Math.max(1, Math.min(size, 100)));
        args.add((long) Math.max(0, page - 1) * Math.max(1, Math.min(size, 100)));
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getString("api_id"));
            item.put("key", rs.getString("api_key"));
            item.put("verb", rs.getString("verb"));
            item.put("path", rs.getString("path"));
            item.put("controller", rs.getString("controller"));
            item.put("handler", rs.getString("handler"));
            item.put("level", rs.getString("risk_level"));
            item.put("score", rs.getInt("risk_score"));
            item.put("confidence", rs.getDouble("confidence"));
            item.put("depth", rs.getInt("depth"));
            item.put("reasons", readJson(rs.getString("reasons_json")));
            return item;
        }, args.toArray());
    }

    @Transactional
    protected void replaceImpacts(String runId, List<ApiImpact> impacts) {
        jdbc.update("DELETE FROM api_impact WHERE run_id = ?", runId);
        for (ApiImpact impact : impacts) {
            jdbc.update("""
                            INSERT INTO api_impact (run_id, api_id, api_key, verb, path, controller, handler,
                                                    risk_level, risk_score, confidence, depth, reasons_json)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    runId, impact.api().id(), impact.api().key(), impact.api().verb(), impact.api().path(),
                    impact.api().controller(), impact.api().handlerKey(), impact.level(), impact.score(),
                    impact.confidence(), impact.depth(), Json.write(impact.reasons()));
        }
    }

    @Transactional
    protected void replaceChanges(String runId, AnalysisResult result) {
        jdbc.update("DELETE FROM change WHERE run_id = ?", runId);
        for (FileChange change : result.changes()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ranges", change.ranges());
            detail.put("binary", change.binary());
            detail.put("javaFile", change.javaFile());
            if (change.oldPath() != null) {
                detail.put("oldPath", change.oldPath());
            }
            jdbc.update("""
                            INSERT INTO change (run_id, file_path, change_type, symbol_key, symbol_kind, detail_json)
                            VALUES (?, ?, ?, NULL, 'FILE', ?)
                            """,
                    runId, change.path(), change.status().name(), Json.write(detail));
        }
        for (SymbolChange change : result.changedSymbols()) {
            jdbc.update("""
                            INSERT INTO change (run_id, file_path, change_type, symbol_key, symbol_kind, detail_json)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    runId, change.file(), change.changeType().name(), change.key().id(),
                    change.key().kind().name(), Json.write(change));
        }
    }

    private void appendApiFilters(StringBuilder sql, List<Object> args, String risk, String query) {
        if (risk != null && !risk.isBlank()) {
            sql.append(" AND risk_level = ?");
            args.add(risk.trim().toUpperCase());
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (api_key LIKE ? OR path LIKE ? OR controller LIKE ? OR handler LIKE ?)");
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    private static Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return Json.mapper().readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
