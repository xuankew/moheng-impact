#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"

MODE="dev"
REBUILD=0
SERVER_PID=""
WEB_PID=""

usage() {
    cat <<'EOF'
用法: ./start.sh [--dev|--prod] [--rebuild]

  --dev       开发模式（默认）：后端 8848 + Vite 开发服务器 5173（热更新）
  --prod      生产模式：构建前端并打入后端，只启动 8848 单端口
  --rebuild   强制重新构建 Java 模块

可用环境变量:
  MOHENG_WORKSPACE_ROOT   允许分析的仓库根目录（默认：项目根目录）
  MOHENG_DATA_DIR         数据与 SQLite 目录（默认：<项目>/data）
  MOHENG_DEMO_REPO        演示仓库路径（默认：<项目>/target/demo-repo）
  MOHENG_WORKER_HEAP      worker 堆上限（默认：1g）
EOF
}

die() {
    echo "错误: $*" >&2
    exit 1
}

java_major() {
    "$1" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | cut -d. -f1
}

select_java() {
    local home major path_java
    local candidates=()
    if [ -n "${JAVA_HOME:-}" ]; then
        candidates+=("$JAVA_HOME")
    fi
    for home in "$HOME"/.sdkman/candidates/java/21* \
                /opt/homebrew/opt/openjdk@21 \
                /usr/local/opt/openjdk@21; do
        if [ -d "$home" ]; then
            candidates+=("$home")
        fi
    done
    if [ "${#candidates[@]}" -gt 0 ]; then
        for home in "${candidates[@]}"; do
            [ -x "$home/bin/java" ] || continue
            major="$(java_major "$home/bin/java")"
            if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then
                export JAVA_HOME="$home"
                export PATH="$JAVA_HOME/bin:$PATH"
                JAVA_BIN="$JAVA_HOME/bin/java"
                return 0
            fi
        done
    fi
    if command -v java >/dev/null 2>&1; then
        path_java="$(command -v java)"
        major="$(java_major "$path_java")"
        if [ -n "$major" ] && [ "$major" -ge 21 ] 2>/dev/null; then
            unset JAVA_HOME
            export PATH="$(dirname "$path_java"):$PATH"
            JAVA_BIN="$path_java"
            return 0
        fi
    fi
    return 1
}

check_port() {
    if lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "端口 $1 已被占用：" >&2
        lsof -nP -iTCP:"$1" -sTCP:LISTEN | sed -n '2,3p' >&2
        die "请先停止占用该端口的进程"
    fi
}

ensure_web_deps() {
    command -v pnpm >/dev/null 2>&1 || die "未找到 pnpm，请先安装（npm install -g pnpm）"
    if [ ! -d web/node_modules ]; then
        echo "==> 安装前端依赖"
        pnpm --dir web install
    fi
}

ensure_java_build() {
    local need=0
    if [ "$REBUILD" -eq 1 ]; then
        need=1
    fi
    if [ ! -f server/target/moheng-server.jar ] || [ ! -f worker/target/moheng-worker.jar ]; then
        need=1
    fi
    if [ "$need" -eq 1 ]; then
        command -v mvn >/dev/null 2>&1 || die "未找到 Maven，请先安装 Maven 3.9+"
        echo "==> 构建 Java 模块（跳过测试）"
        mkdir -p web/dist
        mvn -B -DskipTests package
    fi
}

prepare_demo() {
    local default_repo="$ROOT/target/demo-repo"
    DEMO_REPO="${MOHENG_DEMO_REPO:-$default_repo}"
    if [ ! -d "$DEMO_REPO/.git" ] && [ "$DEMO_REPO" = "$default_repo" ]; then
        if command -v git >/dev/null 2>&1; then
            echo "==> 创建演示仓库 $DEMO_REPO"
            mkdir -p "$(dirname "$DEMO_REPO")"
            sh demo/setup.sh "$DEMO_REPO" || echo "警告: 演示仓库创建失败，可稍后手动执行 demo/setup.sh" >&2
        else
            echo "警告: 未找到 git，跳过演示仓库创建" >&2
        fi
    fi
}

