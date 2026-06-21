# 13-CyberChef Windows Docker 启动说明

交付品牌：码研工坊。

## 准备

```powershell
Set-Location C:\work\cyberspace_Security_shot_time\13-CyberChef
$env:ENV_ROOT = "C:\Environment"
```

## 启动

```powershell
docker compose -f deploy\windows-docker\docker-compose.yml up --build
```

访问：`http://127.0.0.1:18080`。

运行数据通过 Compose 挂载到 `$env:ENV_ROOT`，不要写入源码目录；真实密钥放入 `C:\Environment\12-secrets`，不要写入镜像和仓库。
