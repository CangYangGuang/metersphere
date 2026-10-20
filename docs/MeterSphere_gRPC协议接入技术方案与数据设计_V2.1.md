V2.1 修订版｜协议扩展、数据模型、接口契约、执行器、AI 与安全

| **文档状态** | 开发前技术评审版，Cursor 阶段 0 后需补齐真实路径                                                  |
|--------------|---------------------------------------------------------------------------------------------------|
| **适用范围** | MeterSphere 社区版二开，gRPC 作为接口测试协议类型接入                                             |
| **技术主线** | Descriptor + DynamicMessage + JsonFormat + ClientCalls.blockingUnaryCall                          |
| **关键修订** | 统一 requestJson/responseJson 为 String；补充执行模型、AI 复用、Reflection 规划、错误码、安全策略 |
| **前置条件** | 完成代码地图、确认 MeterSphere 版本、数据库类型、执行器路径、AI 服务接口                          |

# 1. 总体架构

前端：接口测试现有页面扩展

├─ 协议类型 HTTP / GRPC

├─ gRPC 接口定义面板

├─ gRPC 调试面板

├─ gRPC 用例与 AI 生成适配

├─ 场景步骤 GRPC_REQUEST

└─ 报告详情 gRPC 展示分支

后端：协议扩展与执行器

├─ ProtocolType: HTTP / GRPC

├─ GrpcProtoService // proto 上传、编译、解析、版本

├─ GrpcReflectionService // V1.1 反射拉取 Descriptor

├─ GrpcInvokeService // 单次 Unary 调用

├─ GrpcCaseService // gRPC 用例保存与执行

├─ GrpcScenarioExecutor // 场景步骤执行器

├─ GrpcAiCaseContextBuilder// 复用现有 AI 生成用例能力

└─ GrpcReportAdapter // 报告结果适配

# 2. 代码路径基线

| **必须执行：**本表不得凭空填写。Cursor 阶段 0 必须扫描当前仓库，补齐真实路径、关键类、DTO、Mapper、路由、组件、执行器和报告模块后，才允许进入代码修改。 |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|

| **模块**           | **待确认真实路径** | **Cursor 阶段 0 输出要求**                     |
|--------------------|--------------------|------------------------------------------------|
| 接口定义列表/详情  | 待填               | 前端组件路径、路由、接口 API 文件              |
| 接口用例与 AI 生成 | 待填               | 现有 AI 生成入口、同步用例接口、用例 DTO       |
| 调试执行           | 待填               | 服务端执行、本地执行入口、task-runner 调用方式 |
| 场景编辑器         | 待填               | 步骤模型、步骤配置组件、执行器分发逻辑         |
| 报告详情           | 待填               | 报告结果 DTO、步骤详情组件                     |
| 权限               | 待填               | 接口调试/执行/查看权限枚举                     |
| 数据库迁移         | 待填               | 迁移脚本目录、命名规范、回滚方式               |

# 3. 协议抽象设计

enum ProtocolType {

HTTP,

GRPC

}

enum ScenarioStepType {

HTTP_REQUEST,

GRPC_REQUEST,

SCRIPT,

CONTROLLER,

WAIT,

// 保留现有类型

}

interface ApiRequestExecutor {

ExecuteResult execute(ApiExecutionContext context);

}

HttpRequestExecutor implements ApiRequestExecutor

GrpcRequestExecutor implements ApiRequestExecutor

- HTTP 原执行链路不得重写，只允许增加协议分发。

- 所有 protocol 为空的历史数据按 HTTP 兼容处理。

- gRPC 执行结果统一转换为平台通用 ExecuteResult，并扩展 grpcStatus、serviceName、methodName 等字段。

# 4. 数据设计

| **设计原则：**下列表为逻辑模型。实际表名、字段类型、租户字段、软删除字段、索引和迁移脚本必须结合当前 MeterSphere 代码基线确认。 |
|---------------------------------------------------------------------------------------------------------------------------------|

| **逻辑表/对象**      | **关键字段**                                                                                                                   | **说明**                                                   |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| api_definition       | id, project_id, module_id, name, protocol, status, create_user, update_user, create_time, update_time, deleted                 | 接口定义主表或等价对象；新增 protocol。                    |
| api_grpc_definition  | id, api_id, proto_id, package_name, service_name, method_name, method_type, request_message, response_message, descriptor_hash | gRPC 接口扩展信息。                                        |
| api_grpc_proto       | id, project_id, file_name, content, descriptor_content, version, source_type, descriptor_hash, create_user, create_time        | proto/descriptor 快照管理；source_type=UPLOAD/REFLECTION。 |
| api_case             | id, api_id, protocol, name, priority, tags, enabled, create_user, update_time                                                  | 用例主表或现有用例对象扩展。                               |
| api_grpc_case_config | case_id, server_address, metadata_json, request_json, timeout, assertions_json, extractors_json                                | gRPC 用例配置。                                            |
| scenario_step        | id, scenario_id, step_type, ref_id, config_json, sort, enabled                                                                 | 场景步骤；GRPC_REQUEST 使用 config_json 保存 gRPC 配置。   |
| api_report_step      | id, report_id, step_id, protocol, status, duration, request_summary, response_summary, error_code, error_message               | 报告步骤结果；gRPC 扩展内容保存在 detail_json。            |

