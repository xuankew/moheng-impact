#!/bin/sh
set -eu

data_dir=${MOHENG_DATA_DIR:-/data}
mkdir -p "$data_dir"

exec java ${JAVA_OPTS:-} -XX:MaxRAMPercentage=75 -jar /app/moheng-server.jar "$@"
