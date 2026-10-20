# gRPC 测试入门与 MeterSphere 自测说明

## 1. gRPC 是什么

gRPC 可以理解成另一种接口协议。

常见 HTTP 接口通常长这样：

```text
POST /api/user/create
body: {"name":"张三"}
```

gRPC 不主要靠 URL 路径区分接口，而是靠：

```text
Service 服务名
Method 方法名
Request 请求消息
Response 响应消息
```

例如：

```text
服务：HelloService
方法：SayHello
请求：{"greeting":"metersphere"}
返回：{"reply":"hello metersphere"}
```

## 2. Proto 文件是什么

Proto 文件是 gRPC 的接口说明书。

它告诉系统：

```text
有哪些服务
服务下面有哪些方法
每个方法的请求参数长什么样
每个方法的返回值长什么样
```

示例：

```proto
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

这段 Proto 的意思是：

```text
有一个服务 HelloService
服务里有一个方法 SayHello
请求参数是 HelloRequest
返回结果是 HelloReply
请求里有 greeting 字段
返回里有 reply 字段
```

## 3. MeterSphere 里怎么测 gRPC

完整流程如下：

1. 填写 gRPC 服务地址

```text
host.docker.internal:50051
```

2. 上传 Proto 文件

Proto 文件用于告诉 MeterSphere 这个 gRPC 服务有哪些接口、参数是什么。

3. 选择 Method

上传并解析 Proto 成功后，Method 下拉框里会出现可测试的方法，例如：

```text
hello.HelloService / SayHello
```

4. 生成请求 JSON

MeterSphere 会根据 Proto 自动生成请求模板，例如：

```json
{
  "greeting": "string"
}
```

可以改成：

```json
{
  "greeting": "metersphere"
}
```

5. 点击服务端执行

MeterSphere 会真正调用 gRPC 服务。

6. 查看响应结果

成功时通常能看到：

```json
{
  "reply": "hello metersphere"
}
```

状态应为：

```text
grpcStatus = OK
grpcCode = 0
```

## 4. 主 Proto 和依赖 Proto

有些 gRPC 接口只需要一个 Proto 文件。

有些项目会拆成多个 Proto 文件，例如：

```text
main.proto
common.proto
```

主 Proto 通常是入口文件，里面一般有 service：

```proto
import "common.proto";

service UserService {
  rpc CreateUser (CreateUserRequest) returns (CreateUserReply);
}
```

依赖 Proto 通常放公共结构，例如 message、enum：

```proto
message User {
  string name = 1;
  int32 age = 2;
}
```

所以可以这样理解：

```text
主 Proto = 入口文件，通常包含 service
依赖 Proto = 被 import 的文件，通常放公共 message、enum
```

在 MeterSphere 页面上：

```text
上传主 Proto：上传 main.proto
上传依赖 Proto：上传 common.proto
```

如果主 Proto 里写的是：

```proto
import "google/api/annotations.proto";
import "google/api/http.proto";
```

那么依赖 Proto 的路径也要保持一致：

```text
google/api/annotations.proto
google/api/http.proto
```

不能只上传成：

```text
annotations.proto
http.proto
```

否则可能会报：

```text
missing import google/api/annotations.proto
```

## 5. 怎么判断 gRPC 测试通过

不是上传成功就算通过，而是要看完整链路。

建议检查：

```text
Method 下拉能选到服务和方法
请求 JSON 模板能生成
服务端执行能返回结果
保存后重新打开，主 Proto 和依赖 Proto 仍然存在
报告里 grpcStatus、grpcCode、requestJson、responseJson 正常
```

## 6. 当前 MeterSphere 项目的自测结论

本项目已经验证过核心 gRPC 链路：

```text
MeterSphere
 -> 读取 Proto
 -> 选择 Method
 -> 生成请求
 -> 调用本地 gRPC 服务
 -> 返回结果
 -> 生成报告
```

自动验证结果：

```text
VALIDATION PASS
grpcCode = 0
```

说明当前最核心的 gRPC 测试能力是通的。

## 7. 一句话总结

gRPC 测试可以理解为：

```text
先用 Proto 告诉 MeterSphere 接口长什么样，
再填服务地址和请求参数，
让 MeterSphere 去调用服务，
最后看返回值和报告是否正确。
```