## 4.1 迁移策略

- 为接口定义、用例、报告步骤增加 protocol 字段时，历史数据默认填充 HTTP。

- 新增 gRPC 扩展表时必须包含 project_id 或从主表关联得到项目范围，满足多租户隔离。

- 所有迁移脚本必须提供回滚方案或降级说明。

- 删除接口前检查用例、场景、测试计划引用，阻止直接物理删除。

# 5. 前后端接口契约

## 5.1 字段类型统一

| **字段**     | **类型**                     | **说明**                                            |
|--------------|------------------------------|-----------------------------------------------------|
| requestJson  | String                       | JSON 编辑器内容，传输时为转义后的 JSON 字符串。     |
| responseJson | String                       | 后端 JsonFormat.printer 输出的 JSON 字符串。        |
| metadata     | Array\<{key,value,enabled}\> | 普通 ASCII Metadata；敏感 key 展示脱敏。            |
| timeout      | Integer                      | 单位 ms，默认 10000，范围 1000~60000。              |
| protoId      | String                       | 已保存 proto/descriptor 快照 ID。                   |
| protoContent | String                       | 仅调试临时模式可传；正式接口/用例优先使用 protoId。 |

## 5.2 合法请求示例

POST /api/grpc/debug/send

{

"serverAddress": "\${grpc_host}:50051",

"protoId": "proto-001",

"serviceName": "com.demo.UserService",

"methodName": "GetUser",

"metadata": \[

{"key": "authorization", "value": "Bearer \${token}", "enabled": true}

\],

"requestJson": "{"userId":"10001"}",

"timeout": 10000,

"executeMode": "SERVER"

}

## 5.3 合法响应示例

{

"success": true,

"data": {

"grpcStatus": "OK",

"grpcCode": 0,

"duration": 35,

"responseJson": "{"userId":"10001","userName":"test-user"}",

"assertionResults": \[\],

"extractorResults": \[\]

}

}

# 6. proto 编译、解析与反射

| **能力**                 | **V1.0 策略**                                                  | **后续策略**                        |
|--------------------------|----------------------------------------------------------------|-------------------------------------|
| 单 proto 上传            | 支持                                                           | 继续支持                            |
| 业务 import              | 暂不支持，提示 GRPC_IMPORT_NOT_SUPPORTED                       | V1.1 支持多文件/目录                |
| google/protobuf 常见类型 | V1.0 明确不承诺；可视 Demo 结果决定 Empty/Timestamp 是否白名单 | V1.1 支持 WKT                       |
| Server Reflection        | 不进入 V1.0-A；可作为 V1.1                                     | 支持 Metadata、保存 Descriptor 快照 |
| proto 版本               | V1.0 保存当前快照                                              | V1.1 支持版本历史和 Diff            |

.proto 源码 / Reflection Descriptor

↓

FileDescriptorSet

↓

Descriptors.FileDescriptor

↓

ServiceDescriptor / MethodDescriptor

↓

Request Descriptor / Response Descriptor

↓

DynamicMessage + JsonFormat

↓

blockingUnaryCall

# 7. gRPC 调用执行链路

执行前：

1\. 校验权限、serverAddress、protoId、serviceName、methodName、requestJson、timeout。

2\. 变量替换：环境变量 → 场景变量 → 前置脚本变量。

3\. 根据 protoId 获取 Descriptor 快照。

4\. 使用 JsonFormat.parser() 将 requestJson 合并为 DynamicMessage。

5\. 将 Metadata 注入 ClientInterceptor 或 MetadataUtils。

6\. 构建 MethodDescriptor\<DynamicMessage, DynamicMessage\>。

7\. 创建 ManagedChannel，设置 deadline。

8\. ClientCalls.blockingUnaryCall() 执行 Unary 请求。

9\. JsonFormat.printer() 将响应转换为 responseJson。

10\. 执行断言、变量提取，写入执行结果和报告。

- Channel 每次调试可创建并关闭；场景批量执行可按 host 复用，但必须避免泄漏。

- 执行完成必须 shutdown，并设置 awaitTermination 超时。

- 本地执行时由 task-runner 或等价执行进程发起 gRPC 连接；服务端执行时由后端服务发起连接。

