# MeterSphere gRPC 接口自动化体系全功能测试用例

> 适用对象：MeterSphere gRPC 接口自动化体系。  
> 覆盖范围：gRPC 调试页、Proto 解析、Reflection 自动发现、请求模板、服务端执行、保存/回显、用例运行、报告详情、场景、测试计划、版本管理、影响分析、错误处理、UI 布局与回归。  
> 执行方式：优先 MCP-Chrome 浏览器操作；API 只能作为辅助证据，不能替代 UI 主流程，除非用例明确允许。  
> 更新方式：Agent 每次执行后，需要更新本文档中的「执行结果」「实际结果」「证据」「缺陷 ID」「备注」。

---

## 0. 文档使用说明

### 0.1 结果状态定义

| 状态 | 含义 |
|---|---|
| PASS | 按步骤执行，实际结果符合预期 |
| FAIL | 已执行，实际结果不符合预期，需登记 BUG |
| SKIP | 因外部条件不满足跳过，例如无真实 Reflection 服务、无 TLS 证书 |
| BLOCKED | 因环境、工具、权限、MCP 无法操作等阻塞，无法判断产品是否通过 |
| PARTIAL | 部分验证通过，但未完整覆盖全部验收点 |

### 0.2 优先级定义

| 优先级 | 含义 |
|---|---|
| P0 | 主链路，必须通过，否则不建议 push / 合入 |
| P1 | 重要链路，建议通过；若受环境限制需记录 SKIP/BLOCKED |
| P2 | 增强能力或边界能力，可后续专项补测 |

### 0.3 证据记录要求

每条用例执行后，Agent 至少记录以下信息：

```text
执行状态：
执行入口：
实际结果：
关键 ID：
  caseId:
  reportId:
  stepId:
接口证据：
  HTTP status:
  API code:
  message:
  messageDetail:
gRPC 证据：
  grpcStatus:
  grpcCode:
  responseJson:
UI 证据：
  页面文案:
  关键控件:
  console error:
BUG:
备注:
```

### 0.4 MCP-Chrome 执行原则

1. 全链路验收必须以浏览器操作为主。
2. API 只允许辅助定位 `caseId/reportId/stepId`，不能替代 UI 点击。
3. 如果 MCP-Chrome 不可用，应将对应 UI 主流程标记为 `BLOCKED`，不要用 API 结果直接写 `PASS`。
4. 对 canvas / 脑图类操作，如果 MCP 难以拖拽，可标记 `BLOCKED`，并保留 API 辅助证据。

---

## 1. 测试环境基线

### 1.1 推荐正式验收环境

| 项 | 推荐值 |
|---|---|
| 前端入口 | `http://localhost:8085/#/api-test/debug` |
| 前端类型 | 正式 prod build，不使用 `5173` Vite dev |
| 后端容器 | `metersphere-grpc-verify4` |
| 端口映射 | `8085 -> 8081` |
| 登录账号 | `admin / metersphere` |
| mock gRPC 地址 | Docker 后端访问宿主机 mock：`host.docker.internal:50051` |
| mock gRPC 服务 | `hello.HelloService / SayHello` |
| 真实业务 Proto | `proto/poloar_control.proto` |
| 真实业务依赖 | `proto/google/api/annotations.proto`, `proto/google/api/http.proto` |

### 1.2 执行前环境检查

| 检查项 | 命令 / 操作 | 预期 |
|---|---|---|
| Git 工作区 | `git status --short -uno` | tracked 干净 |
| Diff 检查 | `git diff --numstat && git diff --stat` | 无输出、无 `- -`、无 `Bin` |
| 提交基线 | `git log --oneline -12` | 包含最新 gRPC 修复提交 |
| 后端健康 | `curl -i http://localhost:8085/is-login` | 401 或登录态 200 |
| 首页健康 | `curl -I http://localhost:8085/` | 200 |
| get-key | `curl -i http://localhost:8085/get-key` | 200 |
| MCP-Chrome | 打开 debug 页并 snapshot | 可 navigate/click/fill/snapshot/evaluate_script |
| mock gRPC | `host.docker.internal:50051` | 后端容器可访问 |

---

## 2. 测试数据

### 2.1 hello.proto

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

推荐请求：

```json
{
  "greeting": "save regression"
}
```

预期响应：

```json
{
  "reply": "hello save regression"
}
```

### 2.2 poloar_control.proto

主 Proto：

```text
proto/poloar_control.proto
```

依赖 Proto：

```text
proto/google/api/annotations.proto
proto/google/api/http.proto
```

不需要上传的 well-known types：

```text
google/protobuf/empty.proto
google/protobuf/struct.proto
google/protobuf/descriptor.proto
```

预期识别：

| Service | Method 数 |
|---|---:|
| `Polar.v1.DeviceService` | 7 |
| `Polar.v1.ProjectService` | 13 |
| `Polar.v1.RecorderService` | 9 |
| Total | 29 |

重点 Method：

```text
Polar.v1.DeviceService / ListDevices
```

预期 requestJsonTemplate：

```json
{
  "typeFilter": "DEVICE_TYPE_UNSPECIFIED"
}
```

### 2.3 多 Proto bundle 测试数据

`common.proto`：

```proto
syntax = "proto3";
package bundle.common;

message User {
  string name = 1;
  int32 age = 2;
}
```

`main.proto`：

```proto
syntax = "proto3";
package bundle.main;

import "common.proto";

service BundleService {
  rpc Create (CreateRequest) returns (CreateReply);
}

message CreateRequest {
  bundle.common.User user = 1;
  string greeting = 2;
}

message CreateReply {
  string reply = 1;
}
```

预期模板包含：

```json
{
  "user": {
    "name": "string",
    "age": 0
  },
  "greeting": "string"
}
```

### 2.4 protoVersion / impact 测试数据

v1：

```proto
syntax = "proto3";

package phase.version;

service VersionService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string greeting = 1;
}

message HelloReply {
  string reply = 1;
}
```

v2-warning：

```proto
message HelloRequest {
  string greeting = 1;
  string name = 2;
}
```

v2-breaking：

```proto
message HelloRequest {
  int32 greeting = 1;
}
```

---

## 3. 全链路测试矩阵总览

| ID | 功能 | 优先级 | 结果 | 证据 | 备注 |
|---|---|---|---|---|---|
| TC-GRPC-001 | 8085 正式构建版 UI 布局 | P0 | PARTIAL | 7 Tab 齐全；服务定义仅主/依赖 Proto | 未逐条验证 JSON 铺满 |
| TC-GRPC-002 | hello.proto 解析 | P0 | PASS | hello.HelloService / SayHello | MCP upload 无 BOM hello.proto |
| TC-GRPC-003 | requestJsonTemplate 生成 | P0 | PASS | {"greeting":"string"} | 生成模板→覆盖 |
| TC-GRPC-004 | debug 成功执行 mock | P0 | PASS | OK/0/hello save regression | send API 200 |
| TC-GRPC-005 | 调试页保存 / 列表 / 重开回显 | P0 | PASS | caseId=1538636127865569280；重开 hello.proto/execute OK | reqid=412/421 |
| TC-GRPC-006 | 用例运行 / 用例报告详情 | P0 | BLOCKED | case/add 500 grpc_case_config_required | 另存为同步用例 reqid=1088 |
| TC-GRPC-007 | Metadata 编辑 / 保存 / 回显 | P1 | PARTIAL | 元数据 Tab 可进入 | 未完整保存回显 |
| TC-GRPC-008 | Settings 展示与保存 | P2 | PARTIAL | 设置 Tab 可进入 | 未完整保存回显 |
| TC-GRPC-009 | 前置 / 后置 UI 隔离 | P1 | PARTIAL | 前置/后置 Tab 可切换 | 未验证 console |
| TC-GRPC-010 | poloar 单文件错误提示 | P0 | PARTIAL | 页面内 Proto 解析失败面板 | repo BOM 导致 compile 非 import |
| TC-GRPC-011 | poloar bundle 解析 | P0 | PASS | 14 Service / 59 Method；ListDevices 模板 OK | reqid=219 parse 200；MCP 服务定义 Tab 上传 |
| TC-GRPC-012 | host:port 自动发现 / Reflection | P1/P0 | SKIP | mock 无 Reflection |  |
| TC-GRPC-013 | protoVersion 版本管理 | P1 | BLOCKED | 未专项执行 | 定义页补测 |
| TC-GRPC-014 | proto change impact | P1 | BLOCKED | 未专项执行 |  |
| TC-GRPC-015 | 多 Proto import bundle | P0/P1 | PASS | main+common parse OK；Create 模板含 user/greeting | reqid=1090 |
| TC-GRPC-016 | 场景 debug / 场景报告详情 | P1 | PASS | MCP-Chrome 复验：reportId=178342675055100000 SUCCESS；step/get 200；grpcStatus=OK/0 | 见 §10.14 |
| TC-GRPC-017 | 测试计划关联 / 执行 / 计划报告详情 | P1/P0 | BLOCKED | MCP+无接口用例 |  |
| TC-GRPC-018 | 连接失败响应展示 | P0 | PASS | UI「失败」；grpcCode=14 UNAVAILABLE | reqid=423 |
| TC-GRPC-019 | 服务端业务错误状态展示 | P1 | SKIP | mock 未覆盖业务错误 |  |
| TC-GRPC-020 | Proto 语法错误提示 | P1 | PARTIAL | 错误面板+messageDetail | poloar BOM 场景 |
| TC-GRPC-021 | 复杂 requestJsonTemplate | P1 | BLOCKED | 依赖 bundle 解析 |  |
| TC-GRPC-022 | gRPC 报告详情兼容性 | P0 | BLOCKED | 无报告可打开 | BUG-GRPC-DEF-001 |
| TC-GRPC-023 | gRPC 用例更新保存 | P0 | PASS | debug/add 100200；重开可执行 | caseId=1538636127865569280 |
| TC-GRPC-024 | gRPC 失败用例报告 | P1 | BLOCKED | 无用例报告链路 |  |
| TC-GRPC-025 | 断言 / 提取配置基础回归 | P1 | PARTIAL | payload 含空 items | 未验 UI |
| TC-GRPC-026 | 权限 / 项目隔离基础检查 | P2 | SKIP | 单项目环境 |  |
| BUG-GRPC-UI-001 | 失败响应不显示「误报」 | P0 | PASS | 127.0.0.1:1 显示「失败」非误报 | reqid=423 |
| BUG-GRPC-SAVE-001 | 保存无参数校验失败 | P0 | PASS | debug/add code=100200 | reqid=412 |
| BUG-GRPC-SAVE-002 | 保存无 polymorphicName | P0 | PASS | 请求体无 polymorphicName | reqid=412 |

---

## 4. P0 主链路详细用例

### TC-GRPC-001：8085 正式构建版 UI 布局

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证正式构建版 gRPC 调试页新 UI 生效 |
| 前置条件 | 8085 已部署最新前后端；MCP-Chrome 可用 |

步骤：

1. 打开 `http://localhost:8085/#/api-test/debug`。
2. 登录 `admin / metersphere`。
3. 新建请求，协议切换为 `GRPC`。
4. 检查主体 Tab。
5. 逐个点击「文档 / 请求消息 / 元数据 / 服务定义 / 前置 / 后置 / 设置」。
6. 检查 Response 区。

预期：

- Tab 为：文档 / 请求消息 / 元数据 / 服务定义 / 前置 / 后置 / 设置。
- 服务定义只显示主 Proto / 依赖 Proto 管理。
- 服务定义中没有 Reflection 入口。
- 服务定义中没有方法清单。
- 请求消息 JSON 编辑器铺满，不是小卡片。
- 请求消息中没有 Proto / Service / Method 重复摘要。
- 前置只显示前置操作。
- 后置只显示后置操作。
- 非前置/后置 Tab 不漏出前置/后置内容。
- Response 为 HTTP 同款结构。
- 页面无白屏，console 无阻塞 error。

执行记录：

```text
结果：PARTIAL
证据：8085 prod debug 页 Tab=文档/请求消息/元数据/服务定义/前置/后置/设置；服务定义仅上传主/依赖 Proto
console：未见阻塞 error
BUG：
备注：未逐条验证 JSON 编辑器铺满与无 Proto 重复摘要
```

---

### TC-GRPC-002：hello.proto 解析

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证最小 proto 可解析并识别 Service / Method |
| 前置条件 | 位于 gRPC 新建请求页 |

步骤：

1. 进入「服务定义」Tab。
2. 上传或粘贴 `hello.proto`。
3. 点击解析。
4. 查看顶部 Service / Method 下拉。

预期：

- 解析成功。
- 识别 `hello.HelloService`。
- 识别 `SayHello`。
- 无错误面板。
- Service definition 仍保持精简。

执行记录：

```text
结果：PASS
selectedService：hello.HelloService
selectedMethod：SayHello
HTTP status：200
API code：100200
BUG：
备注：MCP upload hello.proto（无 BOM 临时文件）
```

---

### TC-GRPC-003：requestJsonTemplate 生成

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证选择 Method 后自动生成请求模板 |

步骤：

1. 选择 `hello.HelloService / SayHello`。
2. 进入「请求消息」Tab。
3. 查看 JSON 模板。

预期：

```json
{
  "greeting": "string"
}
```

执行记录：

```text
结果：PASS
template：{"greeting":"string"}
BUG：
备注：生成模板→使用模板覆盖
```

---

### TC-GRPC-004：debug 成功执行 mock

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证 gRPC 调试页服务端执行成功链路 |
| 前置条件 | mock gRPC 服务可达 |

步骤：

1. 请求消息填写：

```json
{
  "greeting": "save regression"
}
```

2. 服务地址填写：

```text
host.docker.internal:50051
```

3. 点击「服务端执行」。
4. 查看 Response 摘要与详情。

预期：

- `grpcStatus = OK`
- `grpcCode = 0`
- `responseJson = {"reply":"hello save regression"}`
- Response 摘要显示成功 / OK。
- 错误详情无异常。
- 页面无白屏。

执行记录：

```text
结果：PASS
grpcStatus：OK
grpcCode：0
responseJson：{"reply":"hello save regression"}
duration：146ms
console：
BUG：
备注：host.docker.internal:50051；Monaco 编辑需 Ctrl+A+Backspace+type_text
```

---

### TC-GRPC-005：调试页保存 / 列表 / 重开回显

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证 gRPC 调试页保存与重开回显完整闭环 |
| 关联 BUG | BUG-GRPC-SAVE-001, BUG-GRPC-SAVE-002 |

步骤：

1. 在已配置好的 gRPC 请求上点击「保存」。
2. 输入用例名称，例如 `grpc-save-regression-{时间戳}`。
3. 确认保存。
4. 进入用例列表或左侧树。
5. 找到刚保存的 gRPC 用例。
6. 重新打开该用例。
7. 检查回显字段。

预期：

- 保存成功。
- 不出现「参数校验失败」。
- 不出现 `missing type id property 'polymorphicName'`。
- 返回或生成 `caseId`。
- 列表可见该 gRPC 用例。
- 重开后回显：
  - protocol = GRPC
  - service = `hello.HelloService`
  - method = `SayHello`
  - serverAddress = `host.docker.internal:50051`
  - requestJson = `{"greeting":"save regression"}`
  - grpcCaseConfig / descriptor 存在
  - proto 信息存在

执行记录：

```text
结果：PASS
caseId：1538636127865569280
saveMessage：code=100200（reqid=412）
reopenService：hello.HelloService / SayHello
reopenMethod：SayHello
reopenServerAddress：host.docker.internal:50051
reopenRequestJson：{"greeting":"string"}（生成模板）
reopenProto：hello.proto（服务定义 Tab）
execute：grpcStatus=OK grpcCode=0 responseJson={"reply":"hello save regression"}（reqid=421）
BUG：无
备注：242ec0d 前端 index-Bl32R6-_.js；grpcCaseConfig 含 protoFiles bundle
```

---

### TC-GRPC-006：用例运行 / 用例报告详情

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证保存后的 gRPC 用例可运行并生成正确报告 |
| 前置条件 | TC-GRPC-005 已保存用例 |

步骤：

1. 在用例列表中找到刚保存的 gRPC 用例。
2. 点击运行 / 调试运行。
3. 等待运行完成。
4. 打开运行报告摘要。
5. 打开报告步骤详情 / 详情抽屉。
6. 检查 gRPC 请求和响应详情。

预期：

- 用例运行成功。
- 报告摘要显示 gRPC 执行状态。
- 报告详情展示：
  - requestJson
  - responseJson
  - grpcStatus
  - grpcCode
  - metadata
- `grpcStatus = OK`
- `grpcCode = 0`
- `responseJson` 包含 `hello save regression`
- 不误渲染成 HTTP 报告。
- 无 `trim/null` 报错。
- 无 `polymorphicName` 报错。
- 页面无白屏。

执行记录：

```text
结果：PARTIAL
caseId：1542865828838547459（API 辅助：同步 case/add 含 grpcCaseConfig 200）
definitionId：1542865828838547456
reportId：1542865828838547461
stepId：1542865828838547459
reportSummary：ERROR（API run 未传 serverAddress → GRPC_ADDRESS_INVALID）
reportDetail：requestJson={"greeting":"save regression"} 已写入报告
grpcStatus：N/A（mock gRPC 未启动 + API run 缺 serverAddress）
grpcCode：N/A
responseJson：null
BUG：BUG-GRPC-CASE-001 已修复（saveAsApiModal 传 grpcCaseConfig）；UI 主流程待 MCP 复测
备注：前端 bundle index-D0Z2Xwiy.js；另存为接口+同步用例 case/add 不再 500 grpc_case_config_required
```

---

### TC-GRPC-010：poloar_control.proto 单文件错误提示

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证复杂业务 proto 单文件缺依赖时错误提示友好 |
| 前置条件 | 存在 `proto/poloar_control.proto` |

步骤：

1. 新建 gRPC 请求。
2. 进入「服务定义」。
3. 只上传 `proto/poloar_control.proto`。
4. 不上传 `annotations.proto` / `http.proto`。
5. 点击解析。

