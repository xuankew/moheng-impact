#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
START_ARGS=()
REBUILD=1
DRY_RUN=0
MODE_SET=0

usage() {
    cat <<'EOF'
用法: ./restart.sh [--dev|--prod] [--no-build] [--dry-run]

  --dev       重启为开发模式（默认）：后端 8848 + 前端 5173
  --prod      重启为生产模式：单端口 8848
  --no-build  跳过 Java 重新构建（默认会重新构建，确保代码改动生效）
  --dry-run   只检查将停止/启动的进程，不实际执行

脚本只停止本项目的 moheng-server 与 web Vite 进程；端口被其他程序占用时会报错退出。
EOF
}

for arg in "$@"; do
    case "$arg" in
        --dev|--prod)
            START_ARGS+=("$arg")
            MODE_SET=1
            ;;
        --no-build) REBUILD=0 ;;
        --dry-run) DRY_RUN=1 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知参数: $arg" >&2; usage; exit 2 ;;
    esac
done

if [ "$MODE_SET" -eq 0 ]; then
    START_ARGS+=(--dev)
fi
if [ "$REBUILD" -eq 1 ]; then
    START_ARGS+=(--rebuild)
fi

port_listening() {
    lsof -t -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_exit() {
    local pid="$1" stat i
    for i in $(seq 1 60); do
        stat="$(ps -p "$pid" -o stat= 2>/dev/null | tr -d '[:space:]' || true)"
        if [ -z "$stat" ]; then
            return 0
        fi
        case "$stat" in
            Z*) return 0 ;;
        esac
        sleep 0.5
    done
    return 1
}

stop_port() {
    local port="$1" marker="$2" pid cmd i
    pid="$(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
    if [ -z "$pid" ]; then
        return 0
    fi
    cmd="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    case "$cmd" in
        *"$marker"*) ;;
        *)
            echo "端口 ${port} 被其他进程占用（PID ${pid}）：${cmd}" >&2
            return 1
            ;;
    esac
    if [ "$DRY_RUN" -eq 1 ]; then
        echo "==> 将停止端口 ${port}（PID ${pid}）"
        return 0
    fi
    echo "==> 停止端口 ${port}（PID ${pid}）"
    kill -TERM "$pid" 2>/dev/null || true
    kill -CONT "$pid" 2>/dev/null || true
    for i in $(seq 1 10); do
        port_listening "$port" || break
        sleep 1
    done
    if port_listening "$port"; then
        echo "优雅停止超时，强制终止端口 ${port}（PID ${pid}）"
        kill -KILL "$pid" 2>/dev/null || true
        for i in $(seq 1 5); do
            port_listening "$port" || break
            sleep 1
        done
        if port_listening "$port"; then
            echo "端口 ${port} 无法释放" >&2
            return 1
        fi
    fi
    # 端口释放不代表进程已退出：Spring 关闭钩子仍可能加载 jar 内的类，
    # 必须等进程真正结束，否则随后重建 jar 会让钩子抛 NoClassDefFoundError。
    if ! wait_exit "$pid"; then
        echo "进程 ${pid} 30 秒内未退出，强制终止"
        kill -KILL "$pid" 2>/dev/null || true
        if ! wait_exit "$pid"; then
            echo "进程 ${pid} 无法终止" >&2
            return 1
        fi
    fi
    return 0
}

stop_port 8848 "moheng-server.jar" || exit 1
stop_port 5173 "$ROOT/web/node_modules" || exit 1

if [ "$DRY_RUN" -eq 1 ]; then
    echo "==> 将启动: bash $ROOT/start.sh ${START_ARGS[*]}"
    exit 0
fi

echo "==> 重新启动服务"
exec bash "$ROOT/start.sh" "${START_ARGS[@]}"