# 8. AI 生成用例协议适配

| **核心原则：**不新增 gRPC 专属 AI 模块，不新增模型配置。复用现有 AI 生成用例、设置、预览、同步用例流程。 |
|----------------------------------------------------------------------------------------------------------|

GrpcAiCaseContextBuilder 输入：

\- protocol = GRPC

\- packageName / serviceName / methodName / methodType

\- requestMessage 字段树

\- responseMessage 字段树

\- enum / repeated / nested message

\- metadataTemplate

\- 用户自然语言生成要求

输出给现有 AI 服务：结构化上下文 + 用户提示词

AI 返回后：

\- proto 字段合法性校验

\- JSON 类型校验

\- enum 值校验

\- repeated 数组校验

\- 生成 riskTips

\- 用户预览确认后同步为 gRPC 接口用例

| **校验项**    | **不通过处理**                        |
|---------------|---------------------------------------|
| 字段不存在    | 标记 invalid，提示字段不在 proto 中。 |
| 类型错误      | 标记 invalid，提示字段类型不匹配。    |
| 枚举值非法    | 标记 invalid，提示可选枚举值。        |
| 缺少业务期望  | 生成 riskTips，提醒用户人工确认。     |
| 敏感 Metadata | 传给 AI 前脱敏或占位。                |

# 9. 断言、变量提取与报告

- 响应体统一按 JSON 字符串处理，字段断言和变量提取优先复用现有 HTTP JSONPath 机制。

- gRPC 新增专有断言：grpcStatus 等于/不等于，grpcCode 等于/不等于。

- 响应时间断言复用现有耗时断言。

- 报告 detail_json 中保存 gRPC 专属信息：Service、Method、Metadata、grpcStatus、requestJson、responseJson、assertionResults、extractorResults。

# 10. 错误码与状态映射

| **错误码**                | **场景**              | **提示文案**                            |
|---------------------------|-----------------------|-----------------------------------------|
| GRPC_ADDRESS_INVALID      | 服务地址格式错误      | 请输入 host:port 格式的 gRPC 服务地址。 |
| GRPC_PROTO_COMPILE_FAILED | proto 编译失败        | proto 文件编译失败，请检查语法。        |
| GRPC_IMPORT_NOT_SUPPORTED | 存在 import 依赖      | 当前版本暂不支持该 proto import 依赖。  |
| GRPC_SERVICE_NOT_FOUND    | Service 不存在        | 未在 Descriptor 中找到指定 Service。    |
| GRPC_METHOD_NOT_FOUND     | Method 不存在         | 未在 Service 中找到指定 Method。        |
| GRPC_STREAM_NOT_SUPPORTED | Streaming Method      | 当前版本暂不支持流式调用。              |
| GRPC_JSON_CONVERT_FAILED  | JSON 转 Protobuf 失败 | 请求参数转换失败，请检查字段和类型。    |
| GRPC_CONNECT_FAILED       | 连接失败/UNAVAILABLE  | gRPC 服务连接失败或服务不可用。         |
| GRPC_TIMEOUT              | DEADLINE_EXCEEDED     | gRPC 调用超时。                         |
| GRPC_PERMISSION_DENIED    | PERMISSION_DENIED     | gRPC 服务端返回权限不足。               |
| GRPC_UNAUTHENTICATED      | UNAUTHENTICATED       | gRPC 服务端返回认证失败。               |
| GRPC_CALL_FAILED          | 其他异常              | gRPC 服务调用失败。                     |

# 11. 安全与兼容性

| **风险**       | **默认控制策略**                                                          |
|----------------|---------------------------------------------------------------------------|
| SSRF/内网探测  | 支持部署级白名单/黑名单；记录调用审计；限制并发和 timeout。               |
| 访问敏感地址   | 默认建议屏蔽 169.254.169.254 等云元数据地址；是否屏蔽内网由部署方配置。   |
| proto 文件风险 | 仅允许 .proto；大小默认 5MB；临时文件随机目录；禁止使用用户文件名拼路径。 |
| 敏感日志       | 请求/响应默认不完整入日志；Metadata 敏感 key 脱敏。                       |
| 大响应         | 默认展示最大 5MB，超过截断并提示。                                        |
| HTTP 回归      | 所有新增协议逻辑必须保证 HTTP 旧数据、旧用例、旧场景、旧报告不受影响。    |

# 12. 参考依据

- MeterSphere V3 开发环境：后端 Java/Spring Boot/Maven、JDK 21 等，以实际仓库为准。

- MeterSphere V3 接口测试：调试、定义、场景、报告均为现有主链路。

- MeterSphere V3 AI：已有 AI 生成用例、模型设置、同步用例流程，应复用。
