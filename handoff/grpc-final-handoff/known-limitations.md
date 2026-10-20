# 已知限制

## 1. task-runner 回调端口

**现象：** 容器内 task-runner 回调 `http://localhost:8085`，而实际映射为 `8081`，可能导致场景/调试抽屉内**异步服务端执行**卡在「停止」状态。

**影响范围：** UI 抽屉内异步 debug/run；直连 API 或同步路径通常正常。

**建议：** 接手方在 Docker 网络层修复回调地址，或接受该环境限制并在验收文档中标记 SKIP。

---

## 2. TLS 自定义证书文件上传

**状态：** 暂未支持通过 UI 上传自定义 TLS 证书文件。

**影响范围：** mTLS / 自定义 CA 场景需手动配置或后续迭代。

---

## 3. flat `/grpc/debug/send` 与 Settings

**行为：**

- flat `/grpc/debug/send` 路径下，**Settings Tab 配置生效**。
- **前置/后置处理器**在 **case 执行链**（用例运行/场景运行）中生效，而非 flat debug send 路径。

---

## 4. 场景自定义 gRPC 独立前后置 UI

**状态：** 场景自定义 gRPC 步骤的**独立前后置 UI** 未完全独立实现；前后置逻辑依赖 case 执行链与 Settings 配置。

---

## 5. Git 未 push

**状态：** 项目 commit 链（HEAD `f7e00fe`）**未 push 到 remote**。

**接手方需：**

1. 自行配置 remote（公司 GitLab/GitHub 等）。
2. 审查 `.gitattributes` 新增规则（`*.java/*.vue text diff`）。
3. push 前跑 Gate（vue-tsc + api-test compile/test）。

---

## 6. hello.proto 仓库位置

**说明：** 项目根 `proto/` 目录**不含** `hello.proto`（验收使用 mock `hello.HelloService`）。本交接包 `test-fixtures/proto/hello.proto` 为标准 mock fixture，供接手方本地 mock 服务使用。

---

## 7. pre-commit hook 与 Vue 文件

**历史问题：** pre-commit lint-staged 曾在 Windows 环境下将部分 Vue/Java blob 写损坏。

**已缓解：** `.gitattributes` 强制 text diff；`f7e00fe` 已 restore 可读文本。

**建议：** 提交 Vue/Java 文件后执行 `git show HEAD:<path> | head` 确认 blob 为文本。
