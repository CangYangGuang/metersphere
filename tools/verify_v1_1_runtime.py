#!/usr/bin/env python3
"""V1.1 runtime acceptance on verify4 (8085): 9A-1 / 9B / 9C-1 + V1.0 regression."""
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8085"
PROJECT = "100001100001"
ORG = "100001"
GRPC_SERVER = "grpcb.in:9000"
EXISTING_DEF = "1513376497484701696"
EXISTING_CASE = "1513376634923655168"
NOW = int(time.time() * 1000)

HELLO_PROTO_V1 = """syntax = "proto3";
package hello;
service HelloService {
  rpc SayHello (HelloRequest) returns (HelloResponse);
}
message HelloRequest { string greeting = 1; }
message HelloResponse { string reply = 1; }
"""

HELLO_PROTO_V2_ADD = HELLO_PROTO_V1.replace(
    "string greeting = 1;", "string greeting = 1;\n  string extra = 2;"
)
HELLO_PROTO_V2_TYPE = HELLO_PROTO_V1.replace("string greeting = 1;", "int32 greeting = 1;")
HELLO_PROTO_V2_METHOD = HELLO_PROTO_V1.replace(
    "rpc SayHello (HelloRequest) returns (HelloResponse);",
    "rpc SayGoodbye (HelloRequest) returns (HelloResponse);",
)


def http(method, path, body=None, token=None, csrf=None):
    headers = {"Content-Type": "application/json", "ORGANIZATION": ORG, "PROJECT": PROJECT}
    if token:
        headers["X-AUTH-TOKEN"] = token
    if csrf:
        headers["CSRF-TOKEN"] = csrf
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"HTTP {e.code} {path}: {e.read().decode()}") from e


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


def parse_proto(token, csrf, content):
    return http("POST", "/api/grpc/debug/parse", {"protoContent": content, "fileName": "hello.proto"}, token, csrf)["data"]


def get_module_id(token, csrf):
    return http("POST", "/api/definition/module/tree", {"projectId": PROJECT}, token, csrf)["data"][0]["id"]


def grpc_def_body(name, module_id, proto_content, service="HelloService", method="SayHello"):
    return {
        "name": name,
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "protoContent": proto_content,
            "fileName": "hello.proto",
            "serviceName": service,
            "methodName": method,
        },
    }


def add_definition(token, csrf, proto_content, suffix, service="HelloService", method="SayHello"):
    body = grpc_def_body(f"v11-{suffix}-{NOW}", get_module_id(token, csrf), proto_content, service, method)
    def_id = http("POST", "/api/definition/add", body, token, csrf)["data"]["id"]
    return def_id, http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf)["data"]


def update_definition(token, csrf, def_id, proto_content, service="HelloService", method="SayHello"):
    detail = http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf)["data"]
    body = grpc_def_body(detail["name"], detail["moduleId"], proto_content, service, method)
    body["id"] = def_id
    http("POST", "/api/definition/update", body, token, csrf)
    return http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf)["data"]


def add_case(token, csrf, def_id, request_json):
    body = {
        "name": f"v11-case-{NOW}-{request_json[:8]}",
        "priority": "P0",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "apiDefinitionId": def_id,
        "request": {},
        "grpcCaseConfig": {"requestJson": request_json, "metadata": [], "timeout": 60000},
    }
    case_id = http("POST", "/api/case/add", body, token, csrf)["data"]["id"]
    return case_id, http("GET", f"/api/case/get-detail/{case_id}", token=token, csrf=csrf)["data"]


def run_case(token, csrf, case_id):
    q = urllib.parse.urlencode({"serverAddress": GRPC_SERVER})
    return http("GET", f"/api/case/run/{case_id}?{q}", token=token, csrf=csrf)["data"]


def debug_send(token, csrf, parsed, request_json='{"greeting":"MeterSphere"}'):
    return http(
        "POST",
        "/api/grpc/debug/send",
        {
            "serverAddress": GRPC_SERVER,
            "packageName": parsed.get("packageName"),
            "serviceName": "HelloService",
            "methodName": "SayHello",
            "descriptorContent": parsed["descriptorContent"],
            "requestJson": request_json,
            "metadata": [],
            "timeout": 60000,
        },
        token,
        csrf,
    )["data"]


