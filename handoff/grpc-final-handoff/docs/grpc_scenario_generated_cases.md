# gRPC 场景测试用例生成稿

> 适用范围：基于当前交接目录里的 Proto，直接生成可落地的 MeterSphere 场景测试用例。
>
> 说明：
> - `hello_manual_test.proto` 适合做最小连通性验证
> - `scenario_main.proto + scenario_common.proto` 适合做多服务业务链路
> - `polar_control.proto` 适合做真实设备控制、项目状态、变量读写、记录器生命周期
> - PolarControl 真实地址按当前验证结果使用 `host.docker.internal:9099`
> - scenario_main 的 mock 服务可用 `host.docker.internal:50052`

## 场景总览

| ID | Proto | 场景主题 | 步数 | 覆盖点 |
|---|---|---:|---:|---|
| SC-01 | `hello_manual_test.proto` | 最小连通性 smoke | 1 | 标量请求、单接口响应 |
| SC-02 | `scenario_main.proto` | 下单到发货通知主链路 | 6 | 嵌套对象、enum、repeated、map、多步变量传递 |
| SC-03 | `scenario_main.proto` | 支付后退款链路 | 5 | 退款接口、业务回查、通知 |
| SC-04 | `polar_control.proto` | 项目加载与状态检查 | 5 | `Empty` 请求/响应、项目状态、版本查询 |
| SC-05 | `polar_control.proto` | 设备激活与变量读写 | 6 | 列表、激活、读取、写入、变量路径传递 |
| SC-06 | `polar_control.proto` | 记录器生命周期 | 7 | oneof、嵌套 oneof、创建/修改/启停/删除 |

## 直接创建建议

如果你现在要在 MeterSphere 场景页里直接建，建议先建这 3 条：

1. `SC-01`，最容易先跑通
2. `SC-02`，适合验证多步变量传递
3. `SC-04` 或 `SC-05`，适合验证 PolarControl 的真实链路

优先填的地址：
- `hello_manual_test.proto` -> `host.docker.internal:50051`
- `scenario_main.proto` -> `host.docker.internal:50052`
- `polar_control.proto` -> `host.docker.internal:9099`

## 场景页填写顺序

1. 点左上角 `新建场景`
2. 协议选 `GRPC`
3. 先填场景名称，例如 `SC-01 Hello smoke`
4. 在协议/服务定义里上传对应主 Proto
5. 有依赖的先上传依赖 Proto
6. 在步骤里逐个加 gRPC 请求
7. 按本文件里的 JSON 填请求体
8. 按本文件里的提取规则把变量串起来
9. 保存后先跑 `SC-01`，再跑多步链路

## SC-01 Hello smoke

- Proto：`hello_manual_test.proto`
- Service/Method：`hello.HelloService / SayHello`
- 推荐地址：`host.docker.internal:50051`

### 请求

```json
{
  "greeting": "meterSphere smoke"
}
```

### 断言

- `grpcStatus = OK`
- `grpcCode = 0`
- `responseJson.reply = "hello meterSphere smoke"`

### 适用目的

- 验证 gRPC 调用链最小闭环
- 验证 Proto 解析、方法选择、请求模板、响应展示

## SC-02 下单到发货通知主链路

- Proto：`scenario_main.proto`
- 依赖 Proto：`scenario_common.proto`
- Service/Method 顺序：
  1. `UserService / CreateUser`
  2. `ProductService / ListProducts`
  3. `OrderService / CreateOrder`
  4. `PaymentService / PayOrder`
  5. `ShippingService / ShipOrder`
  6. `NotificationService / SendMessage`
- 推荐地址：`host.docker.internal:50052`

### Step 1 CreateUser

```json
{
  "userName": "alice",
  "level": "USER_LEVEL_VIP",
  "address": {
    "province": "Zhejiang",
    "city": "Hangzhou",
    "detail": "MeterSphere Road 1"
  }
}
```

提取变量：
- `user.userId -> ${userId}`

### Step 2 ListProducts

```json
{
  "page": {
    "page": 1,
    "size": 10,
    "keyword": "grpc"
  },
  "tags": ["electronics", "test"]
}
```

提取变量：
- `products[0].productId -> ${productId}`
- `products[0].price.amount -> ${productPrice}`

### Step 3 CreateOrder

```json
{
  "userId": "${userId}",
  "items": [
    {
      "productId": "${productId}",
      "productName": "grpc-keyboard",
      "quantity": 1,
      "price": {
        "currency": "CNY",
        "amount": 199
      }
    },
    {
      "productId": "P10002",
      "productName": "grpc-mouse",
      "quantity": 2,
      "price": {
        "currency": "CNY",
        "amount": 99
      }
    }
  ]
}
```

提取变量：
- `order.orderId -> ${orderId}`

### Step 4 PayOrder

```json
{
  "orderId": "${orderId}",
  "amount": {
    "currency": "CNY",
    "amount": 397
  },
  "payChannel": "ALIPAY"
}
```

提取变量：
- `paymentId -> ${paymentId}`
- `status -> ${payStatus}`

### Step 5 ShipOrder

```json
{
  "orderId": "${orderId}",
  "address": {
    "province": "Zhejiang",
    "city": "Hangzhou",
    "detail": "MeterSphere Road 1"
  }
}
```

提取变量：
- `trackingNo -> ${trackingNo}`

### Step 6 SendMessage

```json
{
  "targetUserId": "${userId}",
  "title": "Order shipped",
  "content": "Your order has been shipped",
  "variables": {
    "orderId": "${orderId}",
    "trackingNo": "${trackingNo}"
  }
}
```

### 断言

