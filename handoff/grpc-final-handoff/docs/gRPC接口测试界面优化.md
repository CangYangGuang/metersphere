# MeterSphere gRPC 调试界面 UI / 交互优化方案

## 1. 背景

当前 gRPC 接口调试界面已经具备完整功能，包括：

* 服务地址输入
* Proto 上传 / 粘贴 / 解析
* 多 proto bundle
* Reflection 发现服务
* Service / Method 选择
* Request JSON 编辑
* Metadata 配置
* Timeout 配置
* 服务端执行
* 响应展示
* 保存请求

但当前页面存在明显的信息重复和布局拥挤问题。

经过参考 Postman gRPC 调试界面和 MeterSphere HTTP 接口调试界面后，本次优化不改变外部页面框架，只调整 gRPC 请求主体区域。

---

## 2. 总体设计原则

### 2.1 保持 MeterSphere 外层结构不变

以下内容不做大改：

```text
左侧系统导航
左侧接口树 / 请求列表
顶部请求 Tab
顶部请求栏
服务端执行按钮
保存按钮
整体 MeterSphere 紫色主题
```

### 2.2 只优化 gRPC 主体编辑区

重点优化区域：

```text
Message
Metadata
Service definition
Settings
Response
```

### 2.3 避免重复展示

核心原则：

```text
顶部请求栏 = 当前调试目标
Service definition = 服务定义来源与方法清单
Message = 请求消息编辑
Response = 响应结果展示
```

也就是说：

* 顶部请求栏已经显示服务地址和当前 Method；
* Message 页面不再重复显示 Service / Method / Method 类型；
* Service definition 页面不再以“当前选中方法详情”为主，而是负责管理服务定义来源和方法清单；
* Service definition 中可以展示方法列表，但不再重复做一排当前 Service / Method 摘要栏。

---

## 3. 优化后的整体结构

页面整体结构如下：

```text
┌──────────────────────────────────────────────────────────────┐
│ MeterSphere 外层布局                                          │
│                                                              │
│ 左侧导航 + 请求树 + 顶部请求 Tab 保持不变                      │
│                                                              │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ 顶部请求栏                                                 │ │
│ │ [GRPC] [服务地址] [Method 下拉] [服务端执行] [保存]         │ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                              │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ 请求配置 Tabs                                              │ │
│ │ Message | Metadata | Service definition | Settings         │ │
│ ├──────────────────────────────────────────────────────────┤ │
│ │ 当前 Tab 内容                                               │ │
│ ├──────────────────────────────────────────────────────────┤ │
│ │ Response 响应结果区                                         │ │
│ └──────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. 顶部请求栏职责

### 4.1 顶部请求栏保留内容

顶部请求栏负责当前请求的核心调试目标。

建议结构：

```text
[ GRPC ▼ ] [ 服务地址 / Server Address __________________ ] [ Method 下拉 __________________ ] [服务端执行] [保存 ▼]
```

### 4.2 顶部请求栏字段说明

| 字段        | 说明                    |
| --------- | --------------------- |
| 协议类型      | 固定显示 GRPC，保留协议切换能力    |
| 服务地址      | 当前请求执行时使用的 gRPC 服务地址  |
| Method 下拉 | 当前请求执行时使用的 RPC Method |
| 服务端执行     | 主操作按钮                 |
| 保存        | 保存当前请求                |
| 更多操作      | 后续可放另存为、复制、导出等        |

### 4.3 Method 下拉

Method 下拉建议参考 Postman，按 Service 分组展示：

```text
Select a method

hello.HelloService
  SayHello        UNARY
  SayHelloAgain   UNARY

phase4.main.Phase4BundleService
  CreateUser      UNARY
