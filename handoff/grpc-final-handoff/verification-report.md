# 验证报告摘要

> HEAD: `f7e00fe` | 环境: verify4 @ http://localhost:8085 | 日期: 2026-07-09  
> 详细用例见 `docs/grpc_automation_full_test_cases.md`、`docs/test_cases.md`

## 总览

| 维度 | 结论 |
|---|---|
| 后端单测 | api-test gRPC 包 **PASS**（含 `DefaultStepParserGrpcTest`） |
| 前端 Gate | vue-tsc + eslint **PASS** |
| MCP-Chrome UI 回归 | Phase 0–7 **PASS**（历史记录） |
| 最终收口 BUG | step 重开回显 **PASS**（API）；Proto 上传 **PASS**（代码+Gate） |

---

## Debug 主链路

| 项 | 状态 | 证据 |
|---|---|---|
| 上传 Proto / 解析 | PASS | TC-1-01; definitionId=1525390191026528256 |
| Method 选择 HelloService/SayHello | PASS | UI + API parse |
| requestJson 模板 | PASS | TC-3-01; template `{"greeting":"string"}` |
| 服务端执行 | PASS | TC-1-03; grpcStatus=OK; responseJson OK |
| 本地执行 | PASS | Phase 1 debug |

---

## 保存 / 重开

| 项 | 状态 | 证据 |
|---|---|---|
| 用例保存 | PASS | TC-1-02; caseId=1525390242566135808 |
| 用例重开 | PASS | service/method/requestJson/descriptor 保留 |
| 调试保存 | PASS | 9d4a767 / f96a24a 修复链 |
| 场景 gRPC 步骤重开 | **PASS** | GET `/api/scenario/step/get/178349961167700000` → code=100200; grpcCaseConfig 完整 |

---

## 另存为接口

| 项 | 状态 | 证据 |
|---|---|---|
| 调试页另存为 API 定义 | PASS | 0f0610c fix(grpc): pass grpc state when saving as api |
| gRPC 配置传递 | PASS | ab864c6 / 242ec0d |

---

## 同步添加用例

| 项 | 状态 | 证据 |
|---|---|---|
| 从定义同步用例 | PASS | GrpcCasePersistenceService 单测 |
| grpcCaseConfig 完整 | PASS | serverAddress/settings/pre/post |

---

## 用例运行

| 项 | 状态 | 证据 |
|---|---|---|
| 单用例 run | PASS | TC-1-04; reportId=1525390259746004992 |
| grpcStatus/grpcCode | PASS | OK / 0 |
| 失败响应展示 | PASS | 1a847e5 fix |

---

## 报告深链

| 项 | 状态 | 证据 |
|---|---|---|
| 报告列表打开 | PASS | TC-1-05 |
| 步骤详情 gRPC 字段 | PASS | TC-1-06 FIXED; GrpcReportDetailPanel |
| URL 深链 | PASS | 457af15 fix(report) |

---

## 场景自定义 gRPC

| 项 | 状态 | 证据 |
|---|---|---|
| 协议选择 GRPC | PASS | customApiDrawer |
| Proto 上传点击 | PASS | grpcServiceDefinitionTab pointer-events-none (e90c35e) |
| always-show-response=false | PASS | scenarioGrpcCustomEditor (f7e00fe) |
| 保存场景 | PASS | blob 含 grpcCaseConfig |
| 重开步骤回显 | **PASS** | stepId=178349961167700000; scenarioId=1546229475426025472 |
| 服务端执行 | PARTIAL | task-runner 回调环境限制 |

---

## 场景导入 gRPC

| 项 | 状态 | 证据 |
|---|---|---|
| 导入抽屉 GRPC 协议 | PASS | 47d1f67; TC-016 |
| 引用 gRPC 用例 | PASS | TC-2-01 |

---

## Settings

| 项 | 状态 | 证据 |
|---|---|---|
| Runtime Settings Tab | PASS | c74423c |
| Reflection 自动发现 | PASS | Phase 3 TC-3-03~05 |
| TLS 模式切换 | PASS | UI 可选；自定义证书上传 **未支持** |

---

## 前置 / 后置

| 项 | 状态 | 证据 |
|---|---|---|
| case 执行链前置 | PASS | c74423c 单测 |
| case 执行链后置 | PASS | c74423c 单测 |
| flat debug send 前置 | SKIP | 仅 Settings 生效 — 见 known-limitations |

---

## 失败响应

| 项 | 状态 | 证据 |
|---|---|---|
| grpcStatus != OK 展示 | PASS | 1a847e5 |
| errorMessage / grpcCode | PASS | 报告详情面板 |

---

## Proto bundle

| 项 | 状态 | 证据 |
|---|---|---|
| 多 Proto 上传 | PASS | 依赖 Proto 上传 |
| bundle 版本管理 | PASS | GrpcProtoVersionService 单测 |
| 解析错误详情 | PASS | 82f6888 fix |

---

## 测试数据索引

| 用途 | ID / 值 |
|---|---|
| 场景（click-flow） | scenarioId=`1546229475426025472`; stepId=`178349961167700000` |
| V1.0 用例 | caseId=`1525390242566135808` |
| V1.0 报告 | reportId=`1525390259746004992` |
| Mock 地址 | `host.docker.internal:50051` |
| requestJson 样例 | `{"greeting":"scene-ui-click-flow"}` |

---

## 未在本轮复验项

| 项 | 原因 |
|---|---|
| verify4 容器 | 打包时 Docker 未运行；需接手方重建 |
| MCP-Chrome 全链路 | 建议重建后按 P0 用例复跑 |
| task-runner 异步 UI | 环境网络限制 |