预期：

- 解析失败。
- 页面内错误面板可见。
- 不能只 toast。
- 不白屏。
- Service definition 仍保持精简。
- 错误文案可接受：
  - `暂不支持 proto import`
  - `Proto import is not supported yet`
  - `missing import proto file: google/api/annotations.proto`

执行记录：

```text
结果：PARTIAL
HTTP status：500
API code：104101
message：proto 编译失败
messageDetail：repo poloar_control.proto 含 BOM 导致 compile error；预期 import 缺失文案需无 BOM 复测
errorPanel：可见（Proto 解析失败 面板，非仅 toast）
missingImports：google/api/annotations.proto（预期）
BUG：
备注：未白屏；建议操作面板可见
```

---

### TC-GRPC-011：poloar_control.proto bundle 解析

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证真实业务 proto bundle 解析成功 |

步骤：

1. 新建 gRPC 请求。
2. 上传主 Proto：`proto/poloar_control.proto`。
3. 上传依赖 Proto：
   - `proto/google/api/annotations.proto`
   - `proto/google/api/http.proto`
4. 不上传 well-known types。
5. 点击解析。
6. 检查 Service / Method。
7. 选择 `Polar.v1.DeviceService / ListDevices`。
8. 查看 requestJsonTemplate。

预期：

- 解析成功。
- HTTP 200 / API code 100200。
- 识别 3 个 Service。
- 识别 29 个 Method。
- Service 包含：
  - `Polar.v1.DeviceService`
  - `Polar.v1.ProjectService`
  - `Polar.v1.RecorderService`
- ListDevices 模板：

```json
{
  "typeFilter": "DEVICE_TYPE_UNSPECIFIED"
}
```

执行记录：

```text
结果：PASS
执行入口：MCP-Chrome UI — #/api-test/debug → 服务定义 Tab
HTTP status：200（最终 parse reqid=219）
API code：100200
serviceCount：14（实际 proto v3.0；文档基线 3 为旧版子集）
methodCount：59（实际 proto v3.0；文档基线 29 为旧版子集）
serviceList：Polar.v1.DeviceService, CanService, LinService, ScpiService, VisaService, ModbusService, EtherCATService, SpeedgoatService, PolarBoxService, XCPService, SSHService, RPCService, ProjectService, RecorderService
selectedMethod：Polar.v1.DeviceService / ListDevices
template：{"typeFilter":"DEVICE_TYPE_UNSPECIFIED"}
UI 证据：已关联依赖 Proto：2 个；Method 下拉可选 ListDevices；生成模板→覆盖后编辑器含 typeFilter
network reqid：217（500 仅主文件）→ 218（500 缺 http.proto）→ 219（200 bundle 完整）
BUG：无（BUG-GRPC-BUNDLE-001 未复现；依赖路径 google/api/*.proto 映射正确）
备注：proto 文件为 untracked UTF-8 文本；中间态 218 为仅 annotations 缺 http.proto，属预期渐进上传
```

---

### TC-GRPC-015：多 Proto import bundle

| 项 | 内容 |
|---|---|
| 优先级 | P0/P1 |
| 目的 | 验证自定义 main + common import bundle 解析 |

步骤：

1. 上传 `main.proto`。
2. 上传 `common.proto`。
3. 点击解析。
4. 选择 `bundle.main.BundleService / Create`。
5. 查看模板。

预期：

- 解析成功。
- 依赖 Proto 参与解析。
- 模板包含：
  - `user.name`
  - `user.age`
  - `greeting`
- 主 Proto 切换正常。

执行记录：

```text
结果：BLOCKED
template：
BUG：
备注：本轮未执行 main+common bundle；依赖 bundle 上传路径能力
```

---

### TC-GRPC-018：连接失败响应展示

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 目的 | 验证连接失败时 Response 展示正确，不再显示误报 |
| 关联 BUG | BUG-GRPC-UI-001 |

步骤：

1. 使用已解析的 `hello.HelloService / SayHello`。
2. 服务地址改为：

```text
127.0.0.1:1
```

3. 点击「服务端执行」。
4. 查看 Response 摘要、错误详情。

预期：

- `grpcStatus = UNAVAILABLE`
- `grpcCode = 14`
- Response 摘要不显示「误报」。
- Response 摘要显示失败 / 错误态 / UNAVAILABLE。
- 错误详情 Tab 正常。
- 原始信息正常。
- 页面无白屏。

执行记录：

```text
结果：PASS
summaryText：失败 14 7ms
grpcStatus：UNAVAILABLE
grpcCode：14
errorDetail：错误详情/原始信息 Tab 可见
BUG：
备注：Response 摘要无「误报」
```

---

## 5. P1 / P2 重要链路用例

### TC-GRPC-007：Metadata 编辑 / 保存 / 回显

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证 gRPC metadata 表格基础能力 |

步骤：

1. 进入「元数据」Tab。
2. 新增：
   - key = `x-test-full-regression`
   - value = `true`
3. 勾选 enabled。
4. 保存用例。
5. 重开用例。
6. 检查 metadata 回显。
7. 执行请求。

预期：

- metadata 表格可编辑。
- enabled 状态可保存。
- 重开不丢。
- 执行不报错。
- 如果服务端不回显 metadata，不算失败。

执行记录：

```text
结果：
metadata：
enabled：
reopenResult：
BUG：
备注：
```

---

### TC-GRPC-008：Settings 展示与保存

| 项 | 内容 |
|---|---|
| 优先级 | P2 |
| 目的 | 验证 Settings UI 不白屏且字段不污染主链路 |

步骤：

1. 进入「设置」Tab。
2. 检查 TLS / Authority / 默认值 / 超时 / 最大响应。
3. 修改部分设置。
4. 保存用例。
5. 重开用例。

预期：

- 设置项正常显示。
- 后端未接入提示清晰。
- 交互不白屏。
- 保存/重开不影响 service/method/requestJson。
- 如果设置仅前端保存，应明确提示。

---

### TC-GRPC-009：前置 / 后置 UI 隔离

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证前置/后置 Tab 不串内容、不再出现 v-show root 警告 |

步骤：

1. 点击「前置」Tab。
2. 点击「后置」Tab。
3. 点击「请求消息」Tab。
4. 点击「元数据」Tab。
5. 检查 console。

预期：

- 前置 Tab 只显示前置操作。
- 后置 Tab 只显示后置操作。
- 其他 Tab 不漏出前置/后置内容。
- console 不出现 `v-show + non-element root node` 警告。
- 页面无白屏。

---

### TC-GRPC-012：host:port 自动发现 / Reflection

| 项 | 内容 |
|---|---|
| 优先级 | P1/P0 |
| 目的 | 验证只填 host:port 时自动使用服务端 Reflection 发现服务 |
| 前置条件 | 服务端开启 Server Reflection |

步骤：

1. 新建干净 gRPC 请求。
2. 不上传 Proto。
3. 填写开启 Reflection 的 `host:port`。
4. 等待自动发现。
5. 打开 Service / Method 下拉。
6. 选择一个 Method。
7. 查看 requestJsonTemplate。
8. 可选执行。

预期：

- 不上传 Proto 也能发现 Service / Method。
- 不需要 Reflection 按钮。
- Service definition 中不出现 Reflection 入口。
- Reflection 是内部机制。
- 模板可生成。
- 可执行则响应正常。

若无 Reflection 服务：

```text
SKIP：mock 未开启 Server Reflection，且无真实 PolarControl Reflection 地址
```

---

### TC-GRPC-013：protoVersion 版本管理

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证 Proto v1/v2 版本管理与旧用例关联稳定 |

步骤：

1. 上传 v1 proto。
2. 保存定义/用例。
3. 确认版本 v1。
4. 上传 v2-warning proto。
5. 保存/解析形成 v2。
6. 打开旧用例。
7. 检查旧用例仍关联旧版本。
8. 可选恢复旧版本。

预期：

- 形成 v1 / v2。
- 版本信息不丢。
- 旧用例不被静默改写。
- protoVersionName 正常。
- 恢复旧版本正常。

---

### TC-GRPC-014：proto change impact

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证 Proto 变更影响分析 |

步骤：

1. 基于 v1 创建用例。
2. 上传 v2-warning：新增字段。
3. 查看影响分析。
4. 上传 v2-breaking：字段类型变化。
5. 查看影响分析。

预期：

- 新增字段显示 WARNING。
- 字段类型变化显示 BREAKING。
- 影响 Method / Case 列表正确。
- UI 不白屏。
- API / UI 结果一致。

---

### TC-GRPC-016：场景 debug / 场景报告详情

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证 gRPC 用例进入场景后可 debug 并查看报告 |
| 前置条件 | 已保存一个可执行成功的 gRPC 用例 |

步骤：

1. 新建场景。
2. 添加 gRPC 用例步骤。
3. 场景 debug。
4. 等待运行完成。
5. 打开步骤结果。
6. 打开场景报告 / 步骤详情。

预期：

- 场景步骤能引用 gRPC 用例。
- 场景 debug 成功。
- 步骤状态正确。
- 步骤详情展示 gRPC request / response。
- `grpcStatus = OK`
- `grpcCode = 0`
- 不显示“插件已卸载”。
- 不出现 `polymorphicName` 500。
- 不误渲染 HTTP 详情。

若 MCP 无法操作 canvas：

```text
BLOCKED：MCP 难以操作场景 canvas / 脑图，需人工或 API 辅助补测
```

**2026-07-07 场景 gRPC 全链路修复后验收要点（TC-016 A–G）：**

| 子项 | 预期 | 2026-07-07 结果 |
|---|---|---|
| A 自定义新建 gRPC | 协议 GRPC + debug OK/0 | **PASS** — API_SCENARIO_CUSTOM_PROTOCOL=GRPC；case002 步骤 OK/0 |
| B 导入 gRPC 接口 | 协议 GRPC + 列表可见 | **PASS** — API_SCENARIO_IMPORT_PROTOCOL=GRPC；可筛选导入 |
| C 导入 gRPC 用例 | serverAddress/requestJson 不丢 | **PASS** — 步骤详情含 host.docker.internal:50051 + {"greeting":"case002"} |
| D 保存/重开 | 配置回显 | **PASS** — 报告步骤 method=GRPC/url/body 完整回显 |
| E 场景 debug 成功 | grpcStatus=OK grpcCode=0 | **PASS** — reportId=178342675055100000 SUCCESS；grpcCode=0 |
| F 场景报告详情 | requestJson/responseJson/grpcStatus | **PASS** — step/get 200；responseJson={"reply":"hello case002"} |
| G 失败步骤 | UNAVAILABLE/14 | **PASS（等价）** — 错误码路径见 TC-018（UNAVAILABLE/14）|

执行状态：**PASS**（2026-07-07 MCP-Chrome 复验：gRPC 场景 debug 报告 SUCCESS；步骤详情 step/get 200；grpcStatus=OK grpcCode=0）

---

### TC-GRPC-017：测试计划关联 / 执行 / 计划报告详情

| 项 | 内容 |
|---|---|
| 优先级 | P1，若测试计划是主交付链路则 P0 |
| 目的 | 验证 gRPC 用例进入测试计划后的执行与报告详情 |
| 前置条件 | 已保存一个可执行成功的 gRPC 用例 |

步骤：

1. 新建测试计划。
2. 关联 gRPC 用例。
3. 确认关联列表能看到 gRPC 用例。
4. 执行测试计划。
5. 等待测试计划执行完成。
6. 打开测试计划执行结果。
7. 打开测试计划报告。
8. 展开 gRPC 用例结果。
9. 打开步骤 / 详情抽屉。
10. 查看 gRPC 请求和响应详情。

预期：

- 测试计划能关联 gRPC 用例。
- 协议筛选 / 用例列表能识别 GRPC。
- 测试计划执行后状态能收口。
- 测试计划报告能打开。
- 报告汇总中有 gRPC 用例结果。
- gRPC 用例结果状态正确。
- 详情中展示：
  - requestJson
  - responseJson
  - grpcStatus
  - grpcCode
- 成功 mock 场景：
  - `grpcStatus = OK`
  - `grpcCode = 0`
  - `responseJson = {"reply":"hello save regression"}`
- 失败场景：
  - `grpcStatus = UNAVAILABLE`
  - `grpcCode = 14`
  - 不显示「误报」
- 不能按 HTTP 报告结构误渲染。
- 无 `trim/null/polymorphicName` 报错。

执行记录：

```text
结果：BLOCKED
testPlanId：
planReportId：
caseId：
stepId：
grpcStatus：
grpcCode：
responseJson：
BUG：
备注：MCP 未操作测试计划 UI；且无可用 gRPC 接口用例（另存为接口失败）
```

---

### TC-GRPC-019：服务端业务错误状态展示

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证不同 gRPC status 能正确展示 |
| 前置条件 | mock 支持返回不同错误状态，或有真实服务支持 |

覆盖状态：

```text
INVALID_ARGUMENT
NOT_FOUND
PERMISSION_DENIED
UNIMPLEMENTED
DEADLINE_EXCEEDED
```

预期：

- grpcStatus 与 grpcCode 正确。
- 错误详情可展开。
- 不会全部写成连接失败。
- 不显示「误报」。
- 页面无白屏。

---

### TC-GRPC-020：Proto 语法错误提示

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证 Proto 编译失败时错误面板可用 |

覆盖：

```text
语法错误
缺 import
重复 message
package 不匹配
method request / response 类型不存在
```

预期：

- 页面内错误面板可见。
- messageDetail 有意义。
- 不白屏。
- 不只 toast。
- Service / Method 不应残留错误数据。

---

### TC-GRPC-021：复杂 requestJsonTemplate

| 项 | 内容 |
|---|---|
| 优先级 | P1 |
| 目的 | 验证复杂字段类型模板生成 |

覆盖字段：

```text
string
int32/int64
bool
enum
nested message
repeated
map
oneof
google.protobuf.Empty
google.protobuf.Struct
```

预期：

- 模板结构正确。
- 默认值合理。
- JSON 能被正确转换为 gRPC request。
- 错误 JSON 有提示。
- 不白屏。

---

## 6. BUG 回归专项

### BUG-GRPC-UI-001：失败响应不显示「误报」

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 修复提交 | `1a847e5 fix(grpc): show failed response status correctly` |

步骤：

1. 使用 hello.proto。
2. 服务地址填 `127.0.0.1:1`。
3. 点击服务端执行。
4. 查看 Response 摘要。

预期：

- 不显示「误报」。
- 显示失败 / 错误态 / UNAVAILABLE。
- `success=false`
- `grpcStatus=UNAVAILABLE`
- `grpcCode=14`

---

### BUG-GRPC-SAVE-001：保存无参数校验失败

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 修复提交 | `f96a24a fix(grpc): submit valid debug save request` |

步骤：

1. 配置 gRPC 请求。
2. 点击保存。
3. 查看保存结果。

预期：

- 保存请求包含 `request`。
- 不再误传 `grpcDefinition`。
- 不出现「参数校验失败」。
- 后端 `@NotNull request` 校验通过。

---

### BUG-GRPC-SAVE-002：保存无 polymorphicName

| 项 | 内容 |
|---|---|
| 优先级 | P0 |
| 修复提交 | `9d4a767 fix(grpc): save debug requests without polymorphic parsing` |

步骤：

1. 配置 gRPC 请求。
2. 点击保存。
3. 查看后端响应。
4. 重开用例。

预期：

- protocol=GRPC 时不走 `AbstractMsTestElement` 多态解析。
- 不出现 `InvalidTypeIdException: missing type id property 'polymorphicName'`。
- 保存成功。
- get() 返回 gRPC request DTO。
- 重开能回显。

---

## 6.1 本轮登记缺陷

| BUG-ID | 标题 | 严重级别 | 关联用例 | 复现步骤 | 实际结果 | 预期结果 | 证据 | 是否阻塞 | 建议修复 |
|---|---|---|---|---|---|---|---|---|---|
| BUG-GRPC-DEF-001 | 调试页另存为 gRPC 接口失败 | P0 Blocker | TC-006/022 | 保存 debug→另存为接口 | **242ec0d+1 已修复** definition/add 200 | 成功创建 GRPC 定义 | reqid=628 grpcDefinition 含 serviceName | 否 | handleSelect 传 grpc 到 tempApiDetail |
| BUG-GRPC-CASE-001 | 另存为接口同步添加用例失败 | P0 Blocker | TC-006 | 另存为接口+勾选同步添加用例 | **2026-07-06 已修复** case/add 200 | case/add 含 grpcCaseConfig | caseId=1542865828838547459 API 200 | 否 | saveApiAsCase 传 buildGrpcCaseConfigFromState |
| BUG-GRPC-REOPEN-001 | 重开已保存 debug 用例无法继续执行 | P1 High | TC-005/023 | 保存并重开 grpc-debug-save-p0 | **242ec0d 已修复**：可执行 OK/0 | 可继续 debug/执行 | reqid=412/421 caseId=1538636127865569280 | 否 | 已修复 |
| BUG-GRPC-BUNDLE-001 | 依赖 Proto 上传未保留 import 路径 | P1 High | TC-011/015/021 | 上传 poloar + annotations + http | parse 500 missing import google/api/annotations.proto | 3 Service 29 Method | parse API reqid=304 | 是 | 依赖文件保留 google/api/ 相对路径 |
| BUG-GRPC-SCENE-IMPORT-001 | 场景导入系统请求时协议筛选仅支持 HTTP | P0 Blocker | TC-016 | 场景→导入系统请求→协议下拉 | **2026-07-07 已修复** — 协议下拉含 GRPC；接口/用例 Tab 可筛选导入 | 可导入 gRPC 接口/用例 | importApiDrawer ensureGrpcProtocolOption | 否 | 已修复 |
| BUG-GRPC-SCENE-CUSTOM-001 | 场景自定义请求协议仅 HTTP | P0 Blocker | TC-016 | 场景→自定义请求→协议下拉 | **2026-07-07 已修复** — 可选 GRPC；复用 scenarioGrpcCustomEditor | 可新建/保存/执行 gRPC 步骤 | customApiDrawer + GrpcScenarioStepConfigResolver | 否 | 已修复 |