```

要求：

1. 支持搜索 Service。
2. 支持搜索 Method。
3. Method 按 Service 分组。
4. Method 类型显示在下拉项右侧。
5. 选择 Method 后自动同步：

   * serviceName
   * methodName
   * methodType
   * requestJsonTemplate
6. 如果 Request JSON 非空，不允许静默覆盖。

### 4.4 顶部请求栏不做的事情

顶部请求栏不展示：

```text
Proto 文件列表
Reflection 配置
protoVersion 详情
protoChangeImpact 详情
descriptorContent
metadata 表格
timeout 详细设置
```

这些都归位到下方对应 Tab。

---

## 5. Message Tab 设计

### 5.1 职责

Message Tab 只负责请求消息编辑。

不再展示：

```text
Proto 文件
Service
Method
Method 类型
Reflection 入口
Proto 版本
变更影响
```

这些信息会让 Message 页面变拥挤，而且与顶部请求栏或 Service definition 重复。

### 5.2 Message Tab 结构

```text
Message | Metadata | Service definition | Settings

请求消息（JSON）                         [生成模板] [重置模板] [格式化] [...]

┌────────────────────────────────────────────────────────────┐
│ 1  {                                                       │
│ 2    "greeting": "hello MeterSphere"                       │
│ 3  }                                                       │
└────────────────────────────────────────────────────────────┘

响应结果   成功   grpcStatus: OK   grpcCode: 0   耗时: 268ms   大小: 38 B

响应体 | Metadata | 错误详情 | 原始信息

┌────────────────────────────────────────────────────────────┐
│ 1  {                                                       │
│ 2    "reply": "hello MeterSphere"                          │
│ 3  }                                                       │
└────────────────────────────────────────────────────────────┘
```

### 5.3 Request JSON 工具栏

工具栏建议放在请求 JSON 标题右侧：

```text
[生成模板] [重置模板] [格式化] [...]
```

按钮说明：

| 按钮   | 说明                                     |
| ---- | -------------------------------------- |
| 生成模板 | 根据当前 Method 的 requestJsonTemplate 生成请求 |
| 重置模板 | 强制用模板覆盖当前请求 JSON，需要二次确认                |
| 格式化  | 格式化当前 JSON                             |
| 更多   | 校验 JSON、复制、清空等扩展操作                     |

### 5.4 非空 body 保护

交互规则：

1. 当 Request JSON 为空时，选择 Method 后可自动填充模板。
2. 当 Request JSON 非空时，切换 Method 不静默覆盖。
3. 点击“生成模板”时：

   * 如果为空，直接生成；
   * 如果非空，弹窗确认。
4. 点击“重置模板”时，必须弹窗确认。

确认文案：

```text
当前请求 JSON 已有内容，是否使用模板覆盖？
```

### 5.5 Message Tab 空状态

如果未选择 Method：

```text
请先在顶部选择 Method，或在 Service definition 中导入 Proto / 发现服务
```

如果已选择 Method 但请求为空：

```text
暂无请求消息，可点击“生成模板”
```

---

## 6. Metadata Tab 设计

### 6.1 职责

Metadata Tab 负责 gRPC Metadata 配置。

它对应 HTTP 调试中的 Header，因此应该尽量复用 HTTP 请求头表格交互。

### 6.2 Metadata Tab 结构

```text
Metadata

仅 enabled=true 的 Metadata 项会参与发送

┌──────┬────────────┬────────────┬────────────┬────────┐
│ 启用 │ 参数名称   │ 参数值     │ 描述       │ 操作   │
├──────┼────────────┼────────────┼────────────┼────────┤
│ ☑    │ token      │ xxx        │ 鉴权 token │ 删除   │
└──────┴────────────┴────────────┴────────────┴────────┘

[添加一行] [批量添加]
```

### 6.3 Metadata 交互要求

1. 支持新增行。
2. 支持删除行。
3. 支持启用 / 禁用。
4. 支持批量添加。
5. 支持空状态。
6. 执行时只发送 enabled=true 的 metadata。
7. 不在 Message Tab 常驻显示 Metadata。

---

## 7. Service definition Tab 设计

### 7.1 核心职责

Service definition Tab 负责：

```text
服务定义来源管理
Proto 文件 / bundle 管理
Reflection 发现
方法清单展示
Proto 版本展示
变更影响展示
```

它不再负责重复展示当前顶部请求栏已经展示的“当前 Method 摘要”。

### 7.2 Service definition Tab 总体结构

```text
Service definition

来源方式：
[ Proto 文件 ] [ Reflection 发现 ]

