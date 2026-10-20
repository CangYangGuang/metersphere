#!/usr/bin/env python3
"""9F-4 runtime acceptance on verify4 (8085): Reflection descriptor-only persistence."""
import hashlib
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = "http://localhost:8085"
PROJECT = "100001100001"
ORG = "100001"
REFLECTION_SERVER = "grpcb.in:9000"
GRPC_SERVER = REFLECTION_SERVER
NOW = int(time.time() * 1000)

HELLO_PROTO = """syntax = "proto3";
package hello;
service HelloService {
  rpc SayHello (HelloRequest) returns (HelloResponse);
}
message HelloRequest { string greeting = 1; }
message HelloResponse { string reply = 1; }
// 9f4-{marker}
"""

def hello_proto(marker: str, type_change: bool = False) -> str:
    body = HELLO_PROTO.replace("{marker}", marker)
    if type_change:
        body = body.replace("string greeting = 1;", "int32 greeting = 1;")
    return body


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
            raw = resp.read().decode()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {"code": e.code, "message": raw}
        payload["_httpStatus"] = e.code
        return payload


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


def unwrap(data):
    if isinstance(data, dict) and "data" in data and data.get("data") is not None:
        return data["data"]
    return data


def get_module_id(token, csrf):
    return http("POST", "/api/definition/module/tree", {"projectId": PROJECT}, token, csrf)["data"][0]["id"]


def describe_reflection(token, csrf, server_address=REFLECTION_SERVER, timeout=15000):
    return unwrap(http(
        "POST",
        "/api/grpc/debug/reflection/describe",
        {"serverAddress": server_address, "timeout": timeout, "useTls": False},
        token,
        csrf,
    ))


def parse_proto(token, csrf, content, file_name="hello.proto"):
    return unwrap(http("POST", "/api/grpc/debug/parse", {"protoContent": content, "fileName": file_name}, token, csrf))


def reflection_def_body(name, module_id, described, service, method, server_address=REFLECTION_SERVER):
    return {
        "name": name,
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "descriptorContent": described["descriptorContent"],
            "reflection": True,
            "reflectionSourceType": "REFLECTION",
            "reflectionServerAddress": server_address,
            "reflectionUseTls": False,
            "reflectionServiceFullName": service,
            "reflectionDiscoveredAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
            "serviceName": service,
            "methodName": method,
        },
    }


def add_reflection_definition(token, csrf, described, suffix, service, method):
    module_id = get_module_id(token, csrf)
    body = reflection_def_body(f"9f4-{suffix}-{NOW}", module_id, described, service, method)
    resp = http("POST", "/api/definition/add", body, token, csrf)
    if not resp.get("data"):
        raise RuntimeError(f"add definition failed: {resp}")
    def_id = resp["data"]["id"]
    detail = unwrap(http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf))
    return def_id, detail


def update_reflection_definition(token, csrf, def_id, described, service, method, server_address=REFLECTION_SERVER):
    detail = unwrap(http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf))
    body = reflection_def_body(detail["name"], detail["moduleId"], described, service, method, server_address)
    body["id"] = def_id
    http("POST", "/api/definition/update", body, token, csrf)
    return unwrap(http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf))


def add_case(token, csrf, def_id, request_json, descriptor_content=None, suffix=""):
    body = {
        "name": f"9f4-case-{suffix}-{NOW}",
        "priority": "P0",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "apiDefinitionId": def_id,
        "request": {},
        "grpcCaseConfig": {
            "requestJson": request_json,
            "metadata": [],
            "timeout": 60000,
        },
    }
    if descriptor_content:
        body["grpcCaseConfig"]["descriptorContent"] = descriptor_content
    resp = http("POST", "/api/case/add", body, token, csrf)
    if not resp.get("data"):
        raise RuntimeError(f"add case failed: {resp}")
    case_id = resp["data"]["id"]
    return case_id, unwrap(http("GET", f"/api/case/get-detail/{case_id}", token=token, csrf=csrf))


def run_case(token, csrf, case_id, server=GRPC_SERVER):
    q = urllib.parse.urlencode({"serverAddress": server})
    return unwrap(http("GET", f"/api/case/run/{case_id}?{q}", token=token, csrf=csrf))


def debug_send(token, csrf, descriptor_content, service, method, request_json):
    return unwrap(http(
        "POST",
        "/api/grpc/debug/send",
        {
            "serverAddress": GRPC_SERVER,
            "serviceName": service,
            "methodName": method,
            "descriptorContent": descriptor_content,
            "requestJson": request_json,
            "metadata": [],
            "timeout": 60000,
        },
        token,
        csrf,
    ))


