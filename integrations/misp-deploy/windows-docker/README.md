# 08-MISP Windows Docker 启动说明

交付品牌：码研工坊。

## 准备

```powershell
Set-Location C:\work\cyberspace_Security_shot_time\08-MISP
$env:ENV_ROOT = "C:\Environment"
```

## 启动

```powershell
docker compose -f deploy\windows-docker\docker-compose.yml up --build
```

本项目 Docker 入口执行命令行校验/报告生成，不暴露 Web 端口。

运行数据通过 Compose 挂载到 `$env:ENV_ROOT`，不要写入源码目录；真实密钥放入 `C:\Environment\12-secrets`，不要写入镜像和仓库。
