#!/usr/bin/env python3
"""API validation for BUG-GRPC-CASE-002 and report chain."""
import json
import os
import subprocess
import time
import sys
import urllib.request

BASE = os.environ.get("MS_BASE", "http://localhost:8085")
PROJECT = os.environ.get("MS_PROJECT", "100001100001")
ORG = os.environ.get("MS_ORG", "100001")
ADDR = os.environ.get("GRPC_ADDR", "host.docker.internal:50051")

PROTO = """syntax = "proto3";

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


def req(method, path, body=None, headers=None):
    h = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h.setdefault("Content-Type", "application/json")
    r = urllib.request.Request(f"{BASE}{path}", data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body_text = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> {e.code}: {body_text}") from e


def main():
    login = req("POST", "/login", {"username": "admin", "password": "metersphere", "authenticate": "LOCAL"})
    token = login["data"]["sessionId"]
    csrf = login["data"]["csrfToken"]
    headers = {
        "x-auth-token": token,
        "csrf-token": csrf,
        "organization": ORG,
        "project": PROJECT,
    }
    print("login OK")

    parsed = req("POST", "/api/grpc/debug/parse", {"protoContent": PROTO}, headers)
    desc = parsed["data"]["descriptorContent"]
    print("parse OK")

    mod = req("POST", "/api/definition/module/tree", {"keyword": "", "protocols": ["GRPC"], "projectId": PROJECT, "moduleIds": []}, headers)
    mod_id = mod["data"][0]["id"] if mod.get("data") else "root"

    suffix = str(int(time.time()))
    def_add = req(
        "POST",
        "/api/definition/add",
        {
            "name": f"grpc-case002-def-{suffix}",
            "protocol": "GRPC",
            "method": "GRPC",
            "path": ADDR,
            "projectId": PROJECT,
            "moduleId": mod_id,
            "status": "PROCESSING",
            "tags": [],
            "grpcDefinition": {
                "protoContent": PROTO,
                "fileName": "hello.proto",
                "mainFileName": "hello.proto",
                "serviceName": "HelloService",
                "methodName": "SayHello",
                "descriptorContent": desc,
                "requestJson": json.dumps({"greeting": "case002"}),
            },
        },
        headers,
    )
    def_id = def_add["data"]["id"]
    print(f"definition/add id={def_id}")

    case_add = req(
        "POST",
        "/api/case/add",
        {
            "name": f"grpc-case002-case-{suffix}",
            "projectId": PROJECT,
            "environmentId": "",
            "apiDefinitionId": def_id,
            "request": {},
            "grpcCaseConfig": {
                "serviceName": "HelloService",
                "methodName": "SayHello",
                "serverAddress": ADDR,
                "descriptorContent": desc,
                "protoContent": PROTO,
                "fileName": "hello.proto",
                "mainFileName": "hello.proto",
                "requestJson": json.dumps({"greeting": "case002"}),
                "metadata": [],
                "timeout": 60000,
            },
            "priority": "P0",
            "status": "PROCESSING",
            "tags": [],
            "uploadFileIds": [],
            "linkFileIds": [],
        },
        headers,
    )
    case_id = case_add["data"]["id"]
    print(f"case/add id={case_id}")

    db = subprocess.check_output(
        [
            "docker",
            "exec",
            "metersphere-grpc-verify4",
            "mysql",
            "-uroot",
            "-p111111",
            "-h127.0.0.1",
            "-N",
            "-e",
            f"SELECT server_address FROM test.api_grpc_case_config WHERE api_case_id='{case_id}' AND deleted=0 LIMIT 1;",
        ],
        text=True,
    ).strip()
    print(f"DB server_address={db!r}")
    if db != ADDR:
        print("FAIL: server_address not persisted")
        sys.exit(1)

    run = req(
        "POST",
        "/api/case/run",
        {
            "id": case_id,
            "apiDefinitionId": def_id,
            "reportId": None,
            "request": {
                "grpcCaseConfig": {
                    "serviceName": "HelloService",
                    "methodName": "SayHello",
                    "descriptorContent": desc,
                    "requestJson": json.dumps({"greeting": "case002"}),
                }
            },
        },
        headers,
    )
    data = run.get("data") or {}
    print(f"case/run success={data.get('success')} grpcCode={data.get('grpcCode')} reportId={data.get('reportId')}")
    if not data.get("success"):
        print("FAIL: case run without serverAddress in request")
        sys.exit(1)

    report_id = data.get("reportId")
    report = req("GET", f"/api/report/case/get/{report_id}", headers=headers)
    print(f"report/get code={report.get('code')} name={report.get('data', {}).get('name')}")
    if report.get("code") != 100200 or not report.get("data"):
        print("FAIL: report not found")
        sys.exit(1)

    print(f"CASE_ID={case_id}")
    print(f"REPORT_ID={report_id}")
    print(f"DEF_ID={def_id}")
    print("VALIDATION PASS")


if __name__ == "__main__":
    main()