- 每一步 `grpcStatus = OK`
- 每一步 `grpcCode = 0`
- `CreateUserReply.result.code = RESULT_CODE_OK`
- `CreateOrderReply.order.status = ORDER_STATUS_CREATED`
- `PayOrderReply.status = ORDER_STATUS_PAID`
- `ShipOrderReply.status = ORDER_STATUS_SHIPPED`
- `SendMessageReply.messageId` 不为空

## SC-03 支付后退款链路

- Proto：`scenario_main.proto`
- 重点方法：`CreateUser`、`CreateOrder`、`PayOrder`、`RefundOrder`、`GetOrder`、`SendMessage`

### 建议步骤

1. `CreateUser`
2. `CreateOrder`
3. `PayOrder`
4. `RefundOrder`
5. `GetOrder` 或 `SendMessage`

### RefundOrder 请求

```json
{
  "orderId": "${orderId}",
  "paymentId": "${paymentId}",
  "amount": {
    "currency": "CNY",
    "amount": 397
  },
  "reason": "user canceled"
}
```

### 断言

- `refundId = "REF10001"` 或不为空
- `result.code = RESULT_CODE_OK`
- 退款后订单查询结果状态符合预期

## SC-04 项目加载与状态检查

- Proto：`polar_control.proto`
- Service/Method 顺序：
  1. `ProjectService / Load`
  2. `ProjectService / GetCurrent`
  3. `ProjectService / GetSoftVersion`
  4. `ProjectService / StartMeasuring`
  5. `ProjectService / StopMeasuring`
- 推荐地址：`host.docker.internal:9099`

### Step 1 Load

```json
{
  "project_path": "D:\\PolarControl\\DemoProject",
  "is_direct": true
}
```

### Step 2 GetCurrent

```json
{}
```

### Step 3 GetSoftVersion

```json
{}
```

### Step 4 StartMeasuring

```json
{}
```

### Step 5 StopMeasuring

```json
{}
```

### 断言

- `Load` 返回 `ProjectResponse`
- `GetCurrent` 返回当前项目，不为空
- `GetSoftVersion.version` 不为空
- 所有空请求接口都应返回 `grpcStatus = OK`

## SC-05 设备激活与变量读写

- Proto：`polar_control.proto`
- Service/Method 顺序：
  1. `DeviceService / ListDevices`
  2. `DeviceService / ActivateDevice`
  3. `DeviceService / ListVariables`
  4. `DeviceService / ReadVariable`
  5. `DeviceService / WriteVariable`
  6. `DeviceService / DeactivateDevice`
- 推荐地址：`host.docker.internal:9099`

### Step 1 ListDevices

```json
{
  "typeFilter": "DEVICE_TYPE_UNSPECIFIED"
}
```

提取变量：
- `devices[0].id -> ${deviceId}`

### Step 2 ActivateDevice

```json
{
  "device_id": "${deviceId}"
}
```

### Step 3 ListVariables

```json
{
  "device_id": "${deviceId}",
  "page_size": 20,
  "page_index": 1
}
```

提取变量：
- `variables[0].display_path -> ${variablePath}`

### Step 4 ReadVariable

```json
{
  "device_id": "${deviceId}",
  "variable_path": "${variablePath}"
}
```

### Step 5 WriteVariable

```json
{
  "device_id": "${deviceId}",
  "variable_path": "${variablePath}",
  "value": 123
}
```

### Step 6 DeactivateDevice

```json
{
  "device_id": "${deviceId}"
}
```

### 断言

- `ListDevices.devices` 不为空
- `ReadVariable.value` 不为空
- `WriteVariable` 返回空响应但状态成功
- 激活/停用接口均为 `Empty` 响应

## SC-06 记录器生命周期

- Proto：`polar_control.proto`
- Service/Method 顺序：
  1. `RecorderService / GetAllRecorders`
  2. `RecorderService / Create`
  3. `RecorderService / GetRecorder`
  4. `RecorderService / Modify`
  5. `RecorderService / Start`
  6. `RecorderService / Stop`
  7. `RecorderService / DeleteRecorder`
- 推荐地址：`host.docker.internal:9099`

### Step 1 GetAllRecorders

```json
{}
```

### Step 2 Create

```json
{
  "name": "ms_grpc_tmp_001",
  "file_path": "D:\\temp\\ms_grpc_tmp_001.mf4"
}
```

### Step 3 GetRecorder

```json
{
  "name": "ms_grpc_tmp_001"
}
```

### Step 4 Modify

```json
{
  "name": "ms_grpc_tmp_001",
  "modify": {
    "file_path": "D:\\temp\\ms_grpc_tmp_002.mf4"
  }
}
```

### Step 5 Start

```json
{
  "name": "ms_grpc_tmp_001"
}
```

### Step 6 Stop

```json
{
  "name": "ms_grpc_tmp_001"
}
```

### Step 7 DeleteRecorder

```json
{
  "name": "ms_grpc_tmp_001"
}
```

### 断言

- `GetAllRecorders` 返回 `recorder_list`
- `Create / Modify / Start / Stop / DeleteRecorder` 均成功
- `RecorderResponse` oneof 能正确展开
- `RecorderModify` 的嵌套 oneof 可正常回填

## 落地建议

1. 场景协议选 `GRPC`
2. 先上传主 Proto，再上传依赖 Proto
3. 场景步内尽量用变量串起来，不要每步都手工写死
4. PolarControl 场景优先从 `ListDevices`、`GetCurrent`、`Load` 这类低风险接口开始
5. 真机环境里，`ReadVariable / WriteVariable / Recorder` 这类步骤要准备好可用设备、变量路径和可写目录
