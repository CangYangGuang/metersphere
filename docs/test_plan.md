# MeterSphere gRPC UI Regression Test Plan

## Purpose

Use Cursor Agent with MCP-Chrome or Browser Automation to verify MeterSphere gRPC V1.0 and V1.1 features in phases.

Current baseline:

```text
HEAD: cd1c6d7 - docs(grpc): verify grpc report detail runtime
```

Covered scope:

* V1.0 gRPC definition, case, debug, run, report, scenario, test plan
* 9A requestJsonTemplate
* 9B protoVersion
* 9C protoChangeImpact
* 9D multi proto / import parsing
* 9D-2 proto bundle persistence
* 9E Reflection describe + UI
* 9F Reflection descriptor-only persistence / version / impact / guard
* R4 gRPC report detail fix

## Execution rules

1. Execute one phase at a time.
2. Agent updates only `grpc_cases_status.md` after each phase.
3. Do not enter 9F-3 in this verification round.
4. Do not push.
5. Do not submit temporary screenshots, logs, tools, target, dist, node_modules, or nul.
6. If MCP-Chrome is unstable, switch to Browser Automation.
7. If a major issue is found, stop and report it.

## Phases

| Phase   | Name                        | Goal                                            |
| ------- | --------------------------- | ----------------------------------------------- |
| Phase 0 | Environment and MCP smoke   | Verify MCP, browser, verify4, login, git status |
| Phase 1 | V1.0 gRPC main chain        | Definition, case, debug, run, report            |
| Phase 2 | V1.0 scenario and test plan | Scenario debug and test plan run                |
| Phase 3 | V1.1 9A / 9B / 9C           | Template, version, change impact                |
| Phase 4 | V1.1 9D / 9D-2              | Multi proto, import, bundle                     |
| Phase 5 | V1.1 9E / 9F                | Reflection UI, save, version, impact, guard     |
| Phase 6 | R4 report detail            | gRPC report detail and HTTP report regression   |
| Phase 7 | Final summary               | Decide whether final tag / push is allowed      |

## Test data

### hello.proto

```proto
syntax = "proto3";

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
```

Target server:

```text
grpcb.in:9000
```

### Version impact data

v1:

```proto
message HelloRequest {
  string greeting = 1;
}
```

v2:

```proto
message HelloRequest {
  int32 greeting = 1;
}
```

Expected: BREAKING.

### Guard addresses

```text
localhost:50051
127.0.0.1:50051
0.0.0.0:50051
[::1]:50051
169.254.169.254:80
192.168.1.10:50051
10.0.0.1:50051
172.16.0.1:50051
invalid-address
timeout overflow
metadata overflow
```

## Agent phase prompt template

```text
Start Phase X of the MeterSphere gRPC UI regression test plan.

Requirements:
1. Execute only Phase X.
2. Use MCP-Chrome / Browser Automation.
3. Do not develop new features.
4. Only fix small UI / null-safety / text / field mapping bugs found in this phase.
5. Do not perform DB migration.
6. Do not enter 9F-3.
7. Do not push.
8. Update `grpc_cases_status.md` for the corresponding cases.
9. Record key IDs: definitionId, protoId, caseId, reportId, stepId.
10. Record console errors, HTTP 500, blank pages, and SKIP reasons.
11. Stop if MCP-Chrome and Browser Automation are both unavailable.
```
