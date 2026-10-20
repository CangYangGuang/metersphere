# Chrome 页面自动化配置说明

## 当前目标

为本项目的自动化测试准备 Chrome 页面操作能力，用于后续通过 ChatGPT/Codex 操作 MeterSphere 页面，例如登录、上传 Proto、选择 gRPC 方法、保存用例、执行测试、查看报告等。

## 已安装插件

从当前软件的插件页面看，`Chrome` 插件已经安装。该插件的作用是让 ChatGPT/Codex 可以控制本机 Chrome 页面。

## 使用方式

后续可以直接提出类似请求：

```text
用 Chrome 打开 http://localhost:8085，帮我跑一遍 gRPC 自动化测试主流程。
```

或：

```text
用 Chrome 操作 MeterSphere 页面，验证 Proto 上传、gRPC 方法选择、保存用例、执行和报告展示。
```

## 注意事项

- Chrome 插件适合做页面级自动化测试。
- 如果要查数据库、执行后端单测、控制 Docker 或直接调接口，应使用命令行、接口工具或专门 MCP，不依赖 Chrome 插件。
- 如果第一次连接 Chrome 超时，通常需要确认 Chrome 浏览器中是否弹出授权提示，或重启当前软件/Chrome 后再试。
- 自动化测试时，建议先确认被测系统已经启动，例如 `http://localhost:8085` 可访问。

## 本次检查结果

- 当前软件已安装 Chrome 插件。
- 本机 Chrome 进程正在运行。
- Chrome 浏览器扩展已安装并启用。
- Native host 本机通信注册已恢复，manifest 与注册表检查通过。
- 如果实际建立控制连接仍超时，请确认 Chrome 中是否出现 Codex/ChatGPT 扩展授权提示，并在允许后重试；必要时重启当前 Codex 软件以刷新浏览器控制通道。