┌────────────────────────────────────────────────────────────┐
│ 左侧：来源配置                                               │
│ - Proto 文件 / bundle                                      │
│ - Reflection 发现配置                                      │
│                                                            │
│ 右侧：服务方法清单                                           │
│ - Service 列表                                              │
│ - Method 列表                                               │
│ - Method 类型                                               │
│ - 使用此 Method                                             │
└────────────────────────────────────────────────────────────┘
```

也可以做成上下结构，取决于现有页面宽度。

---

# 8. Service definition 中如何避免与顶部重复

这是本次方案的重点。

## 8.1 顶部请求栏已经展示的内容

顶部请求栏已经展示：

```text
服务地址
当前 Method
服务端执行
保存
```

所以 Service definition 不再显示一条类似这样的摘要：

```text
Proto 文件：hello.proto
Service：hello.HelloService
Method：SayHello
Method 类型：UNARY
```

这条摘要应该删除。

## 8.2 Service definition 应该展示什么

Service definition 应展示“来源”和“可选方法清单”。

例如：

```text
服务定义来源：Proto 文件
文件包：hello.proto
Proto 版本：v1
变更影响：无

可用方法：
hello.HelloService
  SayHello        UNARY      [使用]
  SayHelloAgain   UNARY      [使用]
```

或者 Reflection：

```text
服务定义来源：Reflection
来源地址：grpcb.in:9000
发现时间：2026-06-26 14:32:18
Service 数量：6

可用方法：
hello.HelloService
  SayHello        UNARY      [使用]
```

## 8.3 当前选中 Method 如何表示

当前选中 Method 已在顶部请求栏显示。

Service definition 的方法清单中可以用轻量标记表示：

```text
hello.HelloService
  SayHello        UNARY      当前使用
```

但不要再在页面上方单独重复一行。

## 8.4 Method 切换交互

在 Service definition 的方法清单中点击“使用”后：

1. 顶部 Method 下拉同步为该 Method；
2. Message Tab 的 requestJsonTemplate 更新；
3. 如果 Request JSON 非空，弹窗确认是否覆盖；
4. 自动切回 Message Tab 或提示用户已切换成功。

推荐交互：

```text
点击 [使用] 后：
- 顶部 Method 变为 hello.HelloService / SayHello
- 若 Request JSON 为空，自动生成模板
- 若 Request JSON 非空，提示是否覆盖
```

---

## 9. Proto 文件模式

### 9.1 职责

Proto 文件模式负责：

1. 单 proto 上传。
2. 多 proto bundle 上传。
3. mainFileName 选择。
4. Proto 解析。
5. protoFiles 回显。
6. protoVersion 展示。
7. protoChangeImpact 展示。
8. 方法清单生成。

### 9.2 Proto 文件模式布局

```text
Service definition / Proto 文件

┌────────────────────────────────────────────────────────────┐
│ Proto 文件                                                  │
│ [上传 .proto] [上传 proto bundle] [从 URL 导入] [解析 Proto] │
│                                                            │
│ 文件包：                                                    │
│ [hello.proto 主文件]                                       │
│ 或                                                         │
│ [common.proto] [main.proto 主文件]                          │
│                                                            │
│ 主文件： [main.proto ▼]                                     │
│ Proto 版本：v1                                              │
│ 变更影响：无 / WARNING / BREAKING                           │
└────────────────────────────────────────────────────────────┘

方法清单：
hello.HelloService
  SayHello        UNARY      [使用]
```

### 9.3 Proto 内容编辑器

Proto 内容不默认铺开。

提供入口：

```text
[查看 / 编辑 Proto 内容]
```

点击后展开：

```text
Proto 内容
┌────────────────────────────────────────────┐
│ syntax = "proto3";                         │
│ ...                                        │
└────────────────────────────────────────────┘
```

### 9.4 Proto 文件 chip

建议文件展示使用 chip：

```text
[common.proto] [main.proto 主文件]
```

要求：

1. 明确标识主文件。
2. 支持删除文件。
3. 支持切换主文件。
4. 支持重新解析。
5. 保存重开后 chip 信息不丢。

### 9.5 解析结果

解析成功后展示轻量摘要：

```text
解析成功：1 个 Service，1 个 Method
```

不要再展开完整 Service/Method 摘要栏。

真正的方法选择放在“方法清单”中。

---

## 10. Reflection 发现模式

### 10.1 职责

Reflection 发现模式负责：

1. 发起 Reflection describe。
2. 获取 descriptorContent。
3. 生成方法清单。
4. 生成 requestJsonTemplate。
5. 保存 descriptor-only 定义。
6. 展示 Guard 错误。

### 10.2 避免服务地址重复

顶部请求栏已经有服务地址。

Reflection 发现不应默认再放一个完全重复的服务地址输入框。

推荐设计：

```text
Reflection 发现