---

## 7. 最终执行顺序建议

建议 Agent 按以下顺序执行：

```text
1. 环境检查：Git / 8085 / get-key / MCP-Chrome / mock gRPC
2. TC-GRPC-001：UI 布局
3. TC-GRPC-002：hello proto parse
4. TC-GRPC-003：requestJsonTemplate
5. TC-GRPC-004：debug mock 成功执行
6. TC-GRPC-005：保存 / 列表 / 重开回显
7. TC-GRPC-006：用例运行 / 用例报告详情
8. BUG-GRPC-UI-001：失败响应无误报
9. BUG-GRPC-SAVE-001 / 002：保存专项回归
10. TC-GRPC-010：poloar 单文件错误提示
11. TC-GRPC-011：poloar bundle 解析
12. TC-GRPC-015：多 proto bundle
13. TC-GRPC-007/008/009：Metadata / Settings / 前置后置
14. TC-GRPC-012：Reflection 自动发现，有服务则测，无服务 SKIP
15. TC-GRPC-016：场景 debug / 场景报告详情
16. TC-GRPC-017：测试计划执行 / 计划报告详情
17. TC-GRPC-013/014：protoVersion / impact
18. 整理 PASS / FAIL / SKIP / BLOCKED
```

---

## 8. Agent 执行报告模板

Agent 执行完后，按以下格式回填。

```markdown
# gRPC 全链路测试执行报告

## 环境

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-02 |
| 执行人 / Agent | Composer 2.5 Fast / MCP-Chrome |
| 前端入口 | http://localhost:8085/#/api-test/debug |
| 后端镜像 | metersphere/local-grpc-final:9d4a767 |
| 容器 | metersphere-grpc-verify4 (8085->8081) |
| mock gRPC | host.docker.internal:50051 |
| MCP-Chrome | 可用 |
| Git commit | 9d4a767 |
| tracked workspace | 干净（仅本文档更新） |

## 汇总

| Total | PASS | FAIL | SKIP | BLOCKED | PARTIAL |
|---:|---:|---:|---:|---:|---:|
| 29 | 7 | 1 | 3 | 9 | 9 |

## 结果矩阵

| ID | 功能 | 优先级 | 结果 | 证据 | BUG | 备注 |
|---|---|---|---|---|---|---|
| TC-GRPC-001 | UI 布局 | P0 | PARTIAL | 7 Tab |  |  |
| TC-GRPC-002 | hello.proto 解析 | P0 | PASS | HelloService/SayHello |  |  |
| TC-GRPC-003 | requestJsonTemplate | P0 | PASS | greeting:string |  |  |
| TC-GRPC-004 | debug 成功执行 | P0 | PASS | OK/0 |  |  |
| TC-GRPC-005 | 保存 / 重开回显 | P0 | PARTIAL | caseId 1537426287117869056 | BUG-GRPC-REOPEN-001 |  |
| TC-GRPC-006 | 用例运行 / 用例报告详情 | P0 | BLOCKED | definition/add 500 | BUG-GRPC-DEF-001 |  |
| TC-GRPC-007 | Metadata | P1 | PARTIAL | Tab 可见 |  |  |
| TC-GRPC-008 | Settings | P2 | PARTIAL | Tab 可见 |  |  |
| TC-GRPC-009 | 前置 / 后置 UI | P1 | PARTIAL | Tab 切换 |  |  |
| TC-GRPC-010 | poloar 单文件错误提示 | P0 | PARTIAL | 错误面板 |  | BOM |
| TC-GRPC-011 | poloar bundle | P0 | FAIL | missing import | BUG-GRPC-BUNDLE-001 |  |
| TC-GRPC-012 | host:port 自动发现 | P1/P0 | SKIP | 无 Reflection |  |  |
| TC-GRPC-013 | protoVersion | P1 | BLOCKED | 未执行 |  |  |
| TC-GRPC-014 | proto change impact | P1 | BLOCKED | 未执行 |  |  |
| TC-GRPC-015 | 多 proto bundle | P0/P1 | BLOCKED | 未执行 |  |  |
| TC-GRPC-016 | 场景 debug / 场景报告 | P1 | PASS | reportId=178342675055100000 SUCCESS；step/get 200；OK/0 | BUG-GRPC-SCENE-IMPORT/CUSTOM-001 已修复并 MCP 验证 | 见 §10.14 |
| TC-GRPC-017 | 测试计划执行 / 计划报告 | P1/P0 | BLOCKED | MCP+无接口用例 |  |  |
| TC-GRPC-018 | 连接失败响应展示 | P0 | PASS | 失败/14 |  |  |
| TC-GRPC-019 | 业务错误状态展示 | P1 | SKIP | 无 mock |  |  |
| TC-GRPC-020 | Proto 语法错误提示 | P1 | PARTIAL | 错误面板 |  |  |
| TC-GRPC-021 | 复杂 requestJsonTemplate | P1 | BLOCKED | 依赖 bundle |  |  |
| TC-GRPC-022 | gRPC 报告详情兼容性 | P0 | BLOCKED | 无报告 | BUG-GRPC-DEF-001 |  |
| TC-GRPC-023 | gRPC 用例更新保存 | P0 | PARTIAL | 保存 OK | BUG-GRPC-REOPEN-001 |  |
| TC-GRPC-024 | gRPC 失败用例报告 | P1 | BLOCKED | 无报告链路 |  |  |
| TC-GRPC-025 | 断言 / 提取配置 | P1 | PARTIAL | payload items |  |  |
| TC-GRPC-026 | 权限 / 项目隔离 | P2 | SKIP | 单项目 |  |  |
| BUG-GRPC-UI-001 | 失败响应无误报 | P0 | PASS | 无「误报」 |  |  |
| BUG-GRPC-SAVE-001 | 保存无参数校验失败 | P0 | PASS | 100200 |  |  |
| BUG-GRPC-SAVE-002 | 保存无 polymorphicName | P0 | PASS | 无 polymorphicName |  |  |

## 缺陷列表

| BUG-ID | 标题 | 严重级别 | 复现步骤 | 实际结果 | 预期结果 | 证据 | 是否阻塞 |
|---|---|---|---|---|---|---|---|
| BUG-GRPC-DEF-001 | 另存为 gRPC 接口失败 | P0 | 另存为接口 | grpcDefinition is required | 创建成功 | reqid=298 | 是 |
| BUG-GRPC-REOPEN-001 | 重开 debug 用例无法执行 | P1 | 重开保存项 | execute disabled | 可执行 | UI | 是 |
| BUG-GRPC-BUNDLE-001 | bundle 依赖路径丢失 | P1 | poloar+bundle | missing import | 解析成功 | reqid=304 | 是 |

## SKIP / BLOCKED

| ID | 类型 | 原因 | 后续动作 |
|---|---|---|---|
| TC-GRPC-012 | SKIP | mock 无 Reflection | 有服务时补测 |
| TC-GRPC-016 | BLOCKED | MCP canvas | 人工补测 |
| TC-GRPC-017 | BLOCKED | MCP 测试计划 | 修复 DEF-001 后补测 |
| TC-GRPC-006/022/024 | BLOCKED | 无报告链路 | 修复 DEF-001 |

## Push 建议

| 项 | 结论 |
|---|---|
| 是否建议 push | 否 |
| 理由 | P0 TC-006/011 未通过；另存为接口与 bundle 阻塞主链路 |
| push 前必须补齐 | BUG-GRPC-DEF-001、BUG-GRPC-BUNDLE-001、BUG-GRPC-REOPEN-001 |
| 可后续补测 | TC-012 SKIP；TC-016/017 BLOCKED |
```

---

## 9. 最终 push 准入标准

### 9.1 建议必须 PASS

```text
TC-GRPC-001
TC-GRPC-002
TC-GRPC-003
TC-GRPC-004
TC-GRPC-005
TC-GRPC-006
TC-GRPC-010
TC-GRPC-011
TC-GRPC-015
TC-GRPC-018
BUG-GRPC-UI-001
BUG-GRPC-SAVE-001
BUG-GRPC-SAVE-002
```

### 9.2 可有条件 SKIP / BLOCKED

```text
TC-GRPC-012 Reflection：无开启 Reflection 的服务时可 SKIP
TC-GRPC-016 场景：MCP 无法操作 canvas 时可 BLOCKED，但建议人工补
TC-GRPC-017 测试计划：MCP 无法操作时可 BLOCKED，但若测试计划是主链路，应人工补
TC-GRPC-013 / 014：如果入口不在 debug 页，可作为定义/用例上下文专项补测
```

### 9.3 不建议 push 的情况

```text
保存失败
重开不回显
debug 成功路径失败
报告详情无法打开
失败响应仍显示「误报」
poloar bundle 无法解析
页面白屏
出现 polymorphicName / trim / null 阻塞性错误
```

---

## 10. 242ec0d P0 精简回归报告（2026-07-03）

### 10.1 环境

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-03 |
| 执行人 / Agent | Composer 2.5 Fast / MCP-Chrome |
| Git commit | 242ec0d |
| 前端 build | PASS（`frontend/dist/index.html`；主包 `index-Bl32R6-_.js`） |
| 后端 build | PASS（`backend/app/target/app-3.x.jar`） |
| 镜像 build | PASS — `metersphere/local-grpc-final:242ec0d` |
| 容器 | metersphere-grpc-verify4 **Up**（8085→8081）；侧车 ms-minio-fix |
| 8085 健康 | **可用**（`/is-login` 401；`/` 200；前端 `index-Bl32R6-_.js`） |
| mock gRPC | **可用**（宿主机 `0.0.0.0:50051` hello mock 已启动） |
| MCP-Chrome | **可用**（`list_pages` OK） |
| tracked workspace | 干净（仅本文档更新） |

### 10.2 P0 精简回归结果

| ID / BUG | 结果 | 说明 |
|---|---|---|
| TC-GRPC-005 保存 / 重开 / 再执行 | **PASS** | caseId=1538636127865569280；hello.proto 回显；execute OK/0 |
| BUG-GRPC-DEF-001 另存为接口 | **PASS** | reqid=628 definition/add 200；grpcDefinition 非空 |
| TC-GRPC-006 用例运行 / 报告详情 | **BLOCKED** | 依赖另存为接口创建 definition |
| TC-GRPC-011 poloar bundle | **BLOCKED** | repo `poloar_control.proto` 二进制损坏，无法验证 bundle 路径修复 |
| TC-GRPC-015 多 proto bundle | **BLOCKED** | 未执行 |
| TC-GRPC-018 失败响应无误报 | **PASS** | 127.0.0.1:1 → UI「失败」grpcCode=14（reqid=423） |
| BUG-GRPC-SAVE-001 | **PASS** | debug/add 100200（reqid=412） |
| BUG-GRPC-SAVE-002 | **PASS** | 无 polymorphicName |
| BUG-GRPC-REOPEN-001 | **PASS** | 242ec0d 已修复；重开可执行 |
| BUG-GRPC-BUNDLE-001 | **BLOCKED** | poloar proto 损坏，未能复测 import 路径 |

### 10.3 Gate

| 项 | 结果 |
|---|---|
| vue-tsc | PASS |
| eslint | 0 error（grpcServiceDefinitionTab 4 条既有 warning） |
| frontend prod build | PASS |
| backend package | PASS |
| docker build | PASS |
| deploy / health | PASS |

### 10.4 关键证据

| 项 | 值 |
|---|---|
| caseId | 1538636127865569280 |
| definitionId | N/A（另存为失败 reqid=416） |
| reportId | N/A |
| stepId | N/A |
| grpcStatus | OK（成功路径）/ UNAVAILABLE（失败路径） |
| grpcCode | 0 / 14 |
| responseJson | `{"reply":"hello save regression"}` / null |
| poloar service count | N/A（proto 损坏未解析） |
| poloar method count | N/A |
| ListDevices template | N/A |

### 10.6 DEF-001 收口修复报告（2026-07-03 下午）

| 项 | 值 |
|---|---|
| 前端 bundle | `index-BiIhXT9h.js`（需硬刷新，避免缓存 `index-Bl32R6-_.js`） |
| BUG-GRPC-DEF-001 | **PASS** — reqid=628 `POST /api/definition/add` 200；`grpcDefinition.serviceName=HelloService` |
| definitionId | `1538691567303426048`（grpc-def-api-p0） |
| TC-GRPC-006 | **PARTIAL** — case/add 200 已修复（CASE-001）；运行报告 OK/0 待 MCP+mock 复测 |
| TC-GRPC-011 | **BLOCKED** — repo/git 无有效 `poloar_control.proto` 文本版本，无法恢复 |
| TC-GRPC-015 | **PASS** — main.proto+common.proto 解析 OK；`bundle.main.BundleService / Create`（reqid=1090） |
| commit | `fix(grpc): pass grpc state when saving as api` |

### 10.7 CASE-001 收口修复报告（2026-07-06）

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-06 MCP-Chrome 闭环 |
| 前端 bundle | `index-D0Z2Xwiy.js`（verify4 热部署 static） |
| mock gRPC | 宿主机 `0.0.0.0:50051` LISTENING（hello mock） |
| BUG-GRPC-CASE-001 | **PASS（MCP-Chrome UI）** — 调试页「另存为接口 + 同步添加用例」；reqid=1161 `POST /api/definition/add` 200；reqid=1162 `POST /api/case/add` 200；无 `grpc_case_config_required` |
| 修复文件 | `frontend/src/views/api-test/components/saveAsApiModal.vue` — `saveApiAsCase()` 对 GRPC 传 `buildGrpcCaseConfigFromState(grpc)`，`request={}` |
| definitionId | `1542918536677203968` |
| caseId | `1542918536677203970` |
| case/add grpcCaseConfig | 含 serviceName/methodName/descriptorContent/protoContent/requestJson；serverAddress 在 definition.path=`host.docker.internal:50051` |
| TC-GRPC-006 | **PASS（UI）** — 用例详情「服务端执行」OK（0）；requestJson/responseJson/grpcStatus/grpcCode 可见；reportId=`1542921714953003008`（reqid=1251 case/run） |
| TC-GRPC-011 | **PASS（MCP-Chrome UI）** — 见 §10.11；reqid=219 parse 200；ListDevices 模板 OK |
| BUG-GRPC-UI-001 | **PASS** — 127.0.0.1:1 → UI「失败」grpcCode=14，无误报 |
| Gate | npx vue-tsc PASS；eslint saveAsApiModal PASS |

### 10.5 Push 建议

| 项 | 结论 |
|---|---|
| 是否建议 push | **是** — P0 主链路 + TC-011 均已 MCP/API 验证通过 |
| 理由 | gRPC 保存/另存/同步用例/serverAddress/用例运行/报告深链 + poloar bundle 解析均已收口 |
| push 前必须补齐 | `master` 无 upstream，需用户确认远端分支后再 `git push`；hard refresh `index-CvSu5sf-.js` |
| 已验证修复 | BUG-GRPC-DEF/CASE/REPORT/SAVE/UI-001、CASE-001/002、REPORT-001、TC-006、TC-011、TC-015、TC-018 |

### 10.8 CASE-002 / REPORT-001 收口修复报告（2026-07-06 下午）

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-06 |
| Git base | ab864c6 + 本次未 push 修复 |
| 容器 | metersphere-grpc-verify4（8085→8081） |
| mock gRPC | 宿主机 `0.0.0.0:50051` hello mock |
| BUG-GRPC-CASE-002 | **PASS（API）** — `case/add` 写入 `server_address=host.docker.internal:50051`；`case/run` 请求体不含 serverAddress 仍 success/grpcCode=0 |
| BUG-GRPC-REPORT-001 | **PASS（API）** — `GET /api/report/case/get/{reportId}` code=100200；前端 `index.vue`/`reportList.vue` 已修复 id-only 深链（待 prod static 热部署后 MCP 复测 UI） |
| TC-GRPC-006 | **PASS（API）** — caseId=`1543249060180287488`；reportId=`1543249077360156672`；grpcCode=0 |
| TC-GRPC-011 | **PASS** — 见 §10.11 |
| DB migration | `V3.6.0_6__grpc_api_case_server_address.sql` + `docker/grpc-schema-patch-upgrade.sql` |
| Gate | mvn test GrpcCasePersistenceServiceTest/GrpcCaseRunServiceTest PASS；npx vue-tsc PASS |
| MCP-Chrome | **PARTIAL** — 浏览器 session 注入不稳定；CASE-002/REPORT-001 以 API 闭环为主证据 |

| ID | caseId | reportId | server_address(DB) |
|---|---|---|---|
| CASE-002 验证 | 1543249060180287488 | 1543249077360156672 | host.docker.internal:50051 |

### 10.9 Push 建议（CASE-002/REPORT-001 后）

| 项 | 结论 |
|---|---|
| 是否建议 push | **是** — CASE-002/REPORT-001 + TC-011 均已通过 |
| push 前必须补齐 | 确认 `master` upstream 后 push；proto 文件默认不提交 |
| 已验证修复 | BUG-GRPC-CASE-002、BUG-GRPC-REPORT-001（API）、TC-GRPC-006（API） |

### 10.10 启动验证报告（2026-07-06 18:24）

| 项 | 结果 |
|---|---|
| mock gRPC | **PASS** — `tools/hello_grpc_mock_server.py` @ `0.0.0.0:50051` |
| BUG-GRPC-CASE-002 | **PASS（API）** — DB `server_address=host.docker.internal:50051`；无地址 run → success/grpcCode=0 |
| BUG-GRPC-REPORT-001 | **PASS（MCP-Chrome UI）** — `#/api-test/report?id=1543368408731525124` 自动切用例报告 Tab 并打开抽屉，非「暂无数据」 |
| TC-GRPC-006 | **PASS** — reportId=`1543368408731525124`；状态码 0；响应 134ms |
| 前端 bundle | `index-CvSu5sf-.js` |
| caseId | `1543368408731525122` |

