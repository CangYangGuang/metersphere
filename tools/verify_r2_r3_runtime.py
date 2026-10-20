#!/usr/bin/env python3
"""Runtime verification for R2 scenario debug and R3 test plan gRPC execution on verify4 (8085)."""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8085"
PROJECT = "100001100001"
ORG = "100001"
PLAN_ID = "1001139712221184"
CASE1 = "1513376634923655168"
DEF1 = "1513376497484701696"
ENV_ID = "101947563782963516"
GRPC_SERVER = "grpcb.in:9000"
NOW = int(time.time() * 1000)


def http(method, path, body=None, token=None, csrf=None):
    headers = {
        "Content-Type": "application/json",
        "ORGANIZATION": ORG,
        "PROJECT": PROJECT,
    }
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
        err = e.read().decode()
        raise RuntimeError(f"HTTP {e.code} {path}: {err}") from e


def multipart_update_env(token, csrf, env_detail):
    boundary = "----MsGrpcVerifyBoundary"
    env_request = {
        "id": env_detail["id"],
        "projectId": env_detail["projectId"],
        "name": env_detail["name"],
        "description": env_detail.get("description") or "",
        "config": env_detail.get("config") or {},
    }
    cfg = env_request["config"]
    common = cfg.get("commonVariables") or []
    if not any(v.get("key") == "grpcServerAddress" for v in common):
        common.append(
            {
                "key": "grpcServerAddress",
                "value": GRPC_SERVER,
                "description": "gRPC server for runtime verify",
                "enable": True,
            }
        )
    cfg["commonVariables"] = common
    payload = json.dumps(env_request, ensure_ascii=False)
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="request"\r\n'
        f"Content-Type: application/json\r\n\r\n"
        f"{payload}\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "ORGANIZATION": ORG,
        "PROJECT": PROJECT,
        "X-AUTH-TOKEN": token,
        "CSRF-TOKEN": csrf,
    }
    req = urllib.request.Request(BASE + "/project/environment/update", data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


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


def mysql(sql):
    cmd = ["docker", "exec", "metersphere-grpc-verify4", "mysql", "-uroot", "test", "-N", "-e", sql]
    return subprocess.check_output(cmd, text=True).strip()


def ensure_env_grpc(token, csrf):
    env = http("GET", f"/project/environment/get/{ENV_ID}", token=token, csrf=csrf)["data"]
    multipart_update_env(token, csrf, env)
    env2 = http("GET", f"/project/environment/get/{ENV_ID}", token=token, csrf=csrf)["data"]
    vars_ = env2.get("config", {}).get("commonVariables") or []
    return any(v.get("key") == "grpcServerAddress" and v.get("value") == GRPC_SERVER for v in vars_)


def create_case2(token, csrf):
    body = {
        "name": f"grpc-scenario-step2-var-{NOW}",
        "priority": "P0",
        "status": "PROCESSING",
        "projectId": PROJECT,
        "apiDefinitionId": DEF1,
        "request": {},
        "grpcCaseConfig": {
            "requestJson": '{"greeting":"${grpcReply}"}',
            "metadata": [],
            "timeout": 60000,
            "assertionConfig": {
                "items": [
                    {
                        "type": "GRPC_STATUS",
                        "name": "status-ok",
                        "condition": "EQUALS",
                        "expectedValue": "OK",
                        "enable": True,
                    },
                    {
                        "type": "JSON_PATH",
                        "name": "reply-contains",
                        "expression": "$.reply",
                        "condition": "CONTAINS",
                        "expectedValue": "MeterSphere",
                        "enable": True,
                    },
                ]
            },
        },
    }
    return http("POST", "/api/case/add", body, token, csrf)["data"]["id"]


def scenario_debug(token, csrf, case2):
    report_id = str(NOW + 1)
    step_cfg = {"protocol": "GRPC", "serverAddress": GRPC_SERVER}
    steps = [
        {
            "id": "step-grpc-1",
            "uniqueId": "step-grpc-1",
            "name": "extract grpcReply",
            "enable": True,
            "stepType": "API_CASE",
            "refType": "REF",
            "resourceId": CASE1,
            "projectId": PROJECT,
            "config": step_cfg,
        },
        {
            "id": "step-grpc-2",
            "uniqueId": "step-grpc-2",
            "name": "use grpcReply",
            "enable": True,
            "stepType": "API_CASE",
            "refType": "REF",
            "resourceId": case2,
            "projectId": PROJECT,
            "config": step_cfg,
        },
    ]
    body = {
        "id": "debug-scenario-unsaved",
        "reportId": report_id,
        "projectId": PROJECT,
        "environmentId": ENV_ID,
        "grouped": False,
        "frontendDebug": True,
        "scenarioConfig": {"variable": {"commonVariables": []}},
        "steps": steps,
        "stepDetails": {},
    }
    result = http("POST", "/api/scenario/debug", body, token, csrf)
    data = result.get("data") or {}
    task_info = data.get("taskInfo") or {}
    task_item = data.get("taskItem") or {}
    time.sleep(12)
    err_log = subprocess.check_output(
        [
            "docker",
            "exec",
            "metersphere-grpc-verify4",
            "sh",
            "-c",
            "grep -F 'gRPC 场景调试执行失败' /opt/metersphere/logs/metersphere/error.log 2>/dev/null | tail -3 || true",
        ],
        text=True,
    ).strip()
    r2_ok = (
        task_info.get("realTime") is True
        and task_item.get("requestCount") == 2
        and report_id not in err_log
    )
    return report_id, result, task_info, task_item, r2_ok


def setup_test_plan(case_id):
    root_id = "api-root-grpc-r3"
    col_id = "col-grpc-r3-001"
    tpc_id = "tpc-grpc-r3-001"
    pool_id = "100001100001"
    mysql(
        f"DELETE FROM test_plan_api_case WHERE id='{tpc_id}'; "
        f"DELETE FROM test_plan_collection WHERE id IN ('{col_id}','{root_id}');"
    )
    mysql(
        f"INSERT INTO test_plan_collection (id,test_plan_id,parent_id,name,type,execute_method,extended,grouped,environment_id,test_resource_pool_id,retry_on_fail,stop_on_fail,create_user,create_time,pos) "
        f"VALUES ('{root_id}','{PLAN_ID}','NONE','API','API','SERIAL',b'1',b'0','{ENV_ID}','{pool_id}',b'0',b'0','admin',{NOW},4096);"
    )
    mysql(
        f"INSERT INTO test_plan_collection (id,test_plan_id,parent_id,name,type,execute_method,extended,grouped,environment_id,test_resource_pool_id,retry_on_fail,stop_on_fail,create_user,create_time,pos) "
        f"VALUES ('{col_id}','{PLAN_ID}','{root_id}','gRPC API','API','SERIAL',b'1',b'0','{ENV_ID}','{pool_id}',b'0',b'0','admin',{NOW},4096);"
    )
    mysql(
        f"INSERT INTO test_plan_api_case (id,test_plan_id,api_case_id,environment_id,last_exec_result,last_exec_report_id,execute_user,create_time,create_user,pos,test_plan_collection_id) "
        f"VALUES ('{tpc_id}','{PLAN_ID}','{case_id}','{ENV_ID}',NULL,NULL,NULL,{NOW},'admin',1,'{col_id}');"
    )
    cfg_cnt = mysql(f"SELECT COUNT(*) FROM test_plan_config WHERE test_plan_id='{PLAN_ID}'")
    if cfg_cnt == "0":
        mysql(
            f"INSERT INTO test_plan_config (test_plan_id,case_run_mode,automatic_status_update,retry_on_fail,retry_times,retry_interval,stop_on_fail,pass_threshold) "
            f"VALUES ('{PLAN_ID}','SERIAL',b'0',b'0',1,1000,b'0',100);"
        )
    return col_id, tpc_id


def run_test_plan(token, csrf):
    body = {"executeId": PLAN_ID, "runMode": "SERIAL", "executionSource": "MANUAL"}
    report_id = http("POST", "/test-plan-execute/single", body, token, csrf)["data"]
    for _ in range(60):
        time.sleep(5)
        row = mysql(
            f"SELECT api_case_execute_result, api_case_execute_report_id FROM test_plan_report_api_case "
            f"WHERE test_plan_report_id='{report_id}' AND api_case_id='{CASE1}' LIMIT 1"
        )
        if row and row != "NULL\tNULL" and not row.startswith("NULL"):
            parts = row.split("\t")
            if parts[0] not in ("", "NULL"):
                return report_id, row
    return report_id, "TIMEOUT"


def main():
    print("=== login ===")
    token, csrf = login()
    print("login ok")

    print("=== ensure environment grpcServerAddress ===")
    env_ok = ensure_env_grpc(token, csrf)
    print("env grpcServerAddress", env_ok)

    print("=== R2: create case2 + scenario debug ===")
    case2 = create_case2(token, csrf)
    print("case2", case2)
    report_id, debug_resp, task_info, task_item, r2_ok = scenario_debug(token, csrf, case2)
    print("debug reportId", report_id)
    print("debug realTime", task_info.get("realTime"), "requestCount", task_item.get("requestCount"))
    print("R2 ok", r2_ok)

    print("=== R3: setup plan + execute ===")
    setup_test_plan(CASE1)
    tp_report, tp_row = run_test_plan(token, csrf)
    print("test plan report", tp_report, "row", tp_row)
    r3_ok = False
    if tp_row != "TIMEOUT":
        parts = tp_row.split("\t")
        exec_result = parts[0] if parts else ""
        api_report = parts[1] if len(parts) > 1 else ""
        r3_ok = exec_result == "SUCCESS" and bool(api_report)
        if api_report:
            detail = mysql(
                f"SELECT status, request_time, code FROM api_report_detail WHERE report_id='{api_report}' LIMIT 1"
            )
            header = mysql(
                f"SELECT status, success_count, error_count, exec_status FROM api_report WHERE id='{api_report}' LIMIT 1"
            )
            log_cnt = mysql(
                f"SELECT COUNT(*) FROM api_report_log WHERE report_id='{api_report}' AND console LIKE '%JMX%'"
            )
            summary = mysql(
                f"SELECT exec_status, result_status FROM test_plan_report WHERE id='{tp_report}' LIMIT 1"
            )
            print("api_report_detail", detail)
            print("api_report", header)
            print("api_report_log jmx errors", log_cnt)
            print("test_plan_report", summary)
            detail_status = detail.split("\t")[0] if detail else ""
            header_status = header.split("\t")[0] if header else ""
            r3_ok = (
                exec_result == "SUCCESS"
                and detail_status == "SUCCESS"
                and header_status == "SUCCESS"
                and log_cnt == "0"
            )
    else:
        print("R3 TIMEOUT")
    print("R3 ok", r3_ok)

    print("=== HTTP regression spot check ===")
    defs = http(
        "POST",
        "/api/definition/page",
        {"projectId": PROJECT, "current": 1, "pageSize": 5, "protocol": "HTTP"},
        token,
        csrf,
    )
    print("http definitions total", defs.get("data", {}).get("total", "n/a"))

    print("=== SUMMARY ===")
    print("R2 scenario debug:", "PASS" if r2_ok else "FAIL")
    print("R3 test plan execute:", "PASS" if r3_ok else "FAIL")


if __name__ == "__main__":
    main()