☑ 使用顶部服务地址：grpcb.in:9000

发现超时(ms)：[10000]
[发现服务]

高级：
[使用自定义发现地址]
```

只有展开“使用自定义发现地址”时，才展示：

```text
自定义发现地址：[________________]
```

这样避免顶部服务地址和 Reflection 服务地址重复。

### 10.3 Reflection 布局

```text
Service definition / Reflection 发现

┌────────────────────────────────────────────────────────────┐
│ ☑ 使用顶部服务地址：grpcb.in:9000                           │
│ 发现超时(ms)：[10000]                                       │
│ [发现服务]                                                  │
│                                                            │
│ 发现结果：                                                  │
│ 已发现 6 个 Service，12 个 Method                            │
│ 来源：Reflection                                            │
│ 文件名：reflection://grpcb.in:9000                           │
└────────────────────────────────────────────────────────────┘

方法清单：
hello.HelloService
  SayHello        UNARY      [使用]
```

### 10.4 Guard 错误展示

如果地址被拒绝，错误直接展示在 Reflection 区域内：

```text
该地址不允许访问：localhost is not allowed
```

不要只显示通用 500。

### 10.5 Reflection 保存定义

保存时：

```text
sourceType = REFLECTION
fileName = reflection://host:port
protoContent 可为空
descriptorContent 必须保存
```

UI 展示：

```text
来源：Reflection
Proto 版本：v1
```

---

## 11. Settings Tab 设计

### 11.1 职责

Settings Tab 放低频配置。

### 11.2 内容

```text
Settings

请求超时(ms)     [60000]
TLS              [开关]
Authority        [可选]
```

### 11.3 超时配置拆分

注意区分：

| 配置                    | 位置                              |
| --------------------- | ------------------------------- |
| 请求执行 timeout          | Settings                        |
| Reflection 发现 timeout | Service definition / Reflection |

---

## 12. Response 区设计

### 12.1 职责

Response 区固定在请求配置下方，展示本次执行结果。

### 12.2 Response 布局

```text
响应结果   ● 成功   grpcStatus: OK   grpcCode: 0   耗时: 268ms   大小: 38 B      [复制] [放大]

响应体 | Metadata | 错误详情 | 原始信息