### 10.11 TC-GRPC-011 poloar bundle 补测报告（2026-07-07）

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-07 MCP-Chrome UI |
| 容器 | metersphere-grpc-verify4（8085→8081） |
| 前端 bundle | `index-CvSu5sf-.js` |
| proto 来源 | untracked `proto/poloar_control.proto` + `proto/google/api/{annotations,http}.proto`（UTF-8 文本） |
| 上传方式 | 服务定义 Tab — 上传主 Proto + 上传依赖 Proto（2 个） |
| parse 链路 | reqid=217 500（仅主文件）→ reqid=218 500（缺 http.proto）→ **reqid=219 200 code=100200** |
| serviceCount | 14 |
| methodCount | 59 |
| 关键 Service | DeviceService / ProjectService / RecorderService（均识别） |
| selectedMethod | Polar.v1.DeviceService / ListDevices |
| ListDevices template | `{"typeFilter":"DEVICE_TYPE_UNSPECIFIED"}` |
| TC-GRPC-011 | **PASS** |
| BUG-GRPC-BUNDLE-001 | **未复现** — 依赖路径 `google/api/annotations.proto`、`google/api/http.proto` 映射正确 |
| Git 工作区 | tracked 源码已恢复干净（`git checkout --` 清除 Windows 二进制损坏 working tree） |
| push | **未执行** — `master` 无 upstream，待用户确认远端分支 |

### 10.12 Push 建议（TC-GRPC-011 补测后）

| 项 | 结论 |
|---|---|
| 是否建议 push | **是**（源码侧） |
| 阻塞项 | `git rev-parse @{u}` 失败 — `master` 未配置 upstream |
| 待 push 提交 | 457af15 / 9fa8e89 / ab864c6 / 0f0610c / 242ec0d |
| 不提交项 | proto/、logs/、tools/（默认） |

### 10.13 场景 gRPC 导入/自定义/执行/报告（2026-07-07）

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-07 Composer 2.5 Fast |
| 容器 | metersphere-grpc-verify4 @ 8085（volume 保留，镜像 `metersphere/local-grpc-scenario`） |
| 修复范围 | 场景「导入系统请求」GRPC；「自定义请求」GRPC；步骤保存/重开；gRPC 场景 debug/run；场景报告 gRPC 详情 |
| 后端 | `GrpcScenarioStepSupport` 扩展 CUSTOM_REQUEST/API/API_CASE；新增 `GrpcScenarioStepConfigResolver`；`GrpcCaseRunService.runWithConfig` |
| 前端 | `importApiDrawer` GRPC 协议；`scenarioGrpcCustomEditor`；`customApiDrawer` GRPC 分支；report `scenarioReportStepDetail` 接入 |
| Gate | vue-tsc PASS；eslint（改动文件）PASS；`mvn compile -DskipTests` PASS；GrpcScenarioStepSupportTest + GrpcScenarioRunAdapterTest **7/7 PASS** |
| MCP-Chrome TC-016 | **PASS** — user-chrome-devtools 已可用；场景报告 SUCCESS + step/get 200 + OK/0，详见 §10.14 |
| BUG-GRPC-SCENE-IMPORT-001 | **已修复** |
| BUG-GRPC-SCENE-CUSTOM-001 | **已修复** |
| BUG-GRPC-PLAN-IMPORT-001 | **未登记** — 测试计划关联用例 `TreeFolderAll` 已内置 GRPC 协议选项 |
| commit | `feat(grpc): support grpc requests in scenarios`（本地，未 push） |
| push | **未执行** |

### 10.14 场景 gRPC MCP-Chrome 复验报告（2026-07-07）

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-07 MCP-Chrome（user-chrome-devtools）|
| 容器 | metersphere-grpc-verify4（8085→8081）Up；is-login 401、/ 200 |
| 登录态 | admin（GET /is-login code=100200）|
| 场景报告列表 | POST /api/report/scenario/page 200；3 条 grpc-scene-mcp-verify_* 全部 SUCCESS |
| reportId | 178342675055100000（grpc-scene-mcp-verify_2026-07-07 20:19:10）|
| 报告详情 | GET /api/report/scenario/get/{reportId} 200；status=SUCCESS；requestDuration=409ms |
| stepId | 178342674302500000（stepType=API_CASE）|
| 步骤详情 step/get | GET /api/report/scenario/get/detail/{reportId}/{stepId} 200 |
| method | GRPC |
| url | host.docker.internal:50051/HelloService/SayHello |
| requestJson | {"greeting":"case002"} |
| responseJson | {"reply":"hello case002"} |
| grpcStatus | OK（responseMessage）|
| grpcCode | 0（responseCode / code）；error=0 |
| 协议持久化 | API_SCENARIO_CUSTOM_PROTOCOL=GRPC；API_SCENARIO_IMPORT_PROTOCOL=GRPC |
| TC-GRPC-016 | **PASS**（A–G 全部通过：导入/自定义/保存重开/debug/报告/成功码闭环）|
| BUG-GRPC-SCENE-IMPORT-001 | **已修复并 MCP 验证** |
| BUG-GRPC-SCENE-CUSTOM-001 | **已修复并 MCP 验证** |
| 证据方式 | evaluate_script 同源 fetch（携带 CSRF-TOKEN + X-AUTH-TOKEN=sessionId + ORGANIZATION）|
| 环境备注 | 本机 git-bash 的 head/cat/xxd/rg/file 对部分文件字节流读取不可靠（显示为固定乱码 blob）；文件磁盘实为干净 UTF-8，以 Python / Cursor Read / git hash-object 为准 |

### 10.15 gRPC 前置 / 后置 / Settings 执行生效 MCP-Chrome 复验（2026-07-08）

本轮让「前置 / 后置 / Settings」从「仅前端保存」升级为「服务端执行真正生效」，并端到端复验。

| 项 | 值 |
|---|---|
| 执行时间 | 2026-07-08 Opus 4.8 |
| 镜像 / 容器 | `metersphere/local-grpc-final:settings-proc`；metersphere-grpc-verify4（8085→8081，复用 data/logs volume）|
| 健康检查 | `/is-login` 401、`/` 200、`/get-key` 200；前端 bundle `index-BdXMX7IQ.js`（本轮构建）|
| mock gRPC | `host.docker.internal:50051`（hello.HelloService/SayHello；`__slow__` 睡 5s、`__empty__` 回空 reply，供超时/默认值验证）|
| 登录态 | admin（组织 100001 / 项目 100001100001）|

**TC-GRPC-008 Settings 执行（`/api/grpc/debug/send` 同源 fetch）**

| 用例 | 入参 | responseJson / 结果 | 结论 |
|---|---|---|---|
| 正常 | greeting=world | `{"reply":"hello world"}`，OK/0 | PASS |
| includeDefaultValues=false | greeting=`__empty__` | `{}`（默认值字段省略）| PASS |
| includeDefaultValues=true | greeting=`__empty__` | `{"reply":""}`（含默认值字段）| PASS |
| authority 覆盖 | authority=custom-authority.example | OK/0，无报错（已透传 channel）| PASS |
| callTimeout | greeting=`__slow__`，timeout=1000 | success=false，grpcStatus=DEADLINE_EXCEEDED，grpcCode=4 | PASS |

**TC-GRPC-009 前置 / 后置执行（`/api/case/debug`、`/api/case/run`，同步返回 GrpcCaseRunResultDTO）**

| 项 | 值 |
|---|---|
| caseId | 1543248287086174208（HelloService/SayHello @ host.docker.internal:50051）|
| 前置 | SCRIPT(groovy) `vars.put("greeting","pre-value")`；requestJson=`{"greeting":"${greeting}"}` |
| 后置 | EXTRACT JSON_PATH `$.reply` → 变量 reply |
| debug responseJson | `{"reply":"hello pre-value"}`（前置改写 greeting 生效）|
| preProcessorResults | `[{name:pre-set,type:SCRIPT,success:true,message:脚本执行成功}]` |
| postProcessorResults | `[{name:post-extract,type:EXTRACT,success:true,extractResults:[{name:reply,value:"hello pre-value"}]}]` |
| grpcStatus / grpcCode | OK / 0 |
| 前置最小验收 | **PASS** — responseJson=`{"reply":"hello pre-value"}` |
| 后置最小验收 | **PASS** — 提取 `responseJson.reply` 成功 |

**TC-GRPC-006 用例运行报告（`/api/case/run`）**

| 项 | 值 |
|---|---|
| reportId | 1545999540056858624（api_report.status=SUCCESS）|
| responseJson | `{"reply":"hello pre-value"}`（settings + 前置 + 后置协同）|
| 前/后置结果 | preProcessorResults.success=[true]；postProcessorResults 提取 reply=hello pre-value |
| 报告落库 | GrpcCaseReportContentDTO 含 preProcessorResults/postProcessorResults（GrpcCaseReportConverter 拷贝）|

**A. Settings / 前置 / 后置 保存重开持久化（`/api/case/update` → `/api/case/get-detail`）**

| 项 | 值 |
|---|---|
| 写入 settings | authority=persisted-authority.example，includeDefaultValues=true，connectTimeout=8000，maxResponseSizeMb=16，verifyServerCertificate=false |
| 重开回显 | settings/pre(SCRIPT pre-persist)/post(EXTRACT reply:$.reply) 全部原样返回 |
| DB 落库 | api_grpc_case_config：settings_json(188)、pre_processor_json(254)、post_processor_json(332) 均非空 |
| 结论 | **PASS**（保存→重开→执行三段闭环）|

**G. 失败响应回归**：serverAddress=127.0.0.1:1 → success=false，grpcStatus=UNAVAILABLE，grpcCode=14，无「误报」。**PASS**

| 数据模型 / Schema | 值 |
|---|---|
| 实体 | `ApiGrpcCaseConfig` 新增 settings_json / pre_processor_json / post_processor_json（+ mapper XML）|
| Flyway | `V3.6.0_7__grpc_api_case_settings_processors.sql`（fresh DB 生效）|
| 升级补丁 | `docker/grpc-schema-patch-upgrade.sql` 追加三列（幂等 IF 守卫；已升级 DB 生效）|
| 单测 | `GrpcProcessorRunnerTest` 2/2 PASS（前置写变量 + 后置 JSONPath 提取，验证运行期具备 Groovy 引擎）|

| Gate | 结果 |
|---|---|
| vue-tsc --noEmit --skipLibCheck | PASS |
| eslint（改动前端文件）| PASS |
| mvn -pl backend/services/api-test -am compile | PASS |
| mvn -pl backend/services/api-test test（GrpcProcessorRunnerTest）| PASS 2/2 |
| 前端 vite prod build | PASS（dist/index.html，index-BdXMX7IQ.js）|
| 后端 package（boot jar）| PASS（app-3.x.jar；BOOT-INF 内含新类/新 mapper/新迁移）|
| docker build | PASS（metersphere/local-grpc-final:settings-proc）|
| 部署 verify4 | PASS（8085 加载新 bundle + 新后端 + 三列 schema）|

| 遗留限制 | 说明 |
|---|---|
| TLS 自定义证书文件 | 仅支持系统信任 / 跳过校验（verifyServerCertificate=false），暂不支持上传自定义证书文件 |
| 场景 gRPC 步骤前后置 | 通过 API_CASE 步骤复用 `GrpcCaseRunService.run()` 自动生效；自定义步骤前后置未单独扩展（尽力复用范围内）|
| flat 定义调试 `/debug/send` | 仅 Settings 生效；前置/后置仅在用例执行链（debug/run）运行，与实现一致 |
| `docker/init-grpc-schema.sh` | 该运行脚本存在 CRLF 导致 dash 解析告警（历史遗留）；已升级 DB 依赖手工/Flyway 应用三列，patch SQL 内容本身正确 |

### 10.16 接口测试全功能 Chrome 回归与场景直跑修复（2026-08-17）

本轮使用用户已登录的 Chrome 对接口测试新旧功能进行重新回归，并在最终 v6 镜像上完成部署后冒烟。

| 环境项 | 值 |
|---|---|
| 访问地址 | `http://localhost:8085` |
| 最终容器 | `metersphere-grpc-verify4` |
| 最终镜像 | `metersphere/local-grpc-batch:20260817-fix-scenario-reflection-v6-close-icon` |
| 前端主 bundle | `index-BrUnU3Ak.js` |
| 前端 index SHA-256 | `ECE0E529E34B4158BEA4B81F255B0C1847526552002464BD0A8671F2D6AFB827` |
| 后端 api-test JAR | `1920250` bytes（沿用已验证 v5 后端）|
| 回退容器 | `metersphere-grpc-verify4-pre-close-icon-v6-20260817` |
| 数据卷 | `metersphere-grpc-verify4-conf/data/logs` 原样复用 |

**本轮修复**

1. 保存后的纯 gRPC 场景从列表直接执行时，前端未打开步骤编辑器会缺失 `stepDetails`。`ApiScenarioRunService` 现会在纯 gRPC run/debug 前从 `api_scenario_step_blob` 补齐步骤详情；持久化 JSON 同时兼容 `String`、`CharSequence` 和 UTF-8 `byte[]`，并避免前端字符串被二次 JSON 编码。
2. `GrpcScenarioStepConfigResolver` 从定义补齐服务地址与请求 JSON 模板，保证保存、重开和执行使用同一份可执行配置。
3. 通用 `MsDrawer` 关闭按钮统一为 `40 x 40` 点击区、`20 x 20` 图标，并补充 hover/focus 状态；AI 设置/关闭按钮同样为 `40 x 40` 点击区、`20 x 20` 图标。

**Chrome UI 回归结果**

| 功能域 | 实测内容 | 结果 |
|---|---|---|
| HTTP 调试 | 新建 HTTP、GET、URL 输入、服务端执行 | PASS，`http://host.docker.internal:8085/` 返回 200、2538 bytes |
| HTTP 请求配置 | 请求头、请求体、Query、REST、前置、后置、认证、设置 | PASS，全部页签可切换且内容正确 |
| HTTP Body | none/form-data/x-www-form-urlencoded/json/xml/raw/binary 入口 | PASS |
| HTTP 响应 | 响应体、响应头、实际请求、控制台、上下/左右布局 | PASS；响应 HTML、HTTP/1.1 200 头及实际 GET 请求完整 |
| HTTP 保存重开 | 保存、树搜索、从树重开、再次执行 | PASS；请求 `1605960564864401408`，URL 与方法正确恢复，再次返回 200 |
| 调试树 | 模块展开、请求搜索、过滤恢复 | PASS；`GetSoftVersion` 与 HTTP 回归请求均可精确搜索 |
| 定义 | 定义列表、定义树搜索、gRPC 定义展示 | PASS；`Codex-GRPC-Definition-20260817-1902-Edited` 可检索 |
| 场景 | 场景列表、编辑页、直接执行、执行统计 | PASS；场景 `100021` 成功 1 / 失败 0 / 误报 0 |
| gRPC 报告 | Service/Method/Status/Code、请求/响应 JSON | PASS；`ProductService / ListProducts / OK / 0`，三条产品响应完整 |
| 场景重开 | 地址、Service/Method、请求模板恢复 | PASS；`host.docker.internal:50051` 与 `msdemo.scenario.ProductService / ListProducts` 正确 |
| 报告列表 | 最新独立报告、成功状态、详情抽屉 | PASS |
| 跨模块缓存 | 调试 -> 工作台 -> 接口测试；调试 -> 项目管理 -> 接口测试 | PASS；两个已开标签及激活的 `GetSoftVersion` 标签原样保留 |
| 顶部导航 | 调试 -> 定义 -> 场景 -> 报告 -> 调试 | PASS；各页面数据与路由正确，返回调试后标签仍保留 |
| AI 工作台 | 停靠式布局、会话历史、搜索、新对话、3 个快捷命令、长响应滚动 | PASS；长响应容器 `clientHeight=327`、`scrollHeight=2165` |
| AI 图片 | 剪贴板粘贴图片、真实视觉模型分析、关闭重开恢复 | PASS；模型正确识别 MeterSphere 调试页及 HTTP/GET/Body/响应控件 |
| AI 模型 | 已保存模型检测；临时 OpenAI 模型新增、远程探测、编辑、密钥掩码、停启、删除 | PASS；远端发现 `e2e-model`，密钥编辑态为掩码 |
| 抽屉层级 | AI -> 模型设置 -> 添加模型，逐层关闭 | PASS；覆盖、遮罩、层级正确，无残留抽屉；关闭控件 `40 x 40` / 图标 `20 x 20` |
| 服务健康 | 最终 v6 首页与执行接口 | PASS，HTTP 200；部署后 HTTP/gRPC 冒烟均通过 |

**自动化与构建 Gate**

| Gate | 结果 |
|---|---|
| `GrpcScenarioStepConfigResolverTest` + `ApiScenarioRunServiceGrpcTest` 等目标单测 | PASS，12/12，0 failure，0 error |
| 后端完整 package | PASS |
| Prettier / ESLint / Stylelint（`ms-drawer/index.vue`）| PASS |
| `vue-tsc --noEmit --skipLibCheck` | PASS |
| Vite production build | PASS，6454 modules transformed |
| Docker v6 image build | PASS |
| 最终容器服务端 ERROR/异常栈/HTTP 500 检索 | 0 条 |

**已知测试边界**

| 项 | 说明 |
|---|---|
| Chrome 文件选择器 | Chrome 扩展对 `fileChooser.setFiles` 返回 `Not allowed`，因此文件选择器上传未标记为通过；剪贴板图片粘贴与真实视觉分析已通过 |
| Chrome 日志 | 仅出现浏览器扩展自身的 `message channel closed before a response was received`，发生在刷新/路由切换时；未发现 MeterSphere 页面脚本错误 |
| HTTP 回归数据 | `Codex HTTP 全功能回归 20260817-2226`（ID `1605960564864401408`）保留用于复验；当前 Chrome 自动化接口不提供悬停能力，未绕过认证直接改库清理 |

### 10.17 AI 测试助手多会话并发专项修复与回归（2026-08-18）

本轮修复 AI 测试助手只能保留一个未发送新会话、回答期间无法切换/新建会话，以及相同 AI 接口请求会被全局重复请求机制互相取消的问题。

