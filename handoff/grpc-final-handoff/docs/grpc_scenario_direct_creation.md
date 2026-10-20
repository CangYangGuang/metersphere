# 直接创建版

> 这份不是给你手工照着看文档的，是给“直接创建到软件里”时的最终内容底稿。

## SC-01

场景名称：`SC-01 Hello smoke`

步骤 1：
- 类型：`gRPC`
- 方法：`hello.HelloService / SayHello`
- 地址：`host.docker.internal:50051`
- 请求：
```json
{"greeting":"meterSphere smoke"}
```

## SC-02

场景名称：`SC-02 下单到发货通知`

步骤 1：
- 方法：`UserService / CreateUser`
- 请求：
```json
{"userName":"alice","level":"USER_LEVEL_VIP","address":{"province":"Zhejiang","city":"Hangzhou","detail":"MeterSphere Road 1"}}
```

步骤 2：
- 方法：`ProductService / ListProducts`
- 请求：
```json
{"page":{"page":1,"size":10,"keyword":"grpc"},"tags":["electronics","test"]}
```

步骤 3：
- 方法：`OrderService / CreateOrder`
- 请求：
```json
{"userId":"${userId}","items":[{"productId":"${productId}","productName":"grpc-keyboard","quantity":1,"price":{"currency":"CNY","amount":199}}]}
```

步骤 4：
- 方法：`PaymentService / PayOrder`
- 请求：
```json
{"orderId":"${orderId}","amount":{"currency":"CNY","amount":199},"payChannel":"ALIPAY"}
```

步骤 5：
- 方法：`ShippingService / ShipOrder`
- 请求：
```json
{"orderId":"${orderId}","address":{"province":"Zhejiang","city":"Hangzhou","detail":"MeterSphere Road 1"}}
```

步骤 6：
- 方法：`NotificationService / SendMessage`
- 请求：
```json
{"targetUserId":"${userId}","title":"Order shipped","content":"Your order has been shipped","variables":{"orderId":"${orderId}"}}
```

## SC-04

场景名称：`SC-04 PolarControl 设备链路`

地址：`host.docker.internal:9099`

步骤 1：
- 方法：`Polar.v1.DeviceService / ListDevices`
- 请求：
```json
{"typeFilter":"DEVICE_TYPE_UNSPECIFIED"}
```

步骤 2：
- 方法：`Polar.v1.DeviceService / ActivateDevice`
- 请求：
```json
{"device_id":"${deviceId}"}
```
步骤 3：
- 方法：`Polar.v1.DeviceService / ListVariables`
- 请求：
```json
{"device_id":"${deviceId}","page_size":20,"page_index":1}
```

步骤 4：
- 方法：`Polar.v1.DeviceService / ReadVariable`
- 请求：
```json
{"device_id":"${deviceId}","variable_path":"${variablePath}"}
```

步骤 5：
- 方法：`Polar.v1.DeviceService / WriteVariable`
- 请求：
```json
{"device_id":"${deviceId}","variable_path":"${variablePath}","value":123}
```

步骤 6：
- 方法：`Polar.v1.DeviceService / DeactivateDevice`
- 请求：
```json
{"device_id":"${deviceId}"}
```
