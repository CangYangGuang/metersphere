# MeterSphere 工程公开交付与使用指南

> 公开仓库：`https://github.com/CangYangGuang/metersphere`
> 推荐版本：`v0.2.6`

## 1. 交付内容

本项目通过公开 GitHub 仓库和公开 GHCR 镜像交付：

- 完整可构建源码、测试和部署文件；
- Git 标签 `v0.2.6`；
- Docker 镜像 `ghcr.io/cangyangguang/metersphere:v0.2.6`；
- 根目录 `compose.yml`；
- 安装说明、发布说明和已知限制。

同事无需 GitHub 账号或 Token，即可克隆源码并拉取运行镜像。

## 2. 最快安装方式

环境要求：Linux `amd64` Docker Engine，或启用了 Linux 容器的 Docker Desktop；主机端口 `8085` 可用。

```bash
git clone https://github.com/CangYangGuang/metersphere.git
cd metersphere
git checkout v0.2.6
docker compose pull
docker compose up -d
```

查看状态：

```bash
docker compose ps
docker logs -f metersphere
```

状态变为 `healthy` 后访问：

```text
http://localhost:8085
```

## 3. 健康检查

```bash
curl -I http://localhost:8085/
curl -i http://localhost:8085/get-key
curl -i http://localhost:8085/is-login
```

预期 `/` 与 `/get-key` 返回 HTTP 200；未登录时 `/is-login` 返回 401。

## 4. 数据卷

首次启动会创建：

- `metersphere-conf`
- `metersphere-data`
- `metersphere-logs`

停止但保留数据：

```bash
docker compose down
```

不要执行 `docker compose down -v` 或 `docker volume prune`，除非明确要永久删除全部数据。

## 5. 数据边界

公开源码和镜像只交付程序，不包含开发机上的：

- 用户和账号；
- 业务项目与环境配置；
- 接口、用例、场景和测试计划数据；
- 执行历史和报告；
- AI 密钥、Token、日志和 Docker volume。

如果还需要迁移演示数据，必须另行完成备份、敏感信息检查、脱敏和恢复验证。

## 6. 源码构建

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm run type:check
pnpm run build
cd ..

./mvnw -pl backend/app -am package -DskipTests -DskipAntRunForJenkins=true
docker build -f docker/Dockerfile.grpc-local -t metersphere-local:v0.2.6 .
```

Windows PowerShell 中将 `./mvnw` 替换为 `.\mvnw.cmd`。

## 7. 相关文件

- `README.md`
- `compose.yml`
- `deploy/compose.v0.2.6.yml`
- `release/v0.2.6/INSTALL.md`
- `release/v0.2.6/release-notes.md`
- `release/v0.2.6/KNOWN_LIMITATIONS.md`
