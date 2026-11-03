# 墨衡 · 见微（Impact）

本地优先的 **Java / Spring 变更影响分析** 工具。输入两个 Git commit，输出变更符号、受影响方法、受影响 REST API、调用链与风险等级，回答一个问题：

> 这次改动，应该测哪里？

V0.1 完全离线运行：源码不上传、不采集遥测、不依赖任何商业服务。分析仓库只读，结果保存在本地 SQLite 与 `.moheng/runs`。

## 能力范围

- Git diff → 变更文件与变更符号（类 / 方法 / 字段）
- Spoon 源码级调用图（`noClasspath`），支持调用、实现、继承、重写、类型引用
- 从变更符号反向遍历到 Spring REST Handler，再到对外 API
- 每个 API 保留最多 10 条影响路径，风险评分取最优路径
- 风险等级 `LOW / MEDIUM / HIGH / CRITICAL`，附带逐条风险原因与证据
- Safe Mode：改动范围过大或解析失败时主动抬高风险下限
- Web 报告、影响图（按 API / 深度 / 变更起点筛选）、源码上下文
- CLI 文本 / JSON 输出，支持风险阈值退出码
- 分析历史持久化（SQLite WAL + Flyway）
- 单容器部署，镜像内置可运行的演示仓库

## 快速开始

前置条件：JDK 21、Maven 3.9+、Node.js 22+、pnpm 11+。

```sh
# 1. 构建前端
pnpm --dir web install
pnpm --dir web build

# 2. 构建全部 Java 模块与可执行 jar
mvn -B package

# 3. 创建演示仓库（两个 commit）
sh demo/setup.sh /tmp/moheng-demo

# 4. 启动 Web 服务
MOHENG_WORKSPACE_ROOT=/tmp \
MOHENG_DEMO_REPO=/tmp/moheng-demo \
java -jar server/target/moheng-server.jar
```

打开 <http://127.0.0.1:8848>，用预置的演示项目执行一次 `HEAD~1 → HEAD` 分析。

也可以直接使用 CLI：

```sh
java -jar cli/target/moheng-cli.jar impact analyze \
  --repo /tmp/moheng-demo --base HEAD~1 --head HEAD --format json
```

worker jar 会自动从 `worker/target/moheng-worker.jar` 查找；安装到其他位置时用 `--worker-jar` 或 `MOHENG_WORKER_JAR` 指定。

### 一键启动脚本

```sh
./start.sh            # 开发模式：后端 8848 + 前端 5173（热更新）
./start.sh --prod     # 生产模式：构建前端并打入后端，单端口 8848
./start.sh --rebuild  # 强制重新构建 Java 模块
./restart.sh          # 停止并重启（默认重新构建，确保代码改动生效）
./restart.sh --prod   # 以生产模式重启
```

首次运行会自动安装前端依赖、构建缺失的 Java 产物，并在 `target/demo-repo` 创建演示仓库。脚本会自动选择 JDK 21，也可通过 `JAVA_HOME` 指定。

## 使用内网 / 私有仓库

在 Web 端「添加项目」时可直接填写任意本地 Git 仓库的绝对路径；`MOHENG_WORKSPACE_ROOT` 只决定「浏览」的起始目录和相对路径的解析基准。也可以直接用 CLI：

```sh
java -jar cli/target/moheng-cli.jar impact analyze \
  --repo /path/to/your-repo --base origin/main --head WORKTREE --format text
```

`--head WORKTREE` 会分析工作区未提交改动。

## CLI

```text
mh impact analyze   分析 base → head 的变更影响（同 `impact` 子命令）
mh impact init      生成 .moheng.yml
mh impact show      查看最近或指定 run 的结果
mh impact serve     启动本地 Web 服务
```

常用参数：

| 参数 | 说明 |
| --- | --- |
| `--repo <path>` | 仓库路径，默认当前目录 |
| `--base <rev>` | 基线 revision（分支 / 标签 / SHA），必填 |
| `--head <rev\|WORKTREE>` | 目标 revision，默认 `WORKTREE` |
| `--format text\|json\|markdown` | 输出格式，默认 `text` |
| `--out <file>` | 输出到文件 |
| `--max-depth <1-10>` | 反向影响最大深度，默认 `5` |
| `--api-prefix <path>` | 运行时 API 前缀 |
| `--include <pkg>` / `--exclude <pkg>` | 包过滤，可重复 |
| `--source-root <path>` | 额外源码根，可重复 |
| `--include-tests` | 包含 `src/test/java` |
| `--high-confidence-only` | 仅保留高置信度边 |
| `--java-version <n>` | 源码 Java 版本，默认 `21` |
| `--fail-on-risk <level>` | 达到风险等级时退出码为 `5` |
| `--timeout <seconds>` | worker 超时，默认 `600` |
| `--json-schema` | 打印结果 JSON Schema |
| `--no-save` | 不写入 `<repo>/.moheng/runs` |
| `--gitlab-comment` | 将摘要写入 MR 评论（需 GitLab MR 流水线） |

`.moheng.yml` 由 `impact init` 生成，可配置 `repo`、`base`、`head`、`maxDepth`、`apiPrefix`、`includePackages`、`excludePackages`、`includeTests`、`highConfidenceOnly`、`javaVersion`、`risk.failOn`、`output.format`。命令行参数优先于配置文件。

退出码：`0` 成功，`2` 用法错误，`3` 仓库错误，`4` 分析失败，`5` 达到风险阈值，`6` worker 不可用。

## GitLab CI 门禁与 MR 评论

