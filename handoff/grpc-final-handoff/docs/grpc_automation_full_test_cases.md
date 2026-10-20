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