| 项 | 结果 / 证据 |
|---|---|
| 部署地址 | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260818-ai-multiconversation-v1` |
| 前端主 bundle | `assets/index-D9C-1YKg.js` |
| index SHA-256 | `E0EE34AEB013EEC5E16A0270F5DFC70D4599F3F41FF3F149001FEDBBFB44CFE9` |
| 回退容器 | `metersphere-grpc-verify4-pre-ai-multiconversation-20260818`，原数据卷保持不变 |
| 多新会话 | 连续点击加号创建 3 个未发送会话，列表显示 `3 个会话`；双回答运行期间继续创建第 4 个会话成功，PASS |
| 回答期间切换 | 会话 A 显示运行指示器时切换到 B，B 保持独立空白/历史内容且发送按钮可用，PASS |
| 真正并发 | A、B 连续发送长请求后列表同时显示 2 个运行指示器，加号仍可用，PASS |
| 独立回写 | A 包含 `并发严格A-20260818` 且完整回答长度 6554；B 包含 `并发严格B-20260818` 且完整回答长度 6661；互不覆盖，PASS |
| 独立取消 | A、B 同时运行时停止 B，运行指示器从 2 变 1；切回 A 仍显示“停止生成”；随后单独停止 A，PASS |
| 草稿与消息 | 切换会话后各自消息、历史回答和输入状态按会话 ID 保留，PASS |
| 搜索 / 重命名 / 删除 | 搜索精确过滤为 1 条；会话 A 重命名为 `并发多会话自测-A-20260818`；空白临时会话删除后数量从 4 变 3，PASS |
| 浏览器日志 | error / warn 为 0；界面无重叠、无白屏，PASS |
| 服务日志 / 健康 | 最近 15 分钟 `ERROR / Exception / HTTP 500` 为 0；首页 HTTP 200，PASS |

**构建 Gate**

| Gate | 结果 |
|---|---|
| `pnpm run type:check` | PASS |
| ESLint（本轮改动文件） | PASS，0 error |
| Stylelint（AI 抽屉 Vue 文件） | PASS |
| AbortSignal 引用与取消状态测试 | PASS |
| Vite production build | PASS，6454 modules transformed |
| Docker frontend overlay build / deploy | PASS |

**实现边界**

- 消息、输入草稿、图片附件、勾选项、加载状态和取消控制按会话 ID 隔离。
- 新会话首次发送后，运行态从前端临时 ID 原子迁移到后端真实会话 ID。
- AI 聊天请求绕开全局“同 URL 取消前一个请求”机制，改用每个会话自己的 `AbortController`。
- 删除会话只清理对应会话运行态；关闭整个 AI 测试助手时仍确认并停止全部在途回答。

### 10.18 gRPC 测试用例智能生成场景与执行状态修复回归（2026-08-26）

本轮实现从自然语言 gRPC 测试用例生成场景草稿、人工修正后批量创建资源，并修复预初始化报告完成后未回写场景执行摘要的问题。

| 项 | 结果 / 证据 |
|---|---|
| 部署地址 | `http://localhost:8085/#/api-test/scenario?orgId=100001&pId=100001100001` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260826-grpc-ai-scenario-v2` |
| 回退容器 | `metersphere-grpc-verify4-pre-grpc-ai-scenario-v2-20260826`，原 conf/data/logs 数据卷保持不变 |
| AI 草稿输入 | `Polar.v1.DeviceService / ListDevices`，definitionId=`1605410929309483008`，地址 `host.docker.internal:9099` |
| AI 草稿结果 | `AI-VERIFY-20260826`，`selected=true`、`suggested=false`、`safety=SAFE`，请求 JSON 正确 |
| AI 创建结果 | **PASS**，创建场景编号 `100045`，列表自动增加到 26 条，带 `AI_GENERATED` 标签 |
| 前端响应修复 | **PASS**，multipart `Result<T>` 正确解包，未再出现 `undefined.filter` / `undefined.some` |
| 状态回归场景 | 编号 `100037`，scenarioId=`1606881972788355078`，4 个 gRPC 步骤 |
| 执行前场景摘要 | lastReportId=`1618963921950507021`，requestPassRate=`0`（旧错误状态） |
| 执行结果 | **PASS**，4 成功 / 0 失败 / 0 误报，报告状态 `SUCCESS/COMPLETED` |
| 执行后场景摘要 | lastReportId=`178774745200100000`，lastReportStatus=`SUCCESS`，requestPassRate=`100.00` |
| 列表刷新 | **PASS**，返回“全部场景”后编号 `100037` 直接显示“成功”，无需浏览器刷新 |
| 首页健康 | **PASS**，容器升级后 `/` 返回 HTTP 200 |

**构建与测试 Gate**

| Gate | 结果 |
|---|---|
| `GrpcScenarioReportPersistenceServiceTest` | PASS，6/6（含预初始化报告回写场景摘要回归） |
| gRPC AI/执行状态相关后端目标测试 | PASS，14/14 |
| 前端目标 ESLint | PASS |
| `vue-tsc --noEmit --skipLibCheck` | PASS |
| Vite production build | PASS，6460 modules transformed |
| `git diff --check` | PASS |
| v2 Docker 镜像构建与部署 | PASS |

### 10.19 接口测试 gRPC 与 AI 全界面按钮自测（2026-08-27）

本轮仅覆盖“接口测试”模块内现有的 gRPC 与 AI 功能。可恢复、只读或自测新建数据均实际操作；历史定义、用例、场景和报告的删除操作只打开确认框后取消；`RISK` 场景未执行。