┌────────────────────────────────────────────────────────────┐
│ 1  {                                                       │
│ 2    "reply": "hello MeterSphere"                          │
│ 3  }                                                       │
└────────────────────────────────────────────────────────────┘
```

### 12.3 响应摘要字段

| 字段         | 说明                                    |
| ---------- | ------------------------------------- |
| 执行状态       | 成功 / 失败                               |
| grpcStatus | OK / UNIMPLEMENTED / INVALID_ARGUMENT |
| grpcCode   | 0 / 12 等                              |
| 耗时         | duration                              |
| 大小         | response size                         |

### 12.4 Response Tabs

```text
响应体 | Metadata | 错误详情 | 原始信息
```

### 12.5 空状态

执行前：

```text
暂无响应，请点击服务端执行
```

### 12.6 错误响应

例如 UNIMPLEMENTED：

```text
执行失败
grpcStatus: UNIMPLEMENTED
grpcCode: 12
错误信息：Method not found
```

页面不能白屏。

---

## 13. 信息归位表

| 信息                    | 显示位置                                 |
| --------------------- | ------------------------------------ |
| 协议类型 GRPC             | 顶部请求栏                                |
| 服务地址                  | 顶部请求栏                                |
| 当前 Method             | 顶部请求栏                                |
| Method 下拉分组           | 顶部请求栏                                |
| Request JSON          | Message Tab                          |
| 生成模板 / 重置模板 / 格式化     | Message Tab                          |
| Metadata              | Metadata Tab                         |
| Proto 文件              | Service definition / Proto 文件        |
| Proto bundle          | Service definition / Proto 文件        |
| mainFileName          | Service definition / Proto 文件        |
| Reflection 发现         | Service definition / Reflection      |
| Reflection 地址         | 默认使用顶部服务地址；自定义地址放高级项                 |
| Service 清单            | Service definition 方法清单              |
| Method 清单             | Service definition 方法清单              |
| Method 类型             | Method 下拉项 / Service definition 方法清单 |
| Proto 版本              | Service definition                   |
| 变更影响                  | Service definition                   |
| 请求超时                  | Settings Tab                         |
| 响应结果                  | Response 区                           |
| grpcStatus / grpcCode | Response 摘要                          |
| responseJson          | Response / 响应体                       |

---

## 14. 关键删除项

### 14.1 Message 页面删除

Message 页面中删除以下内容：

```text
Proto 文件：hello.proto
Service：hello.HelloService
Method：SayHello
Method 类型：UNARY
通过 Reflection 发现服务
Proto 版本
变更影响
```

### 14.2 Service definition 页面删除

Service definition 页面不要再单独展示“当前选中方法摘要栏”。

避免：

```text
当前 Service：hello.HelloService
当前 Method：SayHello
Method 类型：UNARY
```

改为方法清单 + 当前使用标记。

---

## 15. 推荐最终界面草图

```text
顶部请求栏
┌────────────────────────────────────────────────────────────┐
│ [GRPC] [localhost:50051________________] [hello.HelloService/SayHello ▼] [服务端执行] [保存] │
└────────────────────────────────────────────────────────────┘

请求配置 Tabs
┌────────────────────────────────────────────────────────────┐
│ Message | Metadata | Service definition | Settings          │
├────────────────────────────────────────────────────────────┤
│ 请求消息（JSON）                         [生成模板] [重置模板] [格式化] │
│ ┌────────────────────────────────────────────────────────┐ │
│ │ 1 {                                                    │ │
│ │ 2   "greeting": "hello MeterSphere"                    │ │
│ │ 3 }                                                    │ │
│ └────────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────┤
│ 响应结果  ● 成功  grpcStatus: OK  grpcCode: 0  耗时: 268ms │
│ 响应体 | Metadata | 错误详情 | 原始信息                    │
│ ┌────────────────────────────────────────────────────────┐ │
│ │ 1 {                                                    │ │
│ │ 2   "reply": "hello MeterSphere"                       │ │
│ │ 3 }                                                    │ │
│ └────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

Service definition Tab：

```text
Service definition
┌────────────────────────────────────────────────────────────┐
│ [ Proto 文件 ] [ Reflection 发现 ]                         │
├────────────────────────────────────────────────────────────┤
│ Proto 文件：                                                │
│ [hello.proto 主文件] [上传 .proto] [上传 bundle] [解析 Proto] │
│ Proto 版本：v1     变更影响：无                              │
├────────────────────────────────────────────────────────────┤
│ 方法清单：                                                  │
│ hello.HelloService                                          │
│   SayHello       UNARY       当前使用                        │
│   SayHelloAgain  UNARY       [使用]                          │
└────────────────────────────────────────────────────────────┘
```

Reflection 子页：

```text
Service definition / Reflection 发现
┌────────────────────────────────────────────────────────────┐
│ ☑ 使用顶部服务地址：grpcb.in:9000                           │
│ 发现超时(ms)：[10000]                                       │
│ [发现服务]                                                  │
│                                                            │
│ 发现结果：已发现 6 个 Service，12 个 Method                  │
│ 来源：Reflection                                            │
│ 文件名：reflection://grpcb.in:9000                           │
├────────────────────────────────────────────────────────────┤
│ 方法清单：                                                  │
│ hello.HelloService                                          │
│   SayHello       UNARY       当前使用                        │
│   SayHelloAgain  UNARY       [使用]                          │
└────────────────────────────────────────────────────────────┘
```

---