def guard_rejects(token, csrf, server_address):
    resp = http(
        "POST",
        "/api/grpc/debug/reflection/describe",
        {"serverAddress": server_address, "timeout": 5000},
        token,
        csrf,
    )
    ok = resp.get("_httpStatus") in (400, 403, 500) or resp.get("code") not in (None, 100200)
    msg = str(resp.get("message") or resp.get("messageDetail") or "")
    no_npe = "NullPointerException" not in msg
    return ok and no_npe, resp


def add_definition_proto(token, csrf, proto_content, suffix):
    module_id = get_module_id(token, csrf)
    body = {
        "name": f"9f4-{suffix}-{NOW}",
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "protoContent": proto_content,
            "fileName": "hello.proto",
            "serviceName": "hello.HelloService",
            "methodName": "SayHello",
        },
    }
    def_id = http("POST", "/api/definition/add", body, token, csrf)["data"]["id"]
    detail = unwrap(http("GET", f"/api/definition/get-detail/{def_id}", token=token, csrf=csrf))
    return def_id, detail


def main():
    results = {}
    token, csrf = login()
    print("login ok")

    # Acceptance 1
    described = None
    live_error = None
    try:
        live = describe_reflection(token, csrf)
        if live.get("descriptorContent") and live.get("services"):
            described = live
            results["1_live_reflection_http"] = True
            print("1 reflection HTTP OK")
        else:
            live_error = live
    except Exception as exc:
        live_error = str(exc)

    if described is None:
        described = parse_proto(token, csrf, hello_proto(str(NOW)))
        results["1_live_reflection_http"] = False
        print("1 reflection HTTP SKIP:", str(live_error)[:120] if live_error else "empty response")
    else:
        results["1_live_reflection_http"] = True
        print("1 reflection HTTP OK", (services[0].get("fullName") if (services := described.get("services")) else ""))

    # Persistence / invoke chain uses hello descriptor against grpcb.in (supported service)
    hello_described = parse_proto(token, csrf, hello_proto(str(NOW)))
    accept1 = described if described is not None and results.get("1_live_reflection_http") else hello_described
    services = hello_described.get("services") or []
    methods = (services[0].get("methods") if services else []) or []
    accept1_services = accept1.get("services") or []
    accept1_methods = (accept1_services[0].get("methods") if accept1_services else []) or []
    results["1_describe_services"] = len(accept1_services) > 0
    results["1_describe_methods"] = len(accept1_methods) > 0
    results["1_descriptor_content"] = bool(accept1.get("descriptorContent"))
    template = accept1_methods[0].get("requestJsonTemplate") if accept1_methods else ""
    results["1_request_json_template"] = bool(template)
    service_name = (services[0].get("fullName") if services else None) or "hello.HelloService"
    method_name = methods[0].get("name") if methods else "SayHello"
    req_json = '{"greeting":"MeterSphere"}'
    print("1 service", service_name, method_name)

    # Acceptance 6 guard
    for addr in [
        "localhost:50051", "127.0.0.1:50051", "0.0.0.0:50051", "[::1]:50051",
        "169.254.169.254:80", "192.168.1.10:50051", "10.0.0.1:50051", "172.16.0.1:50051",
    ]:
        ok, resp = guard_rejects(token, csrf, addr)
        results["6_guard_" + addr.replace(":", "_").replace("[", "").replace("]", "")] = ok
        print("6", addr, ok, (resp.get("message") or "")[:60])

    invalid = http("POST", "/api/grpc/debug/reflection/describe", {"serverAddress": "bad-host"}, token, csrf)
    results["6_guard_invalid_address"] = invalid.get("_httpStatus") in (400, 500) or invalid.get("code") not in (None, 100200)
    timeout_high = http("POST", "/api/grpc/debug/reflection/describe", {"serverAddress": "8.8.8.8:50051", "timeout": 90000}, token, csrf)
    results["6_guard_timeout_high"] = timeout_high.get("_httpStatus") in (400, 500) or timeout_high.get("code") not in (None, 100200)
    meta_resp = http("POST", "/api/grpc/debug/reflection/describe", {"serverAddress": "8.8.8.8:50051", "metadata": [{"key": f"k{i}", "value": "v" * 100} for i in range(25)], "timeout": 5000}, token, csrf)
    results["6_guard_metadata_limit"] = meta_resp.get("_httpStatus") in (400, 500) or meta_resp.get("code") not in (None, 100200)

    # Acceptance 2+3
    def_id, def_detail = add_reflection_definition(token, csrf, hello_described, "save", service_name, method_name)
    grpc_def = def_detail["grpcDefinition"]
    expected_hash = hashlib.sha256(hello_described["descriptorContent"].encode()).hexdigest()
    file_key = f"reflection://{REFLECTION_SERVER}"
    results["2_reflection_flag"] = grpc_def.get("reflection") is True
    results["2_no_proto_content"] = not grpc_def.get("protoContent")
    results["2_descriptor_persisted"] = bool(grpc_def.get("descriptorContent"))
    results["2_file_name"] = grpc_def.get("fileName") == file_key
    pv = grpc_def.get("protoVersion") or {}
    results["2_content_hash"] = pv.get("contentHash") == expected_hash
    results["3_services_echo"] = len(grpc_def.get("services") or []) > 0
    results["3_template_echo"] = bool((grpc_def.get("services") or [{}])[0].get("methods", [{}])[0].get("requestJsonTemplate"))
    results["3_proto_version"] = bool(grpc_def.get("protoVersion"))
    results["3_proto_history"] = isinstance(grpc_def.get("protoVersionHistory"), list)
    results["3_impact_no_error"] = (grpc_def.get("protoChangeImpact") or {}).get("impactLevel") is not None
    print("2/3", def_id, grpc_def.get("fileName"), pv.get("versionLabel"))

    # Acceptance 4
    debug = debug_send(token, csrf, grpc_def["descriptorContent"], service_name, method_name, req_json)
    results["4_debug_success"] = debug.get("success") is True
    print("4 debug", debug.get("success"), debug.get("grpcStatus"), debug.get("errorMessage", ""))

    case_id, case_detail = add_case(token, csrf, def_id, req_json, grpc_def["descriptorContent"], "main")
    cfg = case_detail["grpcCaseConfig"]
    results["4_case_proto_bind"] = bool(cfg.get("protoId"))
    results["4_case_descriptor"] = bool(cfg.get("descriptorContent"))
    results["4_case_echo"] = bool(cfg.get("serviceName")) and bool(cfg.get("methodName"))
    run = run_case(token, csrf, case_id)
    results["4_run_success"] = run.get("success") is True
    results["4_report_id"] = bool(run.get("reportId"))
    results["4_report_content"] = run.get("success") is True and bool(run.get("reportId")) and (
        run.get("grpcStatus") == "OK" or run.get("responseJson")
    )
    print("4 run", run.get("success"), run.get("reportId"))

    # Acceptance 5
    p1 = grpc_def["protoId"]
    old_case_id, old_case = add_case(token, csrf, def_id, req_json, grpc_def["descriptorContent"], "old")
    old_proto = old_case["grpcCaseConfig"]["protoId"]
    changed = parse_proto(token, csrf, hello_proto(f"{NOW}-v2", type_change=True))
    def_v2 = update_reflection_definition(token, csrf, def_id, changed, service_name, method_name)
    p2 = def_v2["grpcDefinition"]["protoId"]
    v2 = (def_v2["grpcDefinition"].get("protoVersion") or {}).get("versionLabel")
    hist = def_v2["grpcDefinition"].get("protoVersionHistory") or []
    results["5_v2_created"] = p1 != p2 and len(hist) >= 2 and (def_v2["grpcDefinition"].get("protoVersion") or {}).get("versionNo", 0) > (pv.get("versionNo") or 0)
    after_case = unwrap(http("GET", f"/api/case/get-detail/{old_case_id}", token=token, csrf=csrf))
    cfg2 = after_case["grpcCaseConfig"]
    results["5_no_auto_upgrade"] = cfg2["protoId"] == old_proto == p1
    results["5_outdated"] = cfg2.get("protoVersionOutdated") is True
    impact2 = cfg2.get("protoChangeImpact") or {}
    results["5_impact_level"] = impact2.get("impactLevel") in ("BREAKING", "WARNING", "INFO")
    results["5_body_unchanged"] = cfg2.get("requestJson") == req_json
    print("5", v2, cfg2.get("protoVersionOutdated"), impact2.get("impactLevel"))

    # Acceptance 7 regression
    proto_parsed = parse_proto(token, csrf, hello_proto(f"{NOW}-reg"))
    results["7_proto_upload_parse"] = len(proto_parsed.get("services") or []) > 0
    _, def_proto = add_definition_proto(token, csrf, hello_proto(f"{NOW}-reg"), "reg")
    results["7_proto_upload_save"] = bool(def_proto["grpcDefinition"].get("protoContent"))
    send2 = debug_send(token, csrf, proto_parsed["descriptorContent"], "hello.HelloService", "SayHello", req_json)
    results["7_v10_debug"] = send2.get("success") is True
    print("7 regression ok")

    failed = [k for k, v in results.items() if not v]
    print("\n=== SUMMARY ===")
    print(json.dumps(results, indent=2, ensure_ascii=False))
    print("FAILED:", failed)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