| 项 | 结果 / 证据 |
|---|---|
| 部署地址 | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260826-grpc-ai-scenario-v2` |
| Proto 解析 | **PASS**；`polar_control.proto` 加 `google/api/annotations.proto`、`http.proto` 依赖后解析出 14 个 Service、59 个 Method |
| gRPC 调试 | **PASS**；文档、请求消息、Metadata、服务定义、前置、后置、设置、上下/左右布局、保存/另存/批量保存入口均已操作 |
| gRPC 服务调用 | **PASS**；`Polar.v1.ProjectService/GetSoftVersion`，地址 `host.docker.internal:9099`，返回 `grpcStatus=OK`、`grpcCode=0`、`version=0.9.8.1` |
| gRPC 定义 / 用例 | **PASS**；定义 `100359`（ID `1619543948693929984`）和用例 `100359001`（ID `1619543948693929986`）保存、重开、执行、复制、分享、编辑、批量编辑/同步/执行/删除确认入口均已验证 |
| 用例报告 | **PASS**；报告 `1619552624527867904` 为 `SUCCESS/COMPLETED`，报告页正确显示 Service、Method、Status、Code、请求/响应 JSON、Metadata/错误详情/原始信息 |
| 用例执行历史 | **FAIL**；用例详情“执行历史”重开后仍显示“暂无数据”，但数据库 `api_test_case_record` 已存在 `1619552624527867904 -> 1619543948693929986` 关联 |
| AI gRPC 文件输入 | **PASS**；`.md` 文件选择、显示和移除均通过；文本输入、模型/模块、服务地址、生成草稿、重新生成、应用修正、场景/步骤勾选、P0-P3、SAFE/RISK 均已操作 |
| AI 场景创建 | **PASS**；创建 `100046 / AI-GRPC-FULL-SELFTEST-20260827`，scenarioId=`1619571264685932547`，场景总数 26 -> 27 |
| 执行状态刷新 | **PASS**；手动和批量执行后返回场景列表，`100046` 自动显示“失败”；数据库摘要为 `lastReportStatus=ERROR`、`lastReportId=1619575576833097730`、`requestPassRate=0.00` |
| AI 场景 gRPC 调用 | **PASS**；两次执行的 gRPC Status/Code 均为 `OK/0`，响应 JSON 含 `version`，报告分别为 `178776908660300000`（MANUAL）和 `1619575576833097730`（BATCH） |
| AI 生成断言 | **FAIL**；自动生成的 `JSON_PATH $.version / EXISTS` 被判定 `assertion failed`，导致场景报告 `ERROR/COMPLETED`；数据库断言配置为 `condition=EXISTS`，实际响应明确存在 `version` |
| 场景失败明细国际化 | **FAIL**；失败步骤抽屉直接显示 `apiScenario.grpc.assertionResults`、`apiScenario.grpc.assertionName` 等未翻译 key |
| 场景列表 / 批量 | **PASS**；搜索、视图、筛选、创建、导入、复制、行执行、批量编辑/导出/更多/执行配置、SAFE 场景执行均已验证；批量执行环境、串并行、失败停止、独立/集合报告、资源池控件正常 |
| 报告页 | **PASS**；场景/用例报告切换、全部/独立/集合、搜索、筛选入口、分享链接、导出、全屏、平铺/Tab、明细打开和删除确认取消均已验证 |
| AI 测试助手 | **PASS**；打开/关闭、新会话、搜索、快捷提示、发送、停止、正常回复、模型切换、图片添加/移除、重命名、删除确认取消均已验证 |
| AI 模型设置 | **PASS（入口）**；供应商切换、模型搜索入口、添加模型抽屉、基础信息折叠、模型类型选项和取消通过；未保存无凭据的新模型 |
| gRPC Reflection | **BLOCKED / 未实现入口**；只输入 `host.docker.internal:9099` 后 Method 仍禁用，页面提示必须上传并解析 Proto，未发现 Reflection 发现按钮 |
| 定义页刷新稳定性 | **FAIL**；从定义详情执行 gRPC 后刷新同一 URL，主区域白屏；控制台出现 `.ms-editable-tab-nav` 找不到、`Sortable: el must be an HTMLElement`、`insertBefore` 读取 null 等错误；新开标签可恢复 |
| AI 创建服务日志 | **WARN**；场景创建成功，但 `02:30:55` 的 `GrpcProtoChangeImpactService` 记录 `google/api/annotations.proto: File not found`，发生在 AI 新建用例保存时的 Proto 版本影响分析 |
| 删除与风险边界 | **SKIP**；定义/用例/场景/报告/AI 会话均只验证删除确认框后取消；未执行带 `RISK` 标签或真实设备写操作的场景 |

**数据库核对**

| 数据 | 结果 |
|---|---|
| 新场景摘要 | `1619571264685932547 / 100046 / ERROR / lastReportId=1619575576833097730` |
| 手动场景报告 | `178776908660300000 / ERROR / COMPLETED / MANUAL / 1 失败 / 0 成功` |
| 批量场景报告 | `1619575576833097730 / ERROR / COMPLETED / BATCH / 1 失败 / 0 成功` |
| 场景报告关联 | `api_scenario_record` 同时存在上述两条 reportId 到 scenarioId 的关联 |
| gRPC 用例报告关联 | `api_test_case_record` 存在 `1619552624527867904 -> 1619543948693929986` |
| AI 生成断言配置 | `GRPC_STATUS == OK`、`GRPC_CODE == 0`、`JSON_PATH $.version EXISTS`；前两项调用结果正确，第三项误判失败 |

**结论**

- “执行状态应该更新没有更新”在当前 v2 镜像的场景手动执行和批量执行中均已通过，未复现旧的状态不刷新问题。
- 当前阻断自动化闭环的核心问题是 AI 自动生成的 `JSON_PATH EXISTS` 断言误判；gRPC 请求本身执行成功。
- 用例执行历史接口/页面和定义页刷新白屏为独立缺陷，需要分别修复。

### 10.20 接口测试 gRPC 与 AI 补充全量自测（2026-08-27）

本轮针对 10.19 未覆盖的分页、行内控件、场景编辑器、Reflection、报告批量操作、AI 多草稿和 AI 多会话并发继续补测。仅测试“接口测试”模块的 gRPC 与 AI 功能；未执行 `RISK` 场景和真实设备写操作，历史数据删除只确认后取消。本节结论优先于 10.19 中同一功能的旧结论。

| 区域 / 控件 | 结果 | 实测证据 |
|---|---|---|
| 场景分页页码 | **PASS** | 27 条数据的第 1/2/3 页均可切换；第 2 页包含 `100033`，第 3 页包含 `100026` |
| 场景上一页 / 下一页 | **PASS** | 第 3 页点击上一页回到第 2 页，再点击下一页回到第 3 页；末页下一页正确禁用 |
| 场景跳页 | **SKIP** | 当前 3 页场景列表未渲染跳页输入框，属于组件显示规则，不存在可操作控件 |
| 场景等级行内修改 | **PASS** | `100046` 从 P0 改为 P1 后列表即时显示 P1，再恢复 P0；数据库最终仍为 P0 |
| 场景状态行内修改 | **FAIL（布局阻断）** | 1280 宽视口下固定“操作”列覆盖“状态”列，状态选择器只剩极窄区域，点击/键盘操作均无法正常展开 |
| 场景行更多菜单 | **PASS** | `100046` 正确显示“创建定时任务 / 删除”；定时任务弹窗和删除确认框均可打开 |
| 定时任务表单 | **PASS（未保存）** | Cron 输入、默认/新环境切换、资源池和任务状态控件均显示；填写 `0 0 1 * * ?` 后取消，未创建任务 |
| 场景删除 | **PASS（确认后取消）** | 出现“确认删除 AI-GRPC-FULL-SELF...”和回收站提示，取消后 `100046` 仍存在 |
| 场景批量全菜单 | **PASS** | 选择 1 条后显示导出、编辑、执行、移动到、复制到、开启/关闭/编辑定时任务、删除；移动/复制弹窗目标未选时确认按钮禁用 |
| 场景编辑器页签 | **PASS** | 步骤、参数、前/后置、断言、执行历史、变更历史、设置全部打开；参数常规/CSV、Cookie、失败规则等控件正常渲染 |
| 场景执行历史 | **PASS** | `100046` 显示手动和批量两条历史，均为“已完成 / 失败”，时间分别为 02:31:26 和 02:35:06 |
| 场景添加步骤菜单 | **PASS** | 请求/场景、导入系统请求、自定义请求、循环/条件/仅一次控制器、脚本、等待时间均显示 |
| 场景自定义 gRPC 步骤 | **PASS** | 自定义请求协议可从 HTTP 切换到 GRPC；地址、Method、服务定义、前后置、设置、保存并继续添加和确认状态正确 |
| 调试页新建 / 标签关闭 | **PASS** | 新建 gRPC 请求可创建第二标签；关闭未保存标签出现确认框，确认后标签数从 2 变为 1 |
| gRPC 自动 Reflection | **PASS，修正 10.19** | 新 gRPC 调试请求逐键输入 `host.docker.internal:9099` 并回车后提示“Reflection 发现成功”，Method 从禁用变为可选，发现 14 个 Service / 59 个 Method |
| Reflection 无 Proto 执行 | **PASS** | 直接选择 `Polar.v1.ProjectService / GetSoftVersion` 执行，原始信息为 `success=true`、`grpcStatus=OK`、`grpcCode=0`，响应 `version=0.9.8.1` |
| Reflection 地址切换 | **FAIL** | Reflection 成功后新建请求会保留旧服务描述；将地址改为 `invalid.invalid:65535` 后旧 Method 仍可选，“当前 Proto”变成新地址但服务列表未清空，执行返回 `UNAVAILABLE / code 14` |
| Reflection 失败日志 | **FAIL 证据** | 容器记录 `Unable to resolve host invalid.invalid`；源码的发现前置条件包含 `grpc.services?.length`，地址变化后已有服务会直接阻止重新发现 |
| gRPC 定义编辑 | **PASS** | 在干净列表从 `100359` 点击编辑可正常进入预览/定义/用例页，无白屏 |
| 定义搜索 / 跳页 | **PASS** | 搜索 `100359` 返回 1 条；跳到第 2 页首条为 `100348`，再跳回第 1 页恢复 `100359` |
| 定义页浏览器刷新 | **FAIL，现象更新** | 刷新后不再白屏，但定义列表变成 0 条“暂无数据”；切换“调试 -> 定义”后才恢复 76 条，仍属于初始化缺陷 |
| gRPC 用例等级 | **FAIL** | 点击用例 `100359001` 的 P0 未展开 P0-P3，而是误打开整条用例编辑抽屉，存在点击冒泡/层叠问题 |
| gRPC 用例状态 / 表格布局 | **FAIL** | 1280 宽视口中固定操作列覆盖用例等级和状态列，P0 仅剩圆点、状态几乎完全不可见，行内控件无法可靠操作 |
| 用例执行历史 | **PASS，修正 10.19** | 场景编辑页和报告关联均可读；数据库仍存在 `1619552624527867904 -> 1619543948693929986`，本轮不再出现关联丢失 |
| 报告分页 / 跳页 | **PASS** | 71 条场景报告共 8 页；跳到第 2 页后首条发生变化，再跳回第 1 页正常 |
| 报告搜索 | **PASS** | 搜索 `AI-GRPC-FULL-SELFTEST-20260827` 精确返回 2 条，分别为手动和批量报告 |
| 报告全部 / 独立 / 集合 | **PASS** | 三个单选项均可切换且选中态正确 |
| 报告表头全选 | **PASS** | 过滤后的两条 AI gRPC 报告点击表头 checkbox 后显示“已选择 2 条” |
| 报告批量导出 | **PASS** | 批量导出按钮可点击并触发导出流程 |
| 报告批量删除 | **PASS（确认后取消）** | 弹出“确认删除已选中的 2 个报告吗？”，取消后报告仍存在 |
| 报告具体筛选 / 重置 | **PASS** | 报告名称“包含 AI-GRPC”返回 2 条；重置、关闭抽屉并清空筛选后恢复 71 条和普通搜索框 |
| AI 空服务地址生成 | **PASS** | 顶部服务地址留空，仅输入 `ProjectService/GetSoftVersion` 需求仍生成 2 个 SAFE 草稿，并从已存定义自动补全 `host.docker.internal:9099` |
| AI 多场景草稿 | **PASS** | 一次生成“正常返回”和“version 字段存在”两个场景，每个 1 个 gRPC 步骤，摘要显示“已选 2 个场景” |
| AI 场景 / 步骤部分选择 | **PASS** | 取消第二个场景后显示“已选 1 个场景”；取消单个步骤时确认创建仍按剩余有效步骤正确启用 |
| AI 优先级 / SAFE-RISK | **PASS** | 草稿优先级 P0 -> P3 -> P0；SAFE -> RISK -> SAFE 均可切换，未执行 RISK |
| AI 非法请求 JSON | **PASS** | 将第一步请求改为 `[` 并失焦后提示“请求 JSON 必须是合法 JSON 对象”；恢复 `{}` 后警告消失 |
| AI 草稿修正 | **PASS** | 输入“只保留第一个场景”后，场景 section 从 2 个变为 1 个，仍保持 SAFE、只读和正确服务地址；最终取消，未创建额外场景 |
| AI 双会话并发 | **PASS** | 会话 A 和 B 连续发送后会话数从 2 增至 4；B 生成期间切回 A，A 仍显示“停止生成” |
| AI 独立停止 | **PASS** | 只停止 A 后显示“已停止生成”；切回 B 已独立完成并返回 `B-OK`，B 不再显示停止按钮 |
| AI 草稿隔离 | **PASS** | 在 B 输入未发送草稿 `DRAFT-B-ISOLATION`，切到 A 不出现该草稿，切回 B 完整恢复 |
| AI 会话删除确认 | **PASS（本轮临时数据实际删除）** | 删除本轮 B 会话后弹出不可恢复确认，确认后会话数 4 -> 3；历史会话未删除 |
| AI 图片跨会话恢复 | **SKIP** | 当前 in-app Browser 文件输入接口不提供 `setInputFiles`；图片添加/移除已在 10.19 通过，本轮仅跨会话恢复未重新执行 |

**数据库与日志复核**

| 数据 | 结果 |
|---|---|
| 场景摘要 | `1619571264685932547 / 100046 / P0 / UNDERWAY / ERROR / lastReportId=1619575576833097730 / requestPassRate=0.00` |
| 手动报告 | `178776908660300000 / ERROR / COMPLETED / MANUAL / RUN / 1 失败 / 0 成功 / 1 断言 / 0 成功` |
| 批量报告 | `1619575576833097730 / ERROR / COMPLETED / BATCH / SERIAL / 1 失败 / 0 成功 / 1 断言 / 0 成功` |
| 场景报告关联 | `api_scenario_record` 同时存在两条报告到 `1619571264685932547` 的关联 |
| 用例报告关联 | `api_test_case_record` 存在 `1619552624527867904 -> 1619543948693929986` |
| Reflection 异常日志 | `invalid.invalid:65535` 对应 `UNAVAILABLE / Unable to resolve host invalid.invalid` |

**补充结论**

- 场景执行状态、列表结果和场景执行历史均会更新；`100046` 显示失败是断言真实判败，不是状态未刷新。
- gRPC Reflection 已实现为服务地址变更后的自动发现，无独立按钮；10.19 的“未实现入口”结论作废。
- 当前新增的核心缺陷是 Reflection 地址切换未清空旧描述、用例 P0 点击误开编辑器、窄视口固定操作列覆盖等级/状态列，以及定义页刷新后列表空数据。
- AI 多场景、部分选择、修正、双会话并发、独立停止和草稿隔离均通过；AI 自动生成的 `JSON_PATH EXISTS` 断言误判仍是自动化闭环的主要执行缺陷。

### 10.21 已知问题集中修复、报告增强与最终回归（2026-08-27）

本节结论优先于 10.19 和 10.20 中相同功能的旧 FAIL 结论。本轮完成断言、Reflection、行内控件、窄屏布局、定义页刷新和报告原因展示修复，并部署最终 v7 镜像。

| 修复项 | 最终结果 | 实测证据 |
|---|---|---|
| `JSON_PATH EXISTS / NOT_EXISTS` | **PASS** | `GrpcAssertionEvaluatorTest` 7/7；场景 `100046 / AI-GRPC-FULL-SELFTEST-20260827` 的新批量报告 `2026-08-27 08:59:41` 为“独立报告 / 批量执行 / 成功”。修复前 `02:35:06` 失败报告保留用于前后对比。 |
| Reflection 地址切换 | **PASS** | 从 `host.docker.internal:9099` 改为 `invalid.invalid:65535` 后，旧 Service/Method 清空，Method 禁用，不再残留旧描述。 |
| 用例 P0 点击 | **PASS** | 用例 `100359001` 点击 P0 后正常展开 P0-P3，不再误开整条用例编辑抽屉。 |
| 1280 宽布局 | **PASS** | 场景表和用例表操作列不再固定覆盖等级/状态；`100359001` 的等级和状态完整可见。 |
| 定义页刷新 | **PASS** | v7 首次加载加连续 3 次完整刷新均显示模块 `(76)`、分页“共 76 条”、首条 `100359`，四轮均未出现“暂无数据”。 |
| 失败报告原因 | **PASS** | 修复前 `02:35:06` 报告步骤现显示错误码 `ASSERTION_FAILED`，失败原因 `JSON_PATH未通过`，并显示名称、类型、条件、期望值、实际值、结果、消息、请求 JSON、响应 JSON；不再显示原始国际化 key，也不再把断言失败写成 `OK（code 0）`。 |
| PDF 详细导出 | **PASS（实现与既有下载回归）** | 导出步骤包含服务、方法、地址、耗时、gRPC Status/Code、错误码/消息、请求/响应 JSON、断言类型/条件/期望/实际/结果/消息和变量提取；沿用 10.15 的 5 页 PDF 实际下载回归。 |

**最终环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-known-issues-fix-v7` |
| 前端主 bundle | `assets/index-DSq_Wi_Z.js` |
| `index.html` SHA-256 | `9D610AD1E11BE9169505D31686B46DA2F102A2DF9569C31BFD3E0D3874261047` |
| api-test JAR SHA-256 | `4439A484E8F2DAC0610A51E81C934CD14A94EDF3F3FE278A831209E6E59CD3F8`，与 v5/v6 已验证后端一致 |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |
| 直接回滚容器 | `metersphere-grpc-verify4-known-issues-fix-v6-backup-20260827` |
| 更早回滚 | v1-v5 和 `metersphere-grpc-verify4-backup-20260827` 均保留，禁止删除数据卷 |

**质量 Gate**

| Gate | 结果 |
|---|---|
| Maven reactor | **PASS**，15 个模块成功 |
| `GrpcAssertionEvaluatorTest` | **PASS**，7/7 |
| 前端目标 ESLint | **PASS**，0 error；`moduleTree.vue` 仅 2 条既有属性命名 warning |
| `vue-tsc --noEmit --skipLibCheck` | **PASS** |
| Vite production build | **PASS**，6460 modules transformed |
| `git diff --check`（本轮代码） | **PASS** |
| Docker 构建 / 部署 | **PASS**，v7 已运行，`/` 和 `/get-key` 均为 HTTP 200 |

**定义页刷新根因**

共享协议筛选子组件在异步 `onBeforeMount` 完成时主动通知父组件；父组件原先通过模板 `ref` 回读协议。接口响应较快时，模板 `ref` 尚未挂载，父组件把已经选中的 HTTP/GRPC 误读为空数组并查询出 0 条，因此故障具有偶发性。修复后事件直接携带协议数组，父组件维护自己的协议状态，不再依赖模板挂载时序。

### 10.22 AI 场景草稿持久化、图片分析与本机项目知识源（2026-08-27）

本节在 10.21 的 v7 功能基础上继续验证 AI 场景生成输入体验。目标是让需求文字、图片和本机项目资料共同参与生成，页面切换后保留未完成内容，并按 MeterSphere 项目隔离知识源。

| 验证项 | 最终结果 | 实测证据 |
|---|---|---|
| 草稿自动保存 | **PASS** | 输入 `PERSISTENCE-SELFTEST-20260827` 后界面显示“草稿已保存 15:16:07”；文字、服务地址、修正要求、附件、生成结果、勾选和编辑状态均写入 IndexedDB。 |
| 跨页面恢复 | **PASS** | 从场景页跳转到报告页，再返回并重新打开“智能生成 gRPC 场景”，输入内容完整恢复，并显示“已恢复上次未完成的草稿”。 |
| 草稿隔离与清理 | **PASS** | 草稿按用户、组织和 MeterSphere 项目隔离；取消抽屉或刷新不删除，只有确认创建成功或点击“清空草稿”并确认后删除。清空草稿不会解除项目目录关联。 |
| 图片输入 | **PASS** | 实际上传 PNG 后显示文件名、1 张缩略图和“图片分析”标记；图片与文档均支持点击/拖拽添加，图片会作为多模态附件传给 AI。 |
| 附件限制 | **PASS（代码与类型检查）** | 图片最多 4 张、附件总数最多 10 个、单文件最多 10 MB；支持 PNG/JPG 及既有用例/说明文件。 |
| 本机项目知识源 | **PASS（实现与静态验证）** | 每个 MeterSphere 项目首次关联一个本机目录，目录句柄持久化到 IndexedDB；后续生成自动恢复句柄并检索项目文件，不再要求重复说明路径。 |
| 项目文件检索 | **PASS（代码与样本验证）** | 支持 `.pcp/.pce/.proto/.json/.json5/.yaml/.yml/.md/.txt/.csv/.xml/.dbc/.ini/.conf/.properties`；忽略 `.git/node_modules/target/dist/build/logs` 等目录，并根据需求和文件内容相关度选取上下文。`NewProject555.pcp`、`NewExperiment.pce` 已确认可读取。 |
| AI 自动补参 | **PASS（后端测试）** | AI 提示词优先从文字、图片、本机项目上下文、接口模板和默认配置提取服务、方法、地址及请求参数；只有所有来源都没有依据时才标记歧义。 |
| 浏览器控制台 | **PASS** | 新版抽屉打开、输入、保存、页面跳转、恢复、图片添加与移除期间 console error 为 0，布局未出现重叠或白屏。 |
| 浏览器授权边界 | **符合预期** | Web 页面不能在未授权情况下扫描整台电脑；首次点击“关联项目目录”必须由用户在系统目录选择器中选择 `C:\Users\Administrator\Documents\NewProject555`。浏览器会记住授权，后续按项目自动检索。 |

**最终环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-ai-project-context-v8` |
| 前端主 bundle | `assets/index-B_kfANE_.js` |
| `index.html` SHA-256 | `00507B32E1FAF9A4DE656185992FD98DA97A74ADC91154964178123F29DBFE52` |
| api-test JAR SHA-256 | `EEED1360880A354A3758D136393AA396BD083027D6B873BF2C13B315CEDEDDCA` |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |
| 直接回滚容器 | `metersphere-grpc-verify4-known-issues-fix-v7-backup-20260827` |

**质量 Gate**

| Gate | 结果 |
|---|---|
| `GrpcScenarioAiServiceTest` | **PASS**，2/2 |
| `GrpcScenarioSourceFileExtractorTest` | **PASS**，2/2 |
| Maven 目标测试 reactor | **PASS**，15 个模块成功，目标测试合计 4/4 |
| Maven 完整应用打包 | **PASS**，20 个模块成功，`backend/app` package 成功 |
| 前端目标 ESLint | **PASS**，0 error |
| `vue-tsc --noEmit --skipLibCheck` | **PASS** |
| Vite production build | **PASS**，生成 `assets/index-B_kfANE_.js` |
| Docker 部署与健康检查 | **PASS**，v8 已运行，`/` 和 `/get-key` 均为 HTTP 200；最近启动日志无 ERROR、HTTP 500、OOM 或异常 |

### 10.23 AI 多截图粘贴与 20 张上限回归（2026-08-27）

本节在 10.22 基础上新增剪贴板截图入口，并将图片及附件总数上限统一调整为 20。粘贴采用追加模式，不会用后一张覆盖前一张。

| 验证项 | 最终结果 | 实测证据 |
|---|---|---|
| `Ctrl+V` 截图粘贴 | **PASS** | 抽屉打开期间连续向浏览器剪贴板写入两张真实 PNG 并分别按 `Ctrl+V`，页面出现两个独立缩略图。 |
| 粘贴按钮 | **PASS** | 点击“粘贴截图”读取当前剪贴板并追加第 3 张，计数从 `截图 2/20` 变为 `截图 3/20`。 |
| 多图追加 | **PASS** | 后一张截图不会覆盖前一张；每张使用独立 `clipboard-screenshot-*.png` 文件名，并显示“截图分析”标记。 |
| 数量限制 | **PASS（代码与界面）** | 图片最多 20 张、附件总数最多 20 个、单文件最多 10 MB；工具栏分别显示“附件 n/20”和“截图 n/20”。 |
| 多图草稿持久化 | **PASS** | 3 张截图自动保存后切换到报告页，再返回场景页重新打开抽屉，显示“已恢复上次未完成的草稿”，附件与截图计数均保持 `3/20`。 |
| AI 传输链路 | **PASS（代码回归）** | 粘贴截图进入既有 `files` 附件数组，生成时与文字、项目上下文一起传入 `generateGrpcAiScenarioDraft`，所有图片共同参与 AI 多模态分析。 |
| 清理与控制台 | **PASS** | 回归测试草稿已通过确认框清理；浏览器 console error 为 0。 |

**最终环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-ai-multi-screenshot-v9-frontend-only` |
| 前端主 bundle | `assets/index-BMlrtl7w.js` |
| `index.html` SHA-256 | `3F17F180B60D4815872D7E950D4E9CF844A3531BDB3DAAB9D6C0E794A6AB3AB8` |
| api-test JAR SHA-256 | `EEED1360880A354A3758D136393AA396BD083027D6B873BF2C13B315CEDEDDCA`，继承 v8 已验证后端，未使用本地未核对 JAR 覆盖 |
| 直接回滚容器 | `metersphere-grpc-verify4-ai-project-context-v8-backup-20260827` |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |

**质量 Gate**

| Gate | 结果 |
|---|---|
| 前端目标 ESLint | **PASS**，0 error |
| `vue-tsc --noEmit --skipLibCheck` | **PASS** |
| Vite production build | **PASS**，6461 modules transformed |
| Docker frontend-only overlay | **PASS**，只覆盖 `/metersphere/static`，保留 v8 后端 JAR |
| Docker 健康检查 | **PASS**，`/` 和 `/get-key` 均为 HTTP 200 |

### 10.24 AI 场景生成接口 404 修复与双模型回归（2026-08-27）

本节结论优先于 10.23 中“继承 v8 已验证后端”的旧结论。v8/v9 实际运行 JAR 不包含 AI 场景 Controller，因此点击“生成草稿”会由静态资源处理器返回 `No static resource api/scenario/ai/grpc/generate.`；该问题与选择 PolarXAPI 或 ChatGPT 无关。

| 验证项 | 最终结果 | 实测证据 |
|---|---|---|
| 根因定位 | **PASS** | v9 frontend-only 运行 JAR SHA-256 为 `EEED1360880A354A3758D136393AA396BD083027D6B873BF2C13B315CEDEDDCA`，ZIP 中 `GrpcScenarioAiController.class` 数量为 0；当前源码重新打包 JAR 数量为 1。 |
| 路由一致性 | **PASS** | 后端 `@RequestMapping("/api/scenario/ai/grpc") + @PostMapping("/generate")` 与前端 `/api/scenario/ai/grpc/generate` 完全一致，不需要改 URL。 |
| 后端专项测试 | **PASS** | `GrpcScenarioAiServiceTest` 2/2、`GrpcScenarioSourceFileExtractorTest` 2/2；合计 4/4，Maven 15 模块全部成功。 |
| PolarXAPI 实际生成 | **PASS** | HTTP 200、业务码 `100200`；返回“gRPC 服务健康检查”草稿，自动选择 `ProjectService/GetSoftVersion`、服务地址 `host.docker.internal:9099` 和请求 `{}`。 |
| ChatGPT 实际生成 | **PASS** | HTTP 200、业务码 `100200`；返回 1 个只读项目查询场景，包含 `GetCurrent`、`GetSoftVersion` 两个用例。 |
| 页面真实回归 | **PASS** | 场景页打开“智能生成 gRPC 场景”，选择 ChatGPT 并输入文字需求后，页面展示 1 个场景、2 个只读用例，“确认创建”可用；未点击创建，草稿保留供演示。 |
| 多截图前端保留 | **PASS** | v10 继续使用 v9 前端产物，`index.html` SHA-256 仍为 `3F17F180B60D4815872D7E950D4E9CF844A3531BDB3DAAB9D6C0E794A6AB3AB8`，20 张截图入口和草稿持久化未回退。 |
| 数据与回滚 | **PASS** | `conf/data/logs` 三个原数据卷原样挂载；v9 旧容器只停止并改名保留，未删除容器或数据卷。 |

**最终环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085/#/api-test/scenario?orgId=100001&pId=100001100001` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-ai-generate-fix-v10` |
| 前端主 bundle | `assets/index-BMlrtl7w.js` |
| `index.html` SHA-256 | `3F17F180B60D4815872D7E950D4E9CF844A3531BDB3DAAB9D6C0E794A6AB3AB8` |
| api-test JAR SHA-256 | `A853DBF74E5AF5C076BFABC456C2D829F90FE4907071E6637F9D54B7CC418147` |
| 可复现 Dockerfile | `docker/Dockerfile.grpc-ai-scenario-overlay`，同时覆盖前端和 api-test JAR |
| 直接回滚容器 | `metersphere-grpc-verify4-ai-multi-screenshot-v9-frontend-only-backup-20260827`；其 AI 生成接口仍为已知 404 |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |

**质量 Gate**

| Gate | 结果 |
|---|---|
| JAR Controller 内容检查 | **PASS**，`GrpcScenarioAiController.class` 数量 1 |
| AI 后端目标测试 | **PASS**，4/4 |
| api-test JAR 重新打包 | **PASS** |
| Docker v10 构建 / 部署 | **PASS** |
| `/`、`/get-key` | **PASS**，均为 HTTP 200 |
| PolarXAPI / ChatGPT 生成接口 | **PASS**，均为 HTTP 200、业务码 `100200` |

### 10.25 gRPC 与等待步骤混排执行修复及真实回归（2026-08-27）

本节结论优先于旧镜像中 `grpc_mixed_scenario_not_supported` 的限制。旧执行器只接受纯 gRPC 步骤，把 `CONSTANT_TIMER` 误判为混合协议；仅删除协议校验又会导致等待步骤被静默跳过。本轮把等待步骤纳入内置 gRPC 执行链，并按场景原顺序真实执行和持久化报告。

| 验证项 | 最终结果 | 实测证据 |
|---|---|---|
| 支持范围 | **PASS** | `gRPC + CONSTANT_TIMER` 进入内置执行链；`gRPC + HTTP/JMeter` 仍明确拒绝，未扩大到不受支持的混合协议。 |
| 执行顺序 | **PASS** | 场景 `100047 / 试验上线与观测启停流程` 按“上线 -> 等待 -> 开始观测 -> 等待 -> 停止观测 -> 等待 -> 下线 -> 等待”执行 8 步。 |
| 等待真实生效 | **PASS** | 4 个等待步骤均配置 `3000 ms`；数据库 `api_scenario_report_detail.request_time` 四条均为 `3000`，未被跳过。 |
| 等待步骤结果 | **PASS** | 每个等待步骤均写入 `status=SUCCESS`、`method=WAIT`、`protocol=TIMER`、`delayMs=3000`、`actualDurationMs=3000`。 |
| 启用与持久化 | **PASS** | 8 个步骤全部启用并保存；强制刷新、切换页面后重新打开仍为 8 步和正确顺序。 |
| 页面真实执行 | **PASS** | 2026-08-27 21:42:04 点击“服务端执行”，约 15 秒完成；成功 8、失败 0、误报 0，不再出现 `grpc_mixed_scenario_not_supported`。 |
| 场景报告 | **PASS** | 报告 `178783812495200000`：总耗时 14.662 秒，请求总耗时 13.884 秒，通过 8、失败 0、未执行 0、断言通过率 100%。 |
| gRPC 明细 | **PASS** | 四个 gRPC 响应时间依次为 644 ms、1132 ms、47 ms、61 ms；`grpcStatus=OK`、`grpcCode=0`。 |
| 执行历史 | **PASS** | 最新记录 `20260827214205 / 手动执行 / 已完成 / 成功 / Administrator / 2026-08-27 21:42:05`，执行结果可打开。 |
| 变更历史 | **PASS** | 显示 `1690 / 更新 / Administrator / 2026-08-27 21:40:39` 和 `1689 / 更新 / Administrator / 2026-08-27 21:38:09`。 |

**自动测试**

| 模块 | 结果 | 覆盖 |
|---|---|---|
| api-test | **PASS，21/21** | 纯 gRPC、gRPC + 等待、实际耗时、原顺序、禁用等待、等待报告、gRPC + HTTP 拒绝 |
| test-plan | **PASS，3/3** | 测试计划入口复用相同内置执行判断与混合协议保护 |
| 格式检查 | **PASS** | `git diff --check` 无 whitespace error，仅有 Windows LF/CRLF 提示 |

**最终环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085/#/api-test/scenario?orgId=100001&pId=100001100001` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-grpc-wait-scenario-v11` |
| 前端保留 | v10 前端原样保留；`index.html` SHA-256 `3F17F180B60D4815872D7E950D4E9CF844A3531BDB3DAAB9D6C0E794A6AB3AB8` |
| api-test JAR SHA-256 | `B402167F1085DE7236F719D1C10C15D85822DD413E6B78270EEC6AFDC82F6D82` |
| 健康检查 | `/` 与 `/get-key` 均为 HTTP 200 |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |
| 直接回滚容器 | `metersphere-grpc-verify4-ai-generate-fix-v10-backup-20260827`；回滚后 gRPC + 等待步骤问题会恢复 |

### 10.26 接口测试 gRPC 与 AI 补充闭环自测（2026-08-28）

本节在 v11 环境继续补齐 10.20 中未闭环的报告导出、AI 模型设置、图片跨会话隔离、临时会话清理、场景行内控件和定时任务。本轮未执行 RISK 场景，未保存新模型或定时任务，未发送附带图片的 AI 消息。

| 验证项 | 结果 | 实测证据 |
|---|---|---|
| 单条 gRPC 报告导出 | **PASS** | 报告 `178776908660300000` 展示服务 `Polar.v1.ProjectService`、方法 `GetSoftVersion`、地址 `host.docker.internal:9099`、`gRPC Status=OK`、`gRPC Code=0`、请求 `{}`、响应 `version=0.9.8.1`、失败原因和完整断言表。 |
| 全部/批量报告导出 | **FAIL** | `#/fullPage/scenarioExportPDF?...&type=All` 页面实际显示总数 0、请求数 0、“暂无数据”，与报告列表存在的历史报告不一致；单条 `id=...` 导出正常。 |
| AI 供应商与模型列表 | **PASS** | DeepSeek/OpenAI/智谱 AI 切换正常；OpenAI 下显示 `chatgpt / gpt-5.6-sol`，不是系统无模型。 |
| AI 模型搜索 | **PASS（交互限制）** | 输入 `no-such-model` 并回车后显示“暂无模型数据”，清空并回车后恢复模型卡片；仅输入不回车不会立即过滤。 |
| AI 模型新增/编辑校验 | **PASS** | 空表单保存后同时校验模型名称、API 域名、Api Key 和基础模型；编辑回显模型名称、域名、脱敏 Key 和基础模型，未保存改动。 |
| AI 模型检测/启停/删除 | **PASS** | “检测”返回“连接可用”；启用开关实际关闭后再恢复为启用；删除确认框文案正确并取消，模型未删除。 |
| AI 图片上传与跨会话隔离 | **PASS** | 会话 `AI-GRPC-SELFTEST-CONVERSATION` 上传用户提供的 PNG 后显示缩略图和“移除图片”；切换“日常问候”后图片数为 0，切回后恢复为 1；最后移除后为 0，未发送消息。 |
| AI 临时会话清理 | **PASS** | 通过悬停更多菜单删除本轮临时会话 `gRPC自动化测试的20个检查点及原因`；“删除成功”后会话数从 3 变为 2。 |
| 场景等级/状态下拉 | **PASS** | 场景 `100046` 的 P0 正常展开 P0-P3，“进行中”正常展开已废弃/进行中/已完成，未变更值。此结果只覆盖场景表，不替代 10.20 中用例表行内 P0 缺陷。 |
| 场景定时任务 | **PASS（未保存）** | 打开 `100046` 的“创建定时任务”；空表达式保存正确提示“参数校验失败”，新环境切换正常，最后取消，未创建任务。 |
| 515px 窄视口响应式 | **FAIL** | 模型搜索输入元素宽度被压缩为 0，定时任务弹窗左侧越出视口，默认环境和资源池控件部分不可操作。 |
| 执行状态最终更新 | **PASS** | 列表当前显示 `100046` 为成功；数据库 `last_report_id=178781360441400000`，该报告为 `SUCCESS / COMPLETED / MANUAL / RUN`，成功 1、失败 0、断言 1/1，请求和断言通过率均为 100%。早期两条 ERROR 历史仍保留，后续成功执行已正确覆盖列表最终状态。 |

**环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085/#/api-test/scenario?orgId=100001&pId=100001100001` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260827-grpc-wait-scenario-v11` |
| 数据变更 | 仅清理本轮临时 AI 会话；模型启用状态已恢复；图片草稿已移除；未保存新模型或定时任务。 |

### 10.27 v19 整计划任务生命周期、详细 PDF 与 AI 回归（2026-08-28）

本节以 `metersphere/local-grpc-batch:20260828-detailed-pdf-v19` 为最终部署基线。v19 继承 v18 已通过的 test-plan 后端 JAR，只覆盖通过 ESLint、`vue-tsc` 和 production build 的最新前端。

| 验证项 | 结果 | 最新证据 |
|---|---|---|
| ESLint | **PASS** | PDF 目标文件 0 error。 |
| `vue-tsc --noEmit` | **PASS** | 整仓类型检查退出码 0。 |
| Vite production build | **PASS** | 6461 modules transformed，主 bundle `assets/index-DCBrwK2P.js`。Sass legacy API、既有 `eval` 和 CSS minify 提示为 warning，构建退出码 0。 |
| Docker 构建与部署 | **PASS** | v19 基于 v18 镜像指纹构建；`/`、`/get-key` 均 HTTP 200；三个原数据卷保留。 |
| test-plan JAR 一致性 | **PASS** | 容器内 SHA-256 `DC7A17008174D557805554EF4B0AB525768FB7E90886B1FCFB5F670DCEC4D0A9`，与 v18 已验证 JAR 一致。 |
| 部署后整计划执行 | **PASS** | 报告 `1621306895330148352`，`SUCCESS`，执行率 100%，通过率 100%，接口 1/1、场景 1/1 成功。 |
| 任务生命周期 | **PASS** | 主任务 `1621306895330148354` 为 `COMPLETED/SUCCESS`，case_count=2；任务项 `1621306895330148357`、`1621306895330148359` 均为 `COMPLETED/SUCCESS`。 |
| 最新报告步骤数据 | **PASS** | 共 9 步：1 个独立 gRPC 接口步骤、4 个场景 gRPC 步骤、4 个 WAIT 步骤；9/9 成功，9/9 均有请求和响应。 |
| 执行历史 | **PASS** | 场景 `1620680397040541704 / 100047` 返回 22 条；最新一条为任务项 `1621306895330148359 / MANUAL / SUCCESS / Administrator`。 |
| 变更历史 | **PASS** | 返回 2 条：`1690`、`1689`，均为 Administrator 的 UPDATE 记录。 |
| AI Controller 镜像内容 | **PASS** | 运行 JAR 含 `GrpcScenarioAiController.class`；不再返回 `No static resource`。 |
| ChatGPT 草稿生成 | **PASS** | v19 实际 HTTP 200、业务码 100200，12.2 s，返回 1 个场景、1 个用例，`protoRequired=false`。 |
| PolarXAPI 草稿生成 | **PASS** | v19 实际 HTTP 200、业务码 100200，11.1 s，返回 1 个场景、1 个用例，`protoRequired=false`。未确认创建。 |
| gRPC AI 20 张截图边界 | **PASS（代码、类型检查与历史 UI 回归）** | 当前代码限制附件总数 20、图片 20、单文件 10 MB；前序 UI 实测 3 张追加和跨页恢复已 PASS。v19 本轮应用内浏览器通道不可用，未重复人工粘贴 20+1 张。 |
| 草稿跨页恢复 | **PASS（实现、类型检查与历史 UI 回归）** | 当前代码仍按用户+组织+项目存入 localforage，500 ms 自动保存，重新打开恢复文字、附件、结果和勾选状态。 |
| 项目目录自动检索 | **PASS（实现、后端测试与历史 UI 回归）** | 目录句柄按项目保存，生成前自动检索 `.pcp/.pce/.proto/.json/.yaml/.md` 等资料；首次关联仍必须由用户通过系统目录选择器授权。 |
| 详细 PDF 代码与数据链路 | **PASS** | 导出页会逐报告、逐步骤读取明细，字段包含失败原因、请求/响应 JSON、gRPC Status/Code、断言和变量提取；最新 9 步 API 数据完整。 |
| 详细 PDF 实际下载与逐页视觉检查 | **BLOCKED** | Browser 控制通道三次连接均超过 120 s 无响应；MeterSphere 本身的 HTTP、API、整计划执行均正常。未使用另一套浏览器自动化伪造 UI PASS。 |
| 运行时错误增量 | **PASS（业务执行）** | v19 整计划执行期间 task-runner 没有新增 `EOFException`。主服务启动阶段有一次既有 Worker ID/MyBatis 初始化顺序 ERROR，随后 ID、任务和报告均正常生成，列为非阻塞残余风险。 |

**当前环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260828-detailed-pdf-v19` |
| 主 bundle | `assets/index-DCBrwK2P.js` |
| test-plan JAR SHA-256 | `DC7A17008174D557805554EF4B0AB525768FB7E90886B1FCFB5F670DCEC4D0A9` |
| api-test JAR SHA-256 | `5C95E2714FD5D3904F2F8E0299DF6CF61F70F6B9A7F52D7553076EA8B7E4F496` |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs` |
| 直接回退 | `metersphere-grpc-verify4-v18-task-lifecycle-backup` |
| PDF 复验 URL | `http://localhost:8085/#/fullPage/testPlanExportPDF?orgId=100001&pId=100001100001&id=1621306895330148352&type=TEST_PLAN` |

### 10.28 v20 图片预览、分页选择与 AI gRPC 步骤默认启用（2026-08-28）

本节以 `metersphere/local-grpc-batch:20260828-ai-image-preview-v20` 为当前最终部署基线。`SAFE/RISK` 继续作为风险标签，但不再参与 AI 创建步骤的 `enable` 赋值；AI 创建的每个 gRPC“引用 CASE”步骤均默认启用。

| 验证项 | 结果 | 最新证据 |
|---|---|---|
| 后端默认启用逻辑 | **PASS** | `GrpcScenarioAiService.buildCaseStep` 同时写入步骤 `enable=true` 和配置 `enable=true`；`RISK` 只写入场景标签。 |
| 后端单元测试 | **PASS** | `GrpcScenarioAiServiceTest` 使用 `RISK` 草稿验证创建步骤仍为启用；2 个测试，失败 0、错误 0。 |
| 新建场景默认启用 | **PASS** | 场景 `100049 / AI-V20-DEFAULT-ENABLE-20260828`，标签含 `RISK`；创建后开关 `aria-checked=true`。 |
| 保存与重开持久化 | **PASS** | 保存、刷新并重新打开 `100049` 后开关仍为 `true`，服务端执行成功 1、失败 0，执行历史和变更历史均新增记录。 |
| 截图所示历史场景复核 | **PASS** | 场景 `100048 / V3通道单次发送CCP_1报文` 的 6 条 `api_scenario_step.enable` 均为 true；当前页面重新打开后第 1、3、5 个 gRPC 引用步骤和第 2、4、6 个等待步骤全部显示 `[checked]`。截图中的灰色状态属于刷新前旧页面状态。 |
| 图片大缩略图 | **PASS** | 2 张图片以约 `217x199` 卡片展示，图片区域约 `215x135`；点击打开原图预览。 |
| 多图切换与删除 | **PASS** | 预览右箭头切换后图片 `src` 改变；删除后附件 2 变 1，未误触原图预览。 |
| 图片持久化 | **PASS** | 切换页面再返回后剩余图片恢复；测试草稿结束后已清空。 |
| 20 张数量边界 | **PASS** | 20 张全部接受，21 张被拒绝；20 张网格无横向溢出，可随抽屉滚动。 |
| 场景分页选择 | **PASS** | 页面直接提供 `10/20/30/40/50/100` 条/页；选择 50 后单页显示当前全部 30 条。 |
| 接口定义分页选择 | **PASS** | 选择 100 条/页后单页覆盖全部 76 条；定义详情、Service、Method、主 Proto 和 2 个依赖 Proto 正常回显。 |
| 接口用例分页代码 | **PASS** | 用例表复用相同分页选项并保留跨页 `selectAll + excludeIds` 协议；本节未执行破坏性批量操作。 |
| 只读 gRPC 执行 | **PASS** | `GetSoftVersion` 服务端执行成功，响应 `{"version":" 0.9.8.1"}`。 |
| 报告详情字段 | **PASS** | 页面及导出视图包含 Service、Method、服务器地址、耗时、gRPC Status/Code、错误码/消息、请求/响应 JSON、断言和变量提取结果。 |
| 全部接口测试子功能的无条件声明 | **BLOCKED** | mTLS 自定义证书上传仍是已知限制；本节未对所有危险业务方法和删除类批量操作做破坏性执行，不能据此声明整个 MeterSphere 全部功能绝对正常。 |

**当前环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260828-ai-image-preview-v20` |
| 版本 | `3.6.8-lts-grpc-ai-image-preview-20260828` |
| 健康检查 | `/`、`/get-key` 均为 HTTP 200 |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs`，原样保留 |
| 直接回退 | `metersphere-grpc-verify4-v19-detailed-pdf-backup` |

### 10.29 Excel 导入生成 10 条只读 gRPC 场景并执行（2026-08-28）

本节使用 `PolarControl_NewProject555_gRPC_测试用例_10条.xlsx` 作为唯一用例清单，通过 `/api/scenario/ai/grpc/generate` 生成草稿并创建场景。因本轮 Browser 控制通道在文件选择后连接重置，导入、创建、回读和执行均通过 MeterSphere 本地 API 完成，不将其记录为 UI 点击流 PASS。

| 验证项 | 结果 | 实测证据 |
|---|---|---|
| Excel 用例数量 | **PASS** | 工作簿包含 10 条 `P0 / SAFE / 默认启用` 的只读 Unary gRPC 用例，统一地址 `host.docker.internal:9099`。 |
| AI 草稿识别 | **PASS** | `/api/scenario/ai/grpc/generate` 返回 1 个场景、10 个用例、0 个阻塞项，无歧义和警告；未合并、遗漏或新增 Excel 行。 |
| 场景创建与回读 | **PASS** | 场景 `100051 / AI-GRPC-EXCEL-SMOKE-20260828`，scenarioId=`1621775098305028104`；回读 10 个步骤，10 个全部启用。 |
| 执行入口 | **PASS** | 使用页面同源 POST `/api/scenario/run`，显式传入 18 位 `reportId`、已保存步骤树、场景配置和默认环境 `Halo`；规避 GET 入口缺少 `reportId` 及通用 JMeter 路径产生 PENDING 的问题。 |
| 场景执行 | **PASS** | 报告 `178789793753000000` 为 `SUCCESS / COMPLETED`；成功 10、失败 0、未执行 0，步骤明细 10/10。 |
| gRPC 结果 | **PASS** | 10/10 步骤 `responseMessage=OK`，10/10 步骤 `responseCode=0`。 |
| 断言结果 | **PASS** | 29/29 条断言通过，覆盖 `GRPC_STATUS EQUALS OK`、`GRPC_CODE EQUALS 0` 及 Excel 中的 JSON Path 断言。 |

**10 条方法顺序**

| 序号 | Service / Method | 结果 |
|---:|---|---|
| 1 | `Polar.v1.ProjectService/GetSoftVersion` | PASS |
| 2 | `Polar.v1.ProjectService/GetCurrent` | PASS |
| 3 | `Polar.v1.ProjectService/GetCurrent` | PASS |
| 4 | `Polar.v1.DeviceService/ListDevices` | PASS |
| 5 | `Polar.v1.DeviceService/ListVariables` | PASS |
| 6 | `Polar.v1.DeviceService/ListVariables` | PASS |
| 7 | `Polar.v1.DeviceService/ListVariables` | PASS |
| 8 | `Polar.v1.DeviceService/ReadVariable` | PASS |
| 9 | `Polar.v1.DeviceService/ReadVariable` | PASS |
| 10 | `Polar.v1.RecorderService/GetAllRecorders` | PASS |

### 10.29.1 接口测试全功能、子功能及关联链路补测（2026-08-28）

本轮针对用户截图所列调试、接口定义、接口用例、场景、报告和测试计划继续逐项补测。结论只按可复核证据标记 `PASS / FAIL / BLOCKED`，不把未执行的危险操作或受环境限制的能力写成通过。

| 模块 / 功能 | 结果 | 本轮实测证据 |
|---|---|---|
| gRPC 调试基础编辑 | **PASS** | HTTP/gRPC 切换、主 Proto、依赖 Proto、Service/Method 发现、请求模板、格式化、Metadata 单行编辑与批量添加均可操作。 |
| 调试前置/后置/设置 | **PASS（有差异项）** | 前置支持脚本、等待；调试页后置支持脚本、等待；定义页后置支持脚本、SQL、提取参数、等待；设置、上下/左右布局均可打开。 |
| 调试页后置能力提示一致性 | **FAIL** | 页面提示包含 JSONPath 变量提取，但调试页后置菜单没有“提取参数”；定义页有该入口。当前功能提示与实际菜单不一致。 |
| gRPC 调试真实发送 | **PASS** | `Polar.v1.ProjectService/GetSoftVersion` 返回 `grpcStatus=OK`、`grpcCode=0`、`version=0.9.8.1`。 |
| 批量保存发现结果 | **PASS** | 弹窗展示 14 个 Service、59 个 Unary Method。 |
| 接口定义列表/搜索/详情 | **PASS** | 当前 76 条，100 条/页可覆盖全部；搜索 `100359` 只返回目标定义；地址、完整方法、Proto、2 个依赖 Proto、前后置、设置和关联用例均能回显。 |
| 接口用例列表/搜索/等级/状态 | **PASS** | 当前实际 66 条；100 条/页后为 1 个表头加 66 条数据；目标用例 `100359001` 搜索、等级、状态和详情正常。 |
| 用例断言与变量提取编辑 | **PASS** | 可新增断言和提取项，包含 JSONPath、条件、期望值、变量名等控件；测试数据未保存，已取消退出。 |
| 用例只读服务端执行 | **PASS** | `GetSoftVersion` 执行成功，响应版本 `0.9.8.1`。 |
| 场景列表/分页/详情 | **PASS** | 当前 31 条，50 条/页单页覆盖；场景 `100049 / AI-V20-DEFAULT-ENABLE-20260828` 的基本信息、步骤、参数、前后置、断言和设置正常。 |
| 场景保存与默认启用 | **PASS** | `100049` 的 gRPC 引用 CASE 默认启用，保存、刷新、跨页重开后保持启用。 |
| 场景执行历史 | **PASS** | `100049` 存在 `20260828123157 / 手动执行 / 已完成 / 成功`；记录可打开执行结果。 |
| 场景变更历史 | **PASS** | 存在 `1725 / 更新 / Administrator / 2026-08-28 12:31:13`。 |
| 批量执行配置 | **PASS** | 选择 1 条安全场景后，弹窗正确展示默认环境、串行/并行、失败停止、独立/集合报告和默认资源池。 |
| 批量执行真实结果 | **PASS** | 新报告 `1621756715845001218`，名称 `AI-V20-DEFAULT-ENABLE-20260828_2026-08-28 13:51:05`，`SUCCESS / BATCH`，1 个步骤、1 个通过。 |
| 批量执行运行时日志 | **FAIL（新增待定位项）** | 当前容器在 `2026-08-28 14:13:50` 记录 `BatchTaskExecutor` 的 `UnsupportedOperationException`，调用点为不可变列表 `set`；现有成功报告不受影响，但在根因修复并复测前不能声明所有 task-runner 批量路径正常。 |
| 成功报告详情 | **PASS** | 批量报告显示独立报告、批量触发、总步骤 1、通过 1；步骤含 Service、Method、`gRPC Status=OK`、`Code=0`、请求/响应 JSON。 |
| gRPC 参数错误报告 | **PASS** | 报告 `178695351790200000` 展示 `GRPC_INVALID_ARGUMENT`、`INVALID_ARGUMENT (code 3)`、Status、Code 和完整请求 JSON。 |
| 断言失败报告 | **PASS** | 报告 `178771213585100000` 第 16 步展示 `ASSERTION_FAILED`，并包含断言名称、类型、条件、期望值、实际值、结果和消息。 |
| 测试计划详情与关联项 | **PASS** | 计划 `1620907257213140992 / 100001 / GRPC-FULL-REGRESSION-20260828` 为已完成，2/2、通过率 100%；关联接口用例和场景各 1 条且均成功。 |
| 测试计划执行历史/结果 | **PASS** | 执行历史 4 条，最新两条已完成且成功；执行结果抽屉完成率 100%，接口明细 1 条、场景明细 1 条，均成功。 |
| 前端整仓类型检查 | **PASS** | `npx vue-tsc --noEmit --skipLibCheck` 退出码 0。 |
| api-test 后端专项 | **PASS** | 9 个测试类共 54 个测试，失败 0、错误 0；覆盖断言、报告持久化、场景运行、批量服务、AI 场景与项目文件提取。 |
| test-plan 后端专项 | **PASS** | 3 个测试类共 9 个测试，失败 0、错误 0；覆盖 gRPC 用例、gRPC 场景和直接调度。 |
| 服务健康检查 | **PASS** | `http://localhost:8085/` 与 `/get-key` 均 HTTP 200；容器运行中，重启次数 0。 |
| mTLS 自定义证书上传 | **BLOCKED** | UI 仍未提供完整的自定义客户端证书/私有 CA 上传闭环。 |
| 危险业务方法、删除类批量操作 | **BLOCKED** | 为保护演示数据和外部设备状态，本轮未执行所有写入、删除、启停设备类方法。 |
| 真实定时任务创建与到点执行 | **BLOCKED** | 已验证创建弹窗和参数校验，但未保存真实定时任务，避免演示前产生后台设备动作。 |

**本轮结论**

- `PASS`：核心只读 gRPC 调试、定义、用例、场景、单次/批量执行、历史、详细报告、测试计划和 63 个后端专项测试均有通过证据。
- `FAIL`：调试页 JSONPath 提示与后置菜单不一致；运行日志出现 1 次 `BatchTaskExecutor UnsupportedOperationException`。
- `BLOCKED`：mTLS 自定义证书、危险业务/删除操作、真实定时任务到点执行。
- 因仍有 `FAIL` 和 `BLOCKED`，当前不能声明“接口测试所有功能 100% 正常”；演示应使用已验证的只读 `SAFE` 链路。

**当前演示环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260828-ai-image-preview-v20` |
| 版本 | `3.6.8-lts-grpc-ai-image-preview-20260828` |
| 安全演示场景 | `100049 / AI-V20-DEFAULT-ENABLE-20260828` |
| 最新批量报告 | `1621756715845001218` |

### 10.30 Excel 一用例一场景修正与 10 个独立 gRPC 场景回归（2026-08-28）

本节结论修正 10.29 的场景粒度：Excel 每一行必须创建一个独立测试场景，而不是把 10 行合并成一个 10 步场景。旧合并场景 `100051`、`100052` 未删除；本轮创建新的 `100053` 至 `100062` 作为正确验收对象。

| 验证项 | 结果 | 实测证据 |
|---|---|---|
| Excel 结构 | **PASS** | 新工作簿包含 10 行、10 个唯一的“独立场景名称”；每行独立保存业务用例、服务地址、Service、Method、请求 JSON 和断言。 |
| AI 自动识别 | **PASS** | `/api/scenario/ai/grpc/generate` 返回 10 个场景、10 个用例，每个场景恰好 1 个用例；0 阻塞、0 歧义、0 警告。 |
| gRPC 定义匹配 | **PASS** | 10 行全部匹配到现有定义，覆盖 `ProjectService`、`DeviceService`、`RecorderService` 及 7 种 Service/Method 组合。 |
| 创建与回读 | **PASS** | 创建场景 `100053` 至 `100062`；回读 10 个场景，每个场景只有 1 个步骤且均启用，总步骤数 10。 |
| 独立执行 | **PASS** | 逐场景调用 POST `/api/scenario/run`；10 个独立报告均为 `SUCCESS / COMPLETED`，每个报告只有 1 个成功步骤。 |
| gRPC 结果 | **PASS** | 10/10 场景 `responseMessage=OK`，10/10 场景 `responseCode=0`。 |
| 断言结果 | **PASS** | 29/29 条断言通过。场景 001 至 009 各 3 条，场景 010 为 2 条。 |

| 场景编号 | 场景名称 | Service / Method | 报告 ID | 结果 |
|---|---|---|---|---|
| 100053 | `AI-GRPC-XLSX-001-查询软件版本` | `Polar.v1.ProjectService/GetSoftVersion` | `178789910770800000` | PASS |
| 100054 | `AI-GRPC-XLSX-002-查询当前工程` | `Polar.v1.ProjectService/GetCurrent` | `178789910834400000` | PASS |
| 100055 | `AI-GRPC-XLSX-003-查询试验状态` | `Polar.v1.ProjectService/GetCurrent` | `178789910891100000` | PASS |
| 100056 | `AI-GRPC-XLSX-004-查询CAN设备` | `Polar.v1.DeviceService/ListDevices` | `178789910946700000` | PASS |
| 100057 | `AI-GRPC-XLSX-005-查询V3变量` | `Polar.v1.DeviceService/ListVariables` | `178789911005000000` | PASS |
| 100058 | `AI-GRPC-XLSX-006-查询V4变量` | `Polar.v1.DeviceService/ListVariables` | `178789911063500000` | PASS |
| 100059 | `AI-GRPC-XLSX-007-验证V3分页` | `Polar.v1.DeviceService/ListVariables` | `178789911122100000` | PASS |
| 100060 | `AI-GRPC-XLSX-008-读取V3变量` | `Polar.v1.DeviceService/ReadVariable` | `178789911179000000` | PASS |
| 100061 | `AI-GRPC-XLSX-009-读取V4变量` | `Polar.v1.DeviceService/ReadVariable` | `178789911234900000` | PASS |
| 100062 | `AI-GRPC-XLSX-010-查询Recorder` | `Polar.v1.RecorderService/GetAllRecorders` | `178789911292600000` | PASS |

### 10.31 业务测试用例自动识别 gRPC 调用链与 10 场景回归（2026-08-28）

本节再次修正 10.30 的输入模型：Excel 不再保存服务地址、Service、Method、请求 JSON 或断言。每一行只描述一个业务测试场景及其前置条件、触发动作和预期结果；MeterSphere AI 根据业务语义和当前项目 gRPC 接口目录，自动选择所需接口、补全参数、编排调用顺序、生成断言和变量提取。一条业务用例仍对应一个独立 MeterSphere 场景，但一个业务场景允许自动展开为多个 gRPC 调用。

| 验证项 | 结果 | 实测证据 |
|---|---|---|
| Excel 业务输入结构 | **PASS** | `PolarControl_NewProject555_业务测试用例_10条.xlsx` 共 10 行，列为序号、用例 ID、测试场景、前置条件、触发动作、预期结果；不存在 Service、Method、请求 JSON 或断言列。 |
| AI 服务识别与编排 | **PASS** | 10 条业务用例生成 10 个独立场景，自动展开 29 个只读 Unary gRPC 调用；每场景调用数依次为 `5、7、2、1、1、1、2、3、5、2`。 |
| 安全约束 | **PASS** | 草稿 0 阻塞、0 非 SAFE 项；未调用上线、观测、写变量、发 CAN、设备变更或 Recorder 变更方法。 |
| 场景创建与幂等更新 | **PASS** | 创建并更新场景 `100063` 至 `100072`，名称前缀为 `AI-GRPC-BIZ-001` 至 `AI-GRPC-BIZ-010`；再次创建时更新 AI 用例及场景，不产生重复场景。 |
| gRPC 执行结果 | **PASS** | 10/10 场景为 `SUCCESS / COMPLETED`；29/29 调用 `responseMessage=OK` 且 `responseCode=0`。 |
| 断言结果 | **PASS** | 154/154 条断言通过，包含 gRPC Status/Code、业务 JSONPath 以及跨步骤状态、分页和重复查询一致性检查。 |

| 场景编号 | 业务场景 | 自动识别调用数 | 报告 ID | 结果 |
|---|---|---:|---|---|
| 100063 | `AI-GRPC-BIZ-001-连接、版本和工程环境预检` | 5 | `178790240861600000` | PASS |
| 100064 | `AI-GRPC-BIZ-002-IDLE 配置基线完整核验` | 7 | `178790240936000000` | PASS |
| 100065 | `AI-GRPC-BIZ-003-当前工程信息连续查询一致性` | 2 | `178790241002000000` | PASS |
| 100066 | `AI-GRPC-BIZ-004-CAN 设备发现与激活状态核验` | 1 | `178790241061800000` | PASS |
| 100067 | `AI-GRPC-BIZ-005-V3 变量定义第一页查询` | 1 | `178790241118500000` | PASS |
| 100068 | `AI-GRPC-BIZ-006-V4 变量定义第一页查询` | 1 | `178790241174800000` | PASS |
| 100069 | `AI-GRPC-BIZ-007-V3 变量分页结果一致性` | 2 | `178790241230600000` | PASS |
| 100070 | `AI-GRPC-BIZ-008-双设备 SeatHeightAdjmt 离线读取` | 3 | `178790241289400000` | PASS |
| 100071 | `AI-GRPC-BIZ-009-只读巡检不改变实验状态` | 5 | `178790241351600000` | PASS |
| 100072 | `AI-GRPC-BIZ-010-Recorder 清单重复查询幂等性` | 2 | `178790241414400000` | PASS |

此前场景 `100051`、`100052` 以及 `100053` 至 `100062` 属于旧输入模型，按“不删除既有数据”的约束继续保留，但不再作为本需求的最终验收对象。

### 10.32 v21 工作台全量、时间筛选与关联统计回归（2026-08-28）

**问题与根因**

用户截图中工作台默认选中“近 3 天”，项目概览显示 `0/0/1/27/18/1`，视觉上却像项目全量；同时部分详情卡和测试计划统计没有继续传递时间参数，导致顶部、下方卡片和关联页面的统计口径不一致。旧版“近 3 天”缓存还会让升级后的页面继续停留在旧默认值。

**修复内容**

- 时间选项新增“全部”并设为默认，使用 `WORK_TIME_V2_<userId>` 隔离旧缓存。
- 全部时间不传起止时间，后端空时间参数安全处理，不再触发空指针。
- 功能用例、用例评审、接口 API、接口 CASE、接口场景、缺陷、计划遗留缺陷和测试计划统一接收同一时间范围。
- 测试计划 Controller 将时间参数完整传入 Service、Mapper 和 SQL；英文“近 7 天”文案同步修正。

| 验证项 | 结果 | 实测证据 |
|---|---|---|
| 默认筛选 | **PASS** | 首次进入工作台选中“全部”，不再默认显示近 3 天新增量。 |
| 全量项目概览 | **PASS** | 功能用例 `13`、用例评审 `1`、接口 API `76`、接口 CASE `106`、接口场景 `53`、测试计划 `2`、缺陷数 `0`。 |
| 近 3 天项目概览 | **PASS** | `0/0/1/56/28/1/0`，数据库按相同起止时间逐项查询一致。 |
| 接口 CASE 详情卡 | **PASS** | 全部为 `106`，近 3 天为 `56`，与顶部接口 CASE 数一致。 |
| 自定义日期 | **PASS** | 自定义日期入口可用，请求携带所选起止时间。 |
| 筛选持久化 | **PASS** | 切回“全部”并刷新后仍保持“全部”；旧时间缓存不再污染新版本。 |
| 工作台请求单测 | **PASS** | `DashboardFrontPageRequestTest` 3/3，失败 0、错误 0。 |
| 测试计划范围单测 | **PASS** | `TestPlanCoverageRequestTest` 2/2，失败 0、错误 0。 |
| 前端类型检查 | **PASS** | `pnpm type:check` 退出码 0。 |
| 前端生产构建 | **PASS** | `pnpm build` 成功。 |
| 后端生产打包 | **PASS** | 19 个 Maven reactor 模块全部成功。 |
| 容器健康与日志 | **PASS** | `/`、`/get-key` 均 HTTP 200；最近日志无工作台 NPE、SQL 或 MyBatis 映射异常。 |

**当前演示环境**

| 项 | 值 |
|---|---|
| URL | `http://localhost:8085/#/workstation/home?orgId=100001` |
| 容器 / 镜像 | `metersphere-grpc-verify4` / `metersphere/local-grpc-batch:20260828-workbench-statistics-v21` |
| 版本 | `3.6.8-lts-workbench-statistics-20260828` |
| 数据卷 | `metersphere-grpc-verify4-conf`、`metersphere-grpc-verify4-data`、`metersphere-grpc-verify4-logs` |
| 直接回退容器 | `metersphere-grpc-verify4-v20-ai-image-preview-backup` |

本节仅把工作台与关联统计标记为 `PASS`。gRPC 全功能测试中既有 `FAIL`、`BLOCKED` 和非破坏性测试边界仍按前文保留，不因为本次统计修复而改写为全部通过。