def main():
    r = {}
    token, csrf = login()
    print("login ok")

    parsed = parse_proto(token, csrf, HELLO_PROTO_V1)
    template = parsed["services"][0]["methods"][0].get("requestJsonTemplate")
    r["9A-1_parse_template"] = '"greeting"' in (template or "")
    print("9A-1 parse template:", template)

    send = debug_send(token, csrf, parsed)
    r["9A-1_debug"] = send.get("success") is True
    print("9A-1 debug:", send.get("success"), send.get("grpcStatus"))

    def_id, def_detail = add_definition(token, csrf, HELLO_PROTO_V1, "9a1")
    persisted = def_detail["grpcDefinition"]["services"][0]["methods"][0].get("requestJsonTemplate")
    r["9A-1_def_template_persisted"] = bool(persisted)
    print("9A-1 def template:", persisted)

    case_id, _ = add_case(token, csrf, def_id, '{"greeting":"MeterSphere"}')
    run = run_case(token, csrf, case_id)
    r["9A-1_case_save"] = True
    r["9A-1_case_run"] = run.get("success") is True
    r["9A-1_report"] = bool(run.get("reportId"))
    print("9A-1 run:", run.get("success"), "report", run.get("reportId"))

    # 9B: create case on v1 BEFORE upgrading definition
    def_v1_id, def_v1 = add_definition(token, csrf, HELLO_PROTO_V1, "9b")
    p1 = def_v1["grpcDefinition"]["protoId"]
    v1 = (def_v1["grpcDefinition"].get("protoVersion") or {}).get("versionLabel")
    r["9B_v1"] = v1 == "v1"
    print("9B v1:", v1, p1)

    old_case, old_detail = add_case(token, csrf, def_v1_id, '{"greeting":"old-v1"}')
    bound_p1 = old_detail["grpcCaseConfig"]["protoId"]
    r["9B_case_bind_proto"] = bound_p1 == p1
    print("9B case bind:", bound_p1)

    def_v2 = update_definition(token, csrf, def_v1_id, HELLO_PROTO_V2_ADD)
    p2 = def_v2["grpcDefinition"]["protoId"]
    v2 = (def_v2["grpcDefinition"].get("protoVersion") or {}).get("versionLabel")
    hist = def_v2["grpcDefinition"].get("protoVersionHistory") or []
    r["9B_v2_history"] = v2 == "v2" and len(hist) >= 2 and p1 != p2
    r["9B_def_version_fields"] = def_v2["grpcDefinition"].get("protoVersion") and def_v2["grpcDefinition"].get("protoVersionHistory")
    print("9B v2:", v2, "history", len(hist), "protoVersion fields ok")

    after = http("GET", f"/api/case/get-detail/{old_case}", token=token, csrf=csrf)["data"]
    cfg = after["grpcCaseConfig"]
    r["9B_no_auto_upgrade"] = cfg["protoId"] == p1 and cfg.get("protoVersionOutdated") is True
    print("9B outdated:", cfg.get("protoVersionOutdated"), "protoId:", cfg["protoId"], "active:", p2)

    old_run = run_case(token, csrf, old_case)
    r["9B_old_run"] = old_run.get("success") is True
    print("9B old case run:", old_run.get("success"))

    # 9C-1 impacts (valid proto updates)
    d_type = update_definition(token, csrf, def_v1_id, HELLO_PROTO_V2_TYPE)
    imp_type = (d_type["grpcDefinition"].get("protoChangeImpact") or {}).get("impactLevel")
    r["9C-1_type_breaking"] = imp_type == "BREAKING"
    print("9C-1 field type:", imp_type)

    d_add = update_definition(token, csrf, def_v1_id, HELLO_PROTO_V2_ADD)
    imp_add = (d_add["grpcDefinition"].get("protoChangeImpact") or {}).get("impactLevel")
    r["9C-1_field_warning"] = imp_add in ("WARNING", "INFO")
    print("9C-1 field add:", imp_add)

    d_method = update_definition(token, csrf, def_v1_id, HELLO_PROTO_V2_METHOD, method="SayGoodbye")
    imp_method = (d_method["grpcDefinition"].get("protoChangeImpact") or {}).get("impactLevel")
    r["9C-1_method_breaking"] = imp_method == "BREAKING"
    print("9C-1 method change:", imp_method)

    case_imp = cfg.get("protoChangeImpact") or after["grpcCaseConfig"].get("protoChangeImpact")
    r["9C-1_case_impact"] = case_imp is not None
    r["9C-1_non_blocking"] = old_run.get("success") is True
    print("9C-1 case impact present:", case_imp is not None)

    exist_run = run_case(token, csrf, EXISTING_CASE)
    r["V1_existing_case_run"] = exist_run.get("success") is True
    r["V1_parse"] = bool(parsed.get("services"))
    r["V1_debug"] = r["9A-1_debug"]
    r["V1_case_save"] = r["9A-1_case_save"]
    r["V1_case_run"] = r["9A-1_case_run"]
    r["V1_report"] = r["9A-1_report"]
    http_defs = http("POST", "/api/definition/page", {"projectId": PROJECT, "current": 1, "pageSize": 10, "protocol": "HTTP"}, token, csrf)
    r["V1_http_page"] = http_defs.get("data") is not None
    print("V1 existing case run:", exist_run.get("success"))

    print("\n=== SUMMARY ===")
    ok = True
    for k, v in r.items():
        print(f"{k}: {'PASS' if v else 'FAIL'}")
        ok = ok and v
    print("OVERALL:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
