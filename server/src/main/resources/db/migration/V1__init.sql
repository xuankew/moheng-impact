CREATE TABLE project (
  id               TEXT PRIMARY KEY,
  name             TEXT NOT NULL,
  repo_path        TEXT NOT NULL UNIQUE,
  api_prefix       TEXT NOT NULL DEFAULT '',
  include_packages TEXT NOT NULL DEFAULT '[]',
  exclude_packages TEXT NOT NULL DEFAULT '[]',
  source_roots     TEXT NOT NULL DEFAULT '[]',
  default_base     TEXT,
  created_at       TEXT NOT NULL,
  updated_at       TEXT NOT NULL
);

CREATE TABLE analysis_run (
  id            TEXT PRIMARY KEY,
  project_id    TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  base_rev      TEXT NOT NULL,
  head_rev      TEXT NOT NULL,
  head_mode     TEXT NOT NULL,
  status        TEXT NOT NULL,
  phase         TEXT,
  progress      INTEGER NOT NULL DEFAULT 0,
  risk_level    TEXT,
  risk_score    INTEGER,
  safe_mode     INTEGER NOT NULL DEFAULT 0,
  summary_json  TEXT,
  result_json   TEXT,
  error_code    TEXT,
  error_message TEXT,
  warnings_json TEXT,
  started_at    TEXT NOT NULL,
  finished_at   TEXT
);

CREATE TABLE api_impact (
  run_id       TEXT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
  api_id       TEXT NOT NULL,
  api_key      TEXT NOT NULL,
  verb         TEXT NOT NULL,
  path         TEXT NOT NULL,
  controller   TEXT NOT NULL,
  handler      TEXT NOT NULL,
  risk_level   TEXT NOT NULL,
  risk_score   INTEGER NOT NULL,
  confidence   REAL NOT NULL,
  depth        INTEGER NOT NULL,
  reasons_json TEXT NOT NULL,
  PRIMARY KEY (run_id, api_id)
);

CREATE TABLE change (
  run_id      TEXT NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
  file_path   TEXT NOT NULL,
  change_type TEXT NOT NULL,
  symbol_key  TEXT,
  symbol_kind TEXT,
  detail_json TEXT
);

CREATE INDEX idx_run_project_started ON analysis_run(project_id, started_at DESC);
CREATE INDEX idx_api_impact_run_risk ON api_impact(run_id, risk_score DESC);
CREATE INDEX idx_change_run_file ON change(run_id, file_path);