start_backend() {
    export MOHENG_WORKSPACE_ROOT="${MOHENG_WORKSPACE_ROOT:-$ROOT}"
    export MOHENG_DATA_DIR="${MOHENG_DATA_DIR:-$ROOT/data}"
    export MOHENG_WORKER_JAR="$ROOT/worker/target/moheng-worker.jar"
    export MOHENG_PORT=8848
    if [ -d "$DEMO_REPO" ]; then
        export MOHENG_DEMO_REPO="$DEMO_REPO"
    else
        unset MOHENG_DEMO_REPO || true
    fi
    mkdir -p "$MOHENG_DATA_DIR"

    "$JAVA_BIN" -jar server/target/moheng-server.jar &
    SERVER_PID=$!

    echo "==> 等待后端就绪 http://127.0.0.1:8848"
    local i
    for i in $(seq 1 60); do
        if curl -fsS http://127.0.0.1:8848/api/health >/dev/null 2>&1; then
            return 0
        fi
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo "后端进程已退出，请检查上方日志" >&2
            return 1
        fi
        sleep 1
    done
    echo "后端在 60 秒内未就绪" >&2
    return 1
}

monitor() {
    while true; do
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo "后端已退出。" >&2
            exit 1
        fi
        if [ -n "$WEB_PID" ] && ! kill -0 "$WEB_PID" 2>/dev/null; then
            echo "前端已退出。" >&2
            exit 1
        fi
        sleep 1
    done
}

cleanup() {
    local code=$?
    trap - INT TERM EXIT
    echo
    echo "==> 正在停止服务"
    if [ -n "$WEB_PID" ] && kill -0 "$WEB_PID" 2>/dev/null; then
        kill "$WEB_PID" 2>/dev/null || true
    fi
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null || true
    fi
    if [ -n "$WEB_PID" ]; then
        wait "$WEB_PID" 2>/dev/null || true
    fi
    if [ -n "$SERVER_PID" ]; then
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    exit "$code"
}

for arg in "$@"; do
    case "$arg" in
        --dev) MODE="dev" ;;
        --prod) MODE="prod" ;;
        --rebuild) REBUILD=1 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知参数: $arg" >&2; usage; exit 2 ;;
    esac
done

select_java || die "未找到 JDK 21，请安装 JDK 21 或设置 JAVA_HOME 指向 JDK 21"
echo "使用 Java: $("$JAVA_BIN" -version 2>&1 | head -1)"

command -v node >/dev/null 2>&1 || die "未找到 Node.js，请先安装 Node.js 22+"
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if [ "$NODE_MAJOR" -lt 22 ]; then
    die "Node.js 版本过低（当前 $(node -v)），需要 22+"
fi

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

prepare_demo
ensure_web_deps

if [ "$MODE" = "prod" ]; then
    echo "==> 构建前端"
    pnpm --dir web build
    echo "==> 打包后端（包含前端静态资源）"
    command -v mvn >/dev/null 2>&1 || die "未找到 Maven，请先安装 Maven 3.9+"
    mvn -B -DskipTests package
    check_port 8848
    start_backend || exit 1
    cat <<EOF

墨衡·见微已启动（生产模式）
  界面:   http://127.0.0.1:8848
  健康:   http://127.0.0.1:8848/api/health
  数据:   ${MOHENG_DATA_DIR}
  演示库: ${DEMO_REPO}
按 Ctrl+C 停止。
EOF
else
    ensure_java_build
    check_port 8848
    check_port 5173
    start_backend || exit 1
    echo "==> 启动前端开发服务器 http://127.0.0.1:5173"
    pnpm --dir web dev --host 127.0.0.1 &
    WEB_PID=$!
    cat <<EOF

墨衡·见微已启动（开发模式）
  界面:   http://127.0.0.1:5173
  后端:   http://127.0.0.1:8848
  数据:   ${MOHENG_DATA_DIR}
  演示库: ${DEMO_REPO}
按 Ctrl+C 停止。
EOF
fi

monitor
