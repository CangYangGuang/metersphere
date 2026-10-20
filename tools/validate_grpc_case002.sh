#!/usr/bin/env bash
set -euo pipefail
BASE="${MS_BASE:-http://localhost:8085}"
PROJECT="${MS_PROJECT:-100001100001}"
ORG="${MS_ORG:-100001}"
ADDR="${GRPC_ADDR:-host.docker.internal:50051}"

PROTO='syntax = "proto3";

package hello;

service HelloService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string greeting = 1;
}

message HelloReply {
  string reply = 1;
}
'

login() {
  local resp token csrf
  resp=$(curl -s -X POST "$BASE/login" -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"metersphere","authenticate":"LOCAL"}')
  token=$(echo "$resp" | python -c "import sys,json; print(json.load(sys.stdin)['data']['sessionId'])")
  csrf=$(echo "$resp" | python -c "import sys,json; print(json.load(sys.stdin)['data']['csrfToken'])")
  echo "$token|$csrf"
}

api() {
  local method=$1 path=$2 body=$3
  IFS='|' read -r TOKEN CSRF <<< "$AUTH"
  curl -s -X "$method" "$BASE$path" \
    -H "Content-Type: application/json" \
    -H "x-auth-token: $TOKEN" \
    -H "csrf-token: $CSRF" \
    -H "organization: $ORG" \
    -H "project: $PROJECT" \
    -d "$body"
}

AUTH=$(login)
echo "== login OK =="

PARSE=$(api POST /api/grpc/debug/parse "{\"protoContent\":$(python -c "import json; print(json.dumps('''$PROTO'''))")}")
DESC=$(echo "$PARSE" | python -c "import sys,json; d=json.load(sys.stdin)['data']; print(d.get('descriptorContent',''))")
echo "== parse OK descriptor len=${#DESC} =="

MODULE=$(api GET "/api/definition/module/tree?_t=$(date +%s)" '{}')
MOD_ID=$(echo "$MODULE" | python -c "import sys,json; print(json.load(sys.stdin)['data'][0]['id'])" 2>/dev/null || echo "root")

DEF_BODY=$(python - <<PY
import json
print(json.dumps({
  "name": "grpc-case002-def",
  "protocol": "GRPC",
  "method": "GRPC",
  "path": "$ADDR",
  "projectId": "$PROJECT",
  "moduleId": "$MOD_ID",
  "status": "UNDERWAY",
  "tags": [],
  "grpcDefinition": {
    "protoContent": '''$PROTO''',
    "fileName": "hello.proto",
    "mainFileName": "hello.proto",
    "serviceName": "HelloService",
    "methodName": "SayHello",
    "descriptorContent": "$DESC",
    "requestJson": json.dumps({"greeting": "case002"})
  }
}))
PY
)
DEF=$(api POST /api/definition/add "$DEF_BODY")
DEF_ID=$(echo "$DEF" | python -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "== definition/add id=$DEF_ID =="

CASE_BODY=$(python - <<PY
import json
print(json.dumps({
  "name": "grpc-case002-case",
  "projectId": "$PROJECT",
  "environmentId": "",
  "apiDefinitionId": "$DEF_ID",
  "request": {},
  "grpcCaseConfig": {
    "serviceName": "HelloService",
    "methodName": "SayHello",
    "serverAddress": "$ADDR",
    "descriptorContent": "$DESC",
    "protoContent": '''$PROTO''',
    "fileName": "hello.proto",
    "mainFileName": "hello.proto",
    "requestJson": json.dumps({"greeting": "case002"}),
    "metadata": [],
    "timeout": 60000
  },
  "priority": "P0",
  "status": "PROCESSING",
  "tags": [],
  "uploadFileIds": [],
  "linkFileIds": []
}))
PY
)
CASE=$(api POST /api/case/add "$CASE_BODY")
CASE_ID=$(echo "$CASE" | python -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "== case/add id=$CASE_ID =="

DB_SA=$(docker exec metersphere-grpc-verify4 mysql -uroot -p111111 -N -e \
  "SELECT server_address FROM test.api_grpc_case_config WHERE api_case_id='$CASE_ID' AND deleted=0 LIMIT 1;" 2>/dev/null || true)
echo "== DB server_address=$DB_SA =="

RUN=$(api POST /api/case/run "{\"id\":\"$CASE_ID\",\"reportId\":null,\"request\":{\"grpcCaseConfig\":{\"serviceName\":\"HelloService\",\"methodName\":\"SayHello\",\"descriptorContent\":\"$DESC\",\"requestJson\":\"{\\\"greeting\\\":\\\"case002\\\"}\"}}}")
echo "$RUN" | python -c "import sys,json; r=json.load(sys.stdin); print('== case/run code=', r.get('code'), 'success=', r.get('data',{}).get('success'), 'grpcCode=', r.get('data',{}).get('grpcCode'), 'reportId=', r.get('data',{}).get('reportId'))"
REPORT_ID=$(echo "$RUN" | python -c "import sys,json; print(json.load(sys.stdin)['data'].get('reportId',''))")

REPORT=$(api GET "/api/report/case/get/$REPORT_ID" '{}')
echo "$REPORT" | python -c "import sys,json; r=json.load(sys.stdin); print('== report get code=', r.get('code'), 'name=', r.get('data',{}).get('name'))"

echo "CASE_ID=$CASE_ID"
echo "REPORT_ID=$REPORT_ID"
echo "DEF_ID=$DEF_ID"
