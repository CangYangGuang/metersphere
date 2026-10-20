#!/usr/bin/env python3
"""Phase 1 V1.0 gRPC main chain runtime helper (API evidence for UI regression)."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8085"
PROJECT = "100001100001"
ORG = "100001"
GRPC_SERVER = "grpcb.in:9000"
NOW = int(time.time() * 1000)

HELLO_PROTO = """syntax = "proto3";

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
"""

REQUEST_JSON = '{"greeting":"V1.0 UI"}'


def http(method, path, body=None, token=None, csrf=None):
    headers = {"Content-Type": "application/json", "ORGANIZATION": ORG, "PROJECT": PROJECT}
    if token:
        headers["X-AUTH-TOKEN"] = token
    if csrf:
        headers["CSRF-TOKEN"] = csrf
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def login():
    req = urllib.request.Request(
        BASE + "/login",
        data=json.dumps({"username": "admin", "password": "metersphere", "authenticate": "LOCAL"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        token = resp.headers.get("X-AUTH-TOKEN") or resp.headers.get("x-auth-token")
    info = http("GET", "/is-login", token=token)
    return token, info["data"]["csrfToken"]


def main():
    token, csrf = login()
    parsed = http("POST", "/api/grpc/debug/parse", {"protoContent": HELLO_PROTO, "fileName": "hello.proto"}, token, csrf)["data"]
    module_id = http("POST", "/api/definition/module/tree", {"projectId": PROJECT}, token, csrf)["data"][0]["id"]
    def_body = {
        "name": f"phase1-ui-{NOW}",
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "protoContent": HELLO_PROTO,
            "fileName": "hello.proto",
            "serviceName": "HelloService",
            "methodName": "SayHello",
        },
    }
    def_id = http("POST", "/api/definition/add", def_body, token, csrf)["data"]["id"]
    def_detail = http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf)["data"]
    proto_id = def_detail["grpcDefinition"]["protoId"]
    descriptor = parsed["descriptorContent"]
    debug = http(
        "POST",
        "/api/grpc/debug/send",
        {
            "serverAddress": GRPC_SERVER,
            "packageName": parsed.get("packageName"),
            "serviceName": "HelloService",
            "methodName": "SayHello",
            "descriptorContent": descriptor,
            "requestJson": REQUEST_JSON,
            "metadata": [],
            "timeout": 60000,
        },
        token,
        csrf,
    )["data"]
    case_body = {
        "name": f"phase1-case-{NOW}",
        "priority": "P0",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "apiDefinitionId": def_id,
        "request": {},
        "grpcCaseConfig": {
            "requestJson": REQUEST_JSON,
            "serverAddress": GRPC_SERVER,
            "metadata": [],
            "timeout": 60000,
        },
    }
    case_id = http("POST", "/api/case/add", case_body, token, csrf)["data"]["id"]
    run_q = urllib.parse.urlencode({"serverAddress": GRPC_SERVER})
    run = http("GET", f"/api/case/run/{case_id}?{run_q}", token=token, csrf=csrf)["data"]
    report_id = run.get("reportId")
    report = http("GET", f"/api/report/case/get/{report_id}", token=token, csrf=csrf)["data"]
    task_report = http("GET", f"/api/report/case/task-report/{report_id}", token=token, csrf=csrf)["data"]
    step_id = None
    if task_report.get("stepList"):
        step_id = task_report["stepList"][0].get("id") or task_report["stepList"][0].get("stepId")
    step_detail = (
        http("GET", f"/api/report/case/get/detail/{report_id}/{step_id}", token=token, csrf=csrf)["data"]
        if step_id
        else None
    )
    out = {
        "definitionId": def_id,
        "protoId": proto_id,
        "serviceName": "HelloService",
        "methodName": "SayHello",
        "caseId": case_id,
        "debug": debug,
        "run": run,
        "reportId": report_id,
        "stepId": step_id,
        "reportName": report.get("name"),
        "stepDetailKeys": list((step_detail or {}).keys()),
        "grpcFields": step_detail,
    }
    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
