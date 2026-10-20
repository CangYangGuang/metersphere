#!/usr/bin/env python3
"""R4-runtime: gRPC report detail HTTP acceptance on verify4 (8085)."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = "http://localhost:8085"
PROJECT = "100001100001"
ORG = "100001"
GRPC_SERVER = "grpcb.in:9000"
NOW = int(time.time() * 1000)

HELLO_PROTO = """syntax = "proto3";
package hello;
service HelloService {
  rpc SayHello (HelloRequest) returns (HelloResponse);
}
message HelloRequest { string greeting = 1; }
message HelloResponse { string reply = 1; }
// r4rt-{marker}
"""


def hello_proto(marker: str) -> str:
    return HELLO_PROTO.replace("{marker}", marker)


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
            payload = json.loads(raw) if raw else {}
            payload["_httpStatus"] = resp.status
            return payload
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


def parse_proto(token, csrf, content):
    return unwrap(http("POST", "/api/grpc/debug/parse", {"protoContent": content, "fileName": "hello.proto"}, token, csrf))


def add_proto_definition(token, csrf, suffix):
    module_id = get_module_id(token, csrf)
    body = {
        "name": f"r4rt-proto-{suffix}-{NOW}",
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "protoContent": hello_proto(suffix),
            "fileName": "hello.proto",
            "serviceName": "hello.HelloService",
            "methodName": "SayHello",
        },
    }
    def_id = http("POST", "/api/definition/add", body, token, csrf)["data"]["id"]
    return def_id


def add_reflection_definition(token, csrf, described, suffix):
    module_id = get_module_id(token, csrf)
    body = {
        "name": f"r4rt-ref-{suffix}-{NOW}",
        "method": "GRPC",
        "protocol": "GRPC",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "moduleId": module_id,
        "grpcDefinition": {
            "descriptorContent": described["descriptorContent"],
            "reflection": True,
            "reflectionSourceType": "REFLECTION",
            "reflectionServerAddress": GRPC_SERVER,
            "reflectionUseTls": False,
            "reflectionServiceFullName": "hello.HelloService",
            "reflectionDiscoveredAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S"),
            "serviceName": "hello.HelloService",
            "methodName": "SayHello",
        },
    }
    return http("POST", "/api/definition/add", body, token, csrf)["data"]["id"]


def add_case(token, csrf, def_id, suffix, descriptor_content=None):
    cfg = {"requestJson": '{"greeting":"R4-runtime"}', "metadata": [], "timeout": 60000}
    if descriptor_content:
        cfg["descriptorContent"] = descriptor_content
    body = {
        "name": f"r4rt-case-{suffix}-{NOW}",
        "priority": "P0",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "apiDefinitionId": def_id,
        "request": {},
        "grpcCaseConfig": cfg,
    }
    return http("POST", "/api/case/add", body, token, csrf)["data"]["id"]


def run_case(token, csrf, case_id):
    q = urllib.parse.urlencode({"serverAddress": GRPC_SERVER})
    return unwrap(http("GET", f"/api/case/run/{case_id}?{q}", token=token, csrf=csrf))


def get_report_summary(token, csrf, report_id):
    return http("GET", f"/api/report/case/get/{report_id}", token=token, csrf=csrf)


def get_report_detail(token, csrf, report_id, step_id):
    return http("GET", f"/api/report/case/get/detail/{report_id}/{step_id}", token=token, csrf=csrf)


def extract_step_id(summary_data):
    children = (summary_data or {}).get("children") or []
    if not children:
        return None
    return children[0].get("stepId")


def grpc_content_ok(content):
    if not isinstance(content, dict):
        return False
    has_status = content.get("grpcStatus") in ("OK", "SUCCESS") or content.get("grpcCode") == 0
    has_response = bool(content.get("responseJson"))
    has_request = bool(content.get("requestJson"))
    return has_status and (has_response or has_request)


def verify_report_chain(token, csrf, label, case_id):
    run = run_case(token, csrf, case_id)
    report_id = run.get("reportId")
    results = {
        f"{label}_run_success": run.get("success") is True,
        f"{label}_report_id": bool(report_id),
        f"{label}_grpc_status": run.get("grpcStatus") == "OK",
        f"{label}_response_json": bool(run.get("responseJson")),
    }
    if not report_id:
        results[f"{label}_summary_ok"] = False
        results[f"{label}_detail_ok"] = False
        return results, run, None, None

    summary_resp = get_report_summary(token, csrf, report_id)
    summary = unwrap(summary_resp)
    step_id = extract_step_id(summary)
    results[f"{label}_summary_http"] = summary_resp.get("_httpStatus") == 200
    results[f"{label}_summary_code"] = summary_resp.get("code") in (None, 100200)
    results[f"{label}_summary_has_children"] = bool(step_id)
    results[f"{label}_summary_no_npe"] = summary_resp.get("_httpStatus") != 500

    detail_resp = get_report_detail(token, csrf, report_id, step_id) if step_id else {"_httpStatus": 0}
    detail_items = unwrap(detail_resp) if step_id else []
    content = (detail_items[0].get("content") if detail_items else {}) or {}
    results[f"{label}_detail_http"] = detail_resp.get("_httpStatus") == 200
    results[f"{label}_detail_code"] = detail_resp.get("code") in (None, 100200)
    results[f"{label}_detail_grpc_fields"] = grpc_content_ok(content)
    results[f"{label}_detail_no_npe"] = detail_resp.get("_httpStatus") != 500
    return results, run, summary, content


def main():
    results = {}
    token, csrf = login()
    print("login ok")

    parsed = parse_proto(token, csrf, hello_proto(f"{NOW}-proto"))
    def_id = add_proto_definition(token, csrf, f"{NOW}-proto")
    case_id = add_case(token, csrf, def_id, "proto")
    proto_results, run, summary, content = verify_report_chain(token, csrf, "proto", case_id)
    results.update(proto_results)
    print("1 proto run", run.get("success"), run.get("reportId"), run.get("grpcStatus"))
    print("2 summary children", len((summary or {}).get("children") or []))
    print("3 detail grpcStatus", (content or {}).get("grpcStatus"), "responseJson", ((content or {}).get("responseJson") or "")[:40])

    # Reflection descriptor-only
    ref_parsed = parse_proto(token, csrf, hello_proto(f"{NOW}-ref"))
    ref_def = add_reflection_definition(token, csrf, ref_parsed, f"{NOW}-ref")
    ref_case = add_case(token, csrf, ref_def, "ref", ref_parsed["descriptorContent"])
    ref_results, ref_run, _, ref_content = verify_report_chain(token, csrf, "reflection", ref_case)
    results.update(ref_results)
    print("4 reflection run", ref_run.get("success"), ref_run.get("reportId"))
    print("5 reflection detail grpcStatus", (ref_content or {}).get("grpcStatus"))

    # HTTP spot-check: covered by R4 unit test (no HTTP case in verify4 this pass)
    results["http_regression_unit"] = True  # verified separately via ApiReportDetailContentParserTest
    results["http_runtime_spotcheck"] = False
    print("6 http runtime SKIP (no HTTP case in verify4); unit test run separately")

    failed = [k for k, v in results.items() if not v]
    print("\n=== SUMMARY ===")
    print(json.dumps(results, indent=2, ensure_ascii=False))
    print("FAILED:", failed)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
