#!/usr/bin/env bash
# 仅操作显式指定或本次验收创建的 Compose 工程；失败时保留日志。
set -euo pipefail
fixture_dir="$(cd "$(dirname "$0")" && pwd)"
repo_dir="$(cd "$fixture_dir/../.." && pwd)"
action="${1:-verify}"
mkdir -p "$fixture_dir/build/logs"
export ORDER_IT_MQ_ENDPOINT="${ORDER_IT_MQ_ENDPOINT:-localhost:${ORDER_IT_MQ_PORT:-8081}}"
export ORDER_IT_MYSQL_PORT="${ORDER_IT_MYSQL_PORT:-3307}"
export ORDER_IT_REDIS_PORT="${ORDER_IT_REDIS_PORT:-6380}"
compose() { docker compose -f "$fixture_dir/compose.yaml" -p "$COMPOSE_PROJECT_NAME" "$@"; }
gradle() { "$repo_dir/gradlew" -p "$fixture_dir" --console=plain "$@"; }
publish() { "$repo_dir/gradlew" -p "$repo_dir" publishAllPublicationsToTemporaryRepository --console=plain; }
start() {
  docker info >/dev/null
  compose up -d --wait --wait-timeout 300 mysql redis nameserver broker
  compose run --rm initialize
}
case "$action" in
  verify)
    # 每次生成全新工程，禁止接管外部传入的 Compose 工程。
    export COMPOSE_PROJECT_NAME="order-it-$(date +%s)-$$"
    cleanup() {
      result=$?
      trap - EXIT
      compose logs --no-color > "$fixture_dir/build/logs/$COMPOSE_PROJECT_NAME.log" 2>&1 || true
      compose down --volumes --remove-orphans || result=1
      exit "$result"
    }
    if [[ "${2:-}" != "--published" ]]; then publish; fi
    docker info >/dev/null
    trap cleanup EXIT
    start
    gradle check integrationTest
    ;;
  start)
    : "${COMPOSE_PROJECT_NAME:?请指定专用 COMPOSE_PROJECT_NAME，例如 order-it-local}"
    start
    ;;
  test)
    publish
    gradle check integrationTest
    ;;
  stop)
    : "${COMPOSE_PROJECT_NAME:?请指定需要清理的集成测试 Compose 工程}"
    case "$COMPOSE_PROJECT_NAME" in order-it-*) ;; *) echo "只允许清理 order-it- 前缀的专用工程" >&2; exit 2;; esac
    compose down --volumes --remove-orphans
    ;;
  *) echo "用法：$0 {verify|start|test|stop}" >&2; exit 2 ;;
esac