## 16. 分阶段实施建议

### Phase A：先做信息归位

目标：去掉重复和拥挤。

范围：

1. Message 页面删除服务定义摘要栏。
2. Metadata 移入 Metadata Tab。
3. Timeout 移入 Settings Tab。
4. Proto / Reflection 移入 Service definition Tab。
5. Response 固定在下方。

不改后端。

### Phase B：优化 Service definition

目标：解决 Service definition 与顶部请求栏重复。

范围：

1. Service definition 内不再展示当前 Method 摘要栏。
2. 改成服务定义来源 + 方法清单。
3. Reflection 默认使用顶部服务地址。
4. 自定义 Reflection 地址收进高级项。
5. 方法清单支持“使用”操作，同步顶部 Method。

### Phase C：优化顶部 Method 下拉

目标：接近 Postman。

范围：

1. Method 下拉按 Service 分组。
2. 支持搜索。
3. 显示 Method 类型。
4. 选择 Method 后自动更新模板。
5. 非空 Request JSON 不静默覆盖。

### Phase D：优化 Response

目标：响应区像调试结果面板。

范围：

1. 响应摘要条。
2. 响应体 / Metadata / 错误详情 / 原始信息 Tabs。
3. 空状态。
4. 错误响应不白屏。

---

## 17. 验收标准

### 17.1 页面布局

1. Message 页面不再展示 Service / Method / Method 类型摘要栏。
2. Service definition 不再重复展示顶部已选 Method 的摘要栏。
3. Proto / Reflection 只在 Service definition 中出现。
4. Metadata 只在 Metadata Tab 中出现。
5. Timeout 只在 Settings 中出现。
6. Response 固定在下方。

### 17.2 功能不退化

必须保证：

1. 单 proto parse 正常。
2. 多 proto bundle parse 正常。
3. Reflection discover 正常。
4. Method 选择正常。
5. requestJsonTemplate 正常。
6. 非空 Request JSON 不被覆盖。
7. debug 正常。
8. run 正常。
9. report 正常。
10. Metadata 发送正常。
11. timeout 生效。

### 17.3 兼容历史能力

必须兼容：

1. protoVersion。
2. protoChangeImpact。
3. Reflection descriptor-only 保存。
4. bundle protoFiles / mainFileName 回显。
5. gRPC 报告详情展示。
6. 场景 / 测试计划中 gRPC 用例执行。

---

## 18. 开发边界

### 18.1 允许修改

```text
gRPC 调试前端布局
gRPC 面板 Tab 结构
Message / Metadata / Service definition / Settings 分区
Method 下拉展示
Response 展示
小范围字段映射
小范围 i18n
空值保护
前端类型定义
```

### 18.2 不允许修改

```text
后端 DB schema
认证 / 权限
场景 / 测试计划执行主链路
Reflection allowlist 审计落库
大范围架构重构
无关 HTTP 调试逻辑
```

---

## 19. 推荐给 Agent 的开发目标

第一轮只做 Phase A + Phase B：

```text
目标：只改红框内 gRPC 主体编辑区，保持 MeterSphere 外层布局不变。
```

具体要求：

1. Message 页面只保留请求 JSON 和响应结果。
2. 删除 Message 页面服务定义摘要栏。
3. Service definition 改成服务定义来源管理，不重复展示顶部当前 Method。
4. Proto / Reflection 全部收进 Service definition。
5. Reflection 默认使用顶部服务地址，避免重复输入。
6. Metadata 独立 Tab。
7. Timeout 独立 Settings。
8. Response 固定下方。
9. 不改后端。
10. 不做 DB migration。

---

## 20. 最终结论

本次优化不是重做整个 gRPC 页面，而是在 MeterSphere 现有 HTTP 调试外框内，对 gRPC 主体编辑区做信息归位。

最终目标是：

```text
顶部请求栏：当前调试目标
Message：请求 JSON
Metadata：gRPC Metadata
Service definition：Proto / Reflection 来源与方法清单
Settings：低频配置
Response：执行结果
```

避免重复展示后，页面会更接近 Postman 的 gRPC 调试体验，同时保持 MeterSphere HTTP 调试页面的一致性。
