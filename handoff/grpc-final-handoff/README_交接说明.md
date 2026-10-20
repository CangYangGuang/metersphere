# MeterSphere gRPC 自动化测试二开 — 最终交接说明

## 1. 项目信息

| 项 | 值 |
|---|---|
| 项目名称 | MeterSphere gRPC 自动化测试二开 |
| 仓库路径 | `metersphere-3.6.8-lts` |
| 当前 HEAD | **`f7e00fe`** — `fix(grpc): commit final scenario grpc repairs and text attributes` |
| 是否已 push | **否** |
| 交接包日期 | 2026-07-09 |

## 2. 核心功能清单

- gRPC 接口定义：Proto 上传/解析、依赖 Proto、descriptor 持久化
- gRPC 调试页：服务定义、Method 选择、requestJson 模板、服务端/本地执行
- gRPC 用例：保存、重开、运行、报告详情（grpcStatus/grpcCode/requestJson/responseJson）
- 另存为接口 / 同步添加用例（gRPC 配置完整传递）
- 场景：引用 gRPC 用例、**自定义 gRPC 请求**（Proto 上传、执行、保存、重开回显）
- 场景导入抽屉：协议 GRPC 可选
- 测试计划：关联 gRPC 用例/场景并执行
- Runtime Settings / 前置 / 后置处理器（case 执行链）
- Reflection 自动发现（Settings Tab）
- Proto bundle 版本管理

## 3. 关键提交列表

| Commit | 说明 |
|---|---|
| `f7e00fe` | 修复损坏 blob：`DefaultStepParser` gRPC 回显 + `scenarioGrpcCustomEditor` UI；`.gitattributes` 文本 diff |
| `e90c35e` | `StepParser.parseDetailFromBlob` + Proto 上传点击修复 |
| `b5b7e03` | 场景自定义 gRPC 编辑器对齐调试页 UI |
| `87a7c54` / `c74423c` | Runtime Settings + 前后置处理器 |
| `403e87f` / `77a6e4d` | 场景 gRPC 支持 |
| `457af15` | 报告深链修复 |
| `9fa8e89` ~ `242ec0d` | 保存/另存为/同步用例/失败响应等修复链 |

完整 30 条见 `git-log.txt`。

## 4. 构建命令

### 前端

```bash
cd frontend
NODE_OPTIONS="--max-old-space-size=16384" npx vite build --config ./config/vite.prod.ts
# 或
pnpm run build
cd ..
```

### 后端

```bash
# 若 skipAntRunForJenkins=true，需手动复制 frontend/dist 到 backend/app/src/main/resources/static
mvn -pl backend/app -am package -DskipTests -DskipAntRunForJenkins=true
```

### Gate（建议）

```bash
cd frontend && npx vue-tsc --noEmit --skipLibCheck && cd ..
mvn -pl backend/services/api-test -am compile -DskipTests
mvn -pl backend/services/api-test -am test -Dtest="DefaultStepParserGrpcTest" -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Docker 部署

```bash
docker build -f docker/Dockerfile.grpc-local -t metersphere/local-grpc-final:f7e00fe .
```

**verify4 重建容器（必须保留 volume）：**

```bash
# 停止旧容器（不要 volume prune）
docker stop metersphere-grpc-verify4

# 启动新容器，挂载原有 conf/data/logs volume
docker run -d --name metersphere-grpc-verify4 \
  -p 8085:8081 \
  -v <conf-volume>:/opt/metersphere/conf \
  -v <data-volume>:/opt/metersphere/data \
  -v <logs-volume>:/opt/metersphere/logs \
  metersphere/local-grpc-final:f7e00fe
```

详见 `build-deploy.md`。

## 6. verify4 访问地址

| 项 | 值 |
|---|---|
| URL | http://localhost:8085 |
| 调试页 | http://localhost:8085/#/api-test/debug |
| 场景 | http://localhost:8085/#/api-test/scenario |
| 容器名 | `metersphere-grpc-verify4` |
| 端口 | `8085 → 8081` |

## 7. 测试账号

| 项 | 值 |
|---|---|
| 用户名 | `admin` |
| 密码 | `metersphere` |
| 认证方式 | LOCAL |

## 8. MCP-Chrome 验证结论

| 阶段 | 结论 |
|---|---|
| Phase 0–7 UI 回归 | 见 `docs/test_cases.md` / `docs/grpc_automation_full_test_cases.md` — 主链路 **PASS** |
| 场景自定义 gRPC 重开回显 | API 验证 **PASS**（stepId=`178349961167700000`） |
| 场景 Proto 上传 | 代码修复已提交；完整 MCP 点击流建议接手方在重建 verify4 后复验 |
| task-runner 异步执行 | 环境限制可能导致抽屉内「停止」卡住 — 见 `known-limitations.md` |

## 9. 已知限制

见 `known-limitations.md`（5 条）。

## 10. 接手方下一步建议

1. **配置 remote 并 push**（当前未 push）。
2. **基于 `f7e00fe` 重建 verify4 镜像**，保留现有 Docker volume。
3. 使用 `test-fixtures/proto/hello.proto` + mock `host.docker.internal:50051` 跑 MCP-Chrome 全链路。
4. 复验场景自定义 gRPC：上传 Proto → 选 Method → 保存 → 重开（step/get 非空）。
5. 阅读 `docs/grpc_automation_full_test_cases.md` 按 P0 用例逐项验收。

## 11. 本交接包内容

```
handoff/grpc-final-handoff/
├─ README_交接说明.md          ← 本文件
├─ git-status.txt
├─ git-log.txt
├─ build-deploy.md
├─ verification-report.md
├─ known-limitations.md
├─ test-fixtures/proto/        ← hello.proto + poloar_control + google/api
└─ docs/                       ← 自动化用例 + UI 优化说明
```

不含源码、node_modules、target、dist、logs、.git。