在 GitLab Merge Request 流水线中运行时，CLI 会自动读取 `CI_PROJECT_DIR`、`CI_MERGE_REQUEST_DIFF_BASE_SHA`（缺失时用 `CI_COMMIT_BEFORE_SHA`）和 `CI_COMMIT_SHA`，无需再传 `--repo`、`--base`、`--head`。加上 `--gitlab-comment` 后，结果会写入当前 MR 评论：

```sh
java -jar moheng-cli.jar impact analyze \
  --format markdown --out moheng-impact.md --no-save \
  --fail-on-risk HIGH --gitlab-comment
```

- 评论带固定标记 `<!-- moheng-impact -->`，同一 MR 重复运行会更新原评论，不会重复刷屏。
- 需要 masked 的 CI/CD 变量 `MOHENG_GITLAB_TOKEN`（或 `GITLAB_TOKEN`），项目访问令牌权限选 `api`，角色至少 Developer。
- 评论写入失败只告警，不影响分析结论；`--fail-on-risk` 触发的退出码 `5` 仍然生效，从而阻断流水线。
- 流水线需设置 `GIT_DEPTH: "0"`，浅克隆缺少基线提交会导致 diff 失败。

直接可用模板见 [`ci/gitlab/moheng-impact.gitlab-ci.yml`](ci/gitlab/moheng-impact.gitlab-ci.yml)：仅在 MR 流水线运行，上传 `moheng-impact.md` 产物，并按 `MOHENG_FAIL_ON_RISK` 阻断。模板通过 `MOHENG_CLI_JAR`、`MOHENG_WORKER_JAR` 指向 runner 上的 jar；使用容器执行器时取消 `image:` 注释并替换为已发布镜像。

## Docker

```sh
docker compose up --build -d
```

默认仅绑定 `127.0.0.1:8848`，数据持久化在 `moheng-data` 卷。镜像内置 `/workspace/demo`，启动后可直接分析。

分析宿主机上的仓库时，挂载工作区（会覆盖内置演示仓库）。容器只能看到挂载进来的目录，因此 Docker 模式下仍需把目标仓库或其父目录挂到 `/workspace`：

```sh
MOHENG_WORKSPACE=/path/to/repos docker compose up --build -d
```

没有 Compose 插件时可直接运行：

```sh
docker build -t moheng-impact:0.1.0 .

docker run -d --name moheng \
  -p 127.0.0.1:8848:8848 \
  -v moheng-data:/data \
  -v /path/to/repos:/workspace \
  moheng-impact:0.1.0
```

健康检查：`GET /api/health`。

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MOHENG_BIND_ADDRESS` | `127.0.0.1` | 监听地址（容器内为 `0.0.0.0`） |
| `MOHENG_PORT` | `8848` | 监听端口 |
| `MOHENG_WORKSPACE_ROOT` | 进程工作目录 | 目录浏览与相对路径的起点（绝对路径不受限） |
| `MOHENG_DATA_DIR` | `./data` | SQLite、临时文件目录 |
| `MOHENG_WORKER_JAR` | 自动查找 | worker jar 路径 |
| `MOHENG_WORKER_HEAP` | `1g` | worker JVM 堆上限 |
| `MOHENG_ANALYSIS_TIMEOUT_SECONDS` | `600` | 单次分析超时 |
| `MOHENG_DEMO_REPO` | `/workspace/demo` | 演示仓库路径，不存在则隐藏 |

## 架构

```text
web (React / Vite) ──/api──> server (Spring Boot, SQLite)
                               │
                               └── 独立进程 moheng-worker.jar (Spoon)
                                        │
                                        └── core (diff、调用图、影响遍历、风险)
cli (Picocli) ── 调用 worker 或 server
```

- `core`：模型、Git diff、符号变更、影响遍历、风险规则
- `analyzers/java`：Spoon 模型、调用边、Spring API 提取
- `worker`：独立 JVM 执行分析，stdout 输出 JSON，stderr 输出进度
- `cli`：本地分析与服务启动入口
- `server`：REST API、SQLite 持久化、SPA 托管
- `web`：React 报告与影响图

worker 独立进程隔离，可单独限制堆、超时与退出码，分析失败不会拖垮服务。

## 风险模型

评分区间 0–100，等级阈值：`CRITICAL ≥ 60`、`HIGH ≥ 40`、`MEDIUM ≥ 20`、其余 `LOW`。

主要权重：直接修改 Handler `+30`，API 契约删除 / 变更 `+30`，路径深度 1–2 `+10`、3–4 `+20`、≥5 `+25`，低置信度路径 `+5`，高 fan-in `+10`，签名变化 `+15`，公共 DTO 契约变化 `+20`，核心业务关键字 `+10`。Safe Mode 最低 `60`，触发项 ≥3 或受影响 API ≥20 时最低 `80`。

## 已知边界（V0.1）

- 仅支持 Java + Spring MVC / Spring Boot；暂不覆盖 MyBatis、JPA、MQ、Feign 的运行时链路
- 源码级静态分析，不做 classpath / 字节码解析；反射、动态代理、SpEL、事件总线等间接调用可能漏报
- 调用图只保留源码可解析的边；跨模块二进制依赖需提供源码根
- 影响深度上限默认 5，可用 `--max-depth` 调整
- 删除的 API 只能从契约变更推断，无法给出调用链
- 分析结果写入被分析仓库的 `.moheng/runs`（可用 `--no-save` 关闭），服务端分析写入数据目录

## 开发

```sh
# 后端测试
mvn -B test

# 前端开发服务器（/api 代理到 8848）
pnpm --dir web dev

# 前端类型检查与构建
pnpm --dir web build
```

内部仓库三档变更集的校准结果见 [docs/校准报告_V0.1.md](docs/校准报告_V0.1.md)。

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。第三方组件与许可证说明见 [NOTICE](NOTICE)。
