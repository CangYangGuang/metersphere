#!/bin/sh
set -eu

MYSQL_HOST="${MS_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MS_MYSQL_PORT:-3306}"
MYSQL_USER="${MS_MYSQL_USER:-root}"
MYSQL_PASS="${MS_MYSQL_PASS:-111111}"
MYSQL_DB="${MS_MYSQL_DB:-test}"
PATCH_SQL="${MS_GRPC_SCHEMA_PATCH:-/opt/metersphere/conf/grpc-schema-patch.sql}"
UPGRADE_SQL="${MS_GRPC_SCHEMA_UPGRADE:-/opt/metersphere/conf/grpc-schema-patch-upgrade.sql}"
MAX_WAIT="${MS_GRPC_SCHEMA_WAIT:-120}"

mysql_cmd() {
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -h"$MYSQL_HOST" -P"$MYSQL_PORT" --protocol=TCP "$@"
}

index_exists() {
  mysql_cmd -N -e \
    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='${MYSQL_DB}' AND TABLE_NAME='$1' AND INDEX_NAME='$2';" \
    "$MYSQL_DB"
}

create_index_if_missing() {
  idx="$1"; tbl="$2"; cols="$3"
  if [ "$(index_exists "$tbl" "$idx")" = "0" ]; then
    echo "[grpc-schema] create index ${idx} on ${tbl}(${cols})"
    mysql_cmd -e "CREATE INDEX ${idx} ON ${tbl} (${cols});" "$MYSQL_DB"
  fi
}

is_upgraded_db_without_grpc_flyway() {
  local ver_table_cnt
  ver_table_cnt=$(mysql_cmd -N -e \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}' AND TABLE_NAME='metersphere_version';" \
    "$MYSQL_DB")
  if [ "$ver_table_cnt" = "0" ]; then
    return 1
  fi
  local grpc_migration_cnt
  grpc_migration_cnt=$(mysql_cmd -N -e \
    "SELECT COUNT(*) FROM metersphere_version WHERE version IN ('3.6.0.3','3.6.0.4','3.6.0.5');" \
    "$MYSQL_DB" 2>/dev/null || echo "0")
  [ "$grpc_migration_cnt" = "0" ]
}

echo "[grpc-schema] waiting for MySQL ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB} (timeout=${MAX_WAIT}s)"
elapsed=0
while [ "$elapsed" -lt "$MAX_WAIT" ]; do
  if mysql_cmd -N -e "SELECT 1" "$MYSQL_DB" >/dev/null 2>&1; then
    break
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done
if ! mysql_cmd -N -e "SELECT 1" "$MYSQL_DB" >/dev/null 2>&1; then
  echo "[grpc-schema] ERROR: MySQL not ready after ${MAX_WAIT}s" >&2
  exit 1
fi

echo "[grpc-schema] applying base patch: $PATCH_SQL"
mysql_cmd "$MYSQL_DB" < "$PATCH_SQL"

echo "[grpc-schema] applying upgrade patch: $UPGRADE_SQL"
mysql_cmd "$MYSQL_DB" < "$UPGRADE_SQL"

if is_upgraded_db_without_grpc_flyway; then
  echo "[grpc-schema] upgraded database without gRPC Flyway migrations; applying upgrade patch"
  mysql_cmd "$MYSQL_DB" < "$UPGRADE_SQL"
  create_index_if_missing idx_project_id api_grpc_proto project_id
  create_index_if_missing idx_content_hash api_grpc_proto "project_id, content_hash"
  create_index_if_missing idx_api_definition_id api_grpc_definition api_definition_id
  create_index_if_missing idx_proto_id_def api_grpc_definition proto_id
  create_index_if_missing uk_api_case_id api_grpc_case_config api_case_id
  create_index_if_missing idx_api_definition_id_case api_grpc_case_config api_definition_id
  create_index_if_missing idx_proto_id_case api_grpc_case_config proto_id
else
  echo "[grpc-schema] fresh database; columns/indexes deferred to Flyway"
fi

echo "[grpc-schema] verification queries:"
mysql_cmd -e \
  "SHOW TABLES LIKE 'api_grpc_%';
   SELECT COUNT(*) AS proto_cnt FROM api_grpc_proto;
   SELECT COUNT(*) AS def_cnt FROM api_grpc_definition;
   SELECT COUNT(*) AS case_cnt FROM api_grpc_case_config;" \
  "$MYSQL_DB"

for tbl in api_grpc_proto api_grpc_definition api_grpc_case_config; do
  cnt=$(mysql_cmd -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}' AND TABLE_NAME='${tbl}';" "$MYSQL_DB")
  if [ "$cnt" != "1" ]; then
    echo "[grpc-schema] ERROR: missing table ${tbl}" >&2
    exit 1
  fi
done

if is_upgraded_db_without_grpc_flyway; then
  mysql_cmd -e \
    "SHOW COLUMNS FROM api_grpc_case_config LIKE 'assertion_json';
     SHOW COLUMNS FROM api_grpc_case_config LIKE 'extract_json';" \
    "$MYSQL_DB"
  for col in assertion_json extract_json server_address; do
    cnt=$(mysql_cmd -N -e \
      "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${MYSQL_DB}' AND TABLE_NAME='api_grpc_case_config' AND COLUMN_NAME='${col}';" \
      "$MYSQL_DB")
    if [ "$cnt" != "1" ]; then
      echo "[grpc-schema] ERROR: missing column api_grpc_case_config.${col}" >&2
      exit 1
    fi
  done
fi

echo "[grpc-schema] OK: gRPC base schema ready"
