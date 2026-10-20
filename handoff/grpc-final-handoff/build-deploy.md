# 构建与部署说明

## 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+ / pnpm
- Docker Desktop（verify4 本地验收）

## 前端 Build

```bash
cd frontend
pnpm install   # 首次
NODE_OPTIONS="--max-old-space-size=16384" npx vite build --config ./config/vite.prod.ts
cd ..
```

产物：`frontend/dist/`（含 `index-*.js` bundle）

## 后端 Package

```bash
# 方式 A：标准（antrun 自动复制 frontend/dist）
mvn -pl backend/app -am package -DskipTests

# 方式 B：跳过 antrun（CI/Jenkins 常用）
rm -rf backend/app/src/main/resources/static
mkdir -p backend/app/src/main/resources/static
cp -r frontend/dist/* backend/app/src/main/resources/static/
mvn -pl backend/app -am package -DskipTests -DskipAntRunForJenkins=true
```

产物：`backend/app/target/app-3.x.jar`

## Docker Build

```bash
docker build -f docker/Dockerfile.grpc-local -t metersphere/local-grpc-final:f7e00fe .
```

## verify4 重建容器

> **重要：必须保留 volume，禁止 `docker volume prune` / `docker system prune`。**

### 1. 记录现有 volume

```bash
docker inspect metersphere-grpc-verify4 --format '{{range .Mounts}}{{.Name}} -> {{.Destination}}{{"\n"}}{{end}}'
```

### 2. 停止并删除容器（不删 volume）

```bash
docker stop metersphere-grpc-verify4
docker rm metersphere-grpc-verify4
```

### 3. 启动新容器

```bash
docker run -d --name metersphere-grpc-verify4 \
  -p 8085:8081 \
  -v <conf-volume-id>:/opt/metersphere/conf \
  -v <data-volume-id>:/opt/metersphere/data \
  -v <logs-volume-id>:/opt/metersphere/logs \
  metersphere/local-grpc-final:f7e00fe
```

### 历史 volume 参考（2026-07-08 环境）

| Volume | 挂载点 |
|---|---|
| `b49124e560a3...` | `/opt/metersphere/conf` |
| `a2e9fc5beea7...` | `/opt/metersphere/data` |
| `829c5586e58b...` | `/opt/metersphere/logs` |

## 健康检查

```bash
curl -i http://localhost:8085/
curl -i http://localhost:8085/is-login
curl -i http://localhost:8085/get-key
```

预期：`/` → 200；`/is-login` → 401 或 200（已登录）；bundle 可从 `index.html` 中 `index-*.js` 确认。

## 登录验证

```bash
curl -s -X POST http://localhost:8085/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"metersphere","authenticate":"LOCAL"}'
```

## gRPC step 回显 API 抽检

```bash
# 需先登录获取 X-AUTH-TOKEN / Cookie
curl -s -H "X-AUTH-TOKEN: <token>" \
  http://localhost:8085/api/scenario/step/get/178349961167700000
```

预期：`code=100200`，body 含 `protocol=GRPC`、`grpcCaseConfig.serverAddress`、`requestJson`。

## Mock gRPC 服务

- 地址（容器内访问宿主机）：`host.docker.internal:50051`
- 服务/方法：`hello.HelloService / SayHello`
- 测试 Proto：见 `test-fixtures/proto/hello.proto`
