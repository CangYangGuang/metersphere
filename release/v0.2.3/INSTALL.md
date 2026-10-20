# MeterSphere v0.2.3 安装说明

## 环境要求

- Linux `amd64` Docker Engine，或启用 Linux 容器的 Docker Desktop。
- 主机端口 `8085` 未被占用。
- 能够访问 GitHub 和 GitHub Container Registry（GHCR）。

源码仓库和运行镜像均为公开资源，不需要 GitHub Token。

## 安装并启动

```bash
git clone https://github.com/CangYangGuang/metersphere.git
cd metersphere
git checkout v0.2.3
docker compose pull
docker compose up -d
```

首次启动会创建三个命名卷：

- `metersphere-conf`
- `metersphere-data`
- `metersphere-logs`

应用首次启动可能需要数分钟。查看状态：

```bash
docker compose ps
docker logs -f metersphere
```

容器状态变为 `healthy` 后访问：

```text
http://localhost:8085
```

健康检查：

```bash
curl -I http://localhost:8085/
curl -i http://localhost:8085/get-key
curl -i http://localhost:8085/is-login
```

预期 `/` 和 `/get-key` 返回 HTTP 200；未登录时 `/is-login` 返回 401。

## 停止和重新启动

保留数据并停止：

```bash
docker compose down
```

重新启动：

```bash
docker compose up -d
```

不要执行 `docker compose down -v` 或 `docker volume prune`，除非明确要永久删除全部 MeterSphere 数据。

## 数据说明

公开镜像只包含程序，不包含开发机上的账号、项目、测试数据、报告、日志或密钥。同事首次启动会得到全新环境。
