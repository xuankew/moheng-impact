#!/bin/sh
set -eu

if [ "$#" -gt 1 ]; then
    echo "usage: $0 [target-directory]" >&2
    exit 2
fi

target=${1:-/workspace/demo}
if [ -e "$target" ] && [ -n "$(ls -A "$target" 2>/dev/null)" ]; then
    echo "target exists and is not empty: $target" >&2
    exit 1
fi

source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

mkdir -p "$target"
cp -R "$source_dir/base/." "$target/"

git -C "$target" init -q
git -C "$target" add -A
git -C "$target" -c user.name="Moheng Demo" -c user.email="demo@moheng.invalid" \
    commit -q -m "demo: initial order service"

cp -R "$source_dir/head/." "$target/"
git -C "$target" add -A
git -C "$target" -c user.name="Moheng Demo" -c user.email="demo@moheng.invalid" \
    commit -q -m "demo: coupon fallback, DTO field and port change"

echo "demo repository ready: $target"
