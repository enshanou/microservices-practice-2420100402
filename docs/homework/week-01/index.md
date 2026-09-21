# 作业 01：开发环境与个人仓库

| 项目 | 内容 |
| --- | --- |
| 姓名 |  |
| 学号 | 2420100402 |
| 作业 | week-01 |
| 完成日期 | 2026-09-21 |
| 仓库 | https://github.com/enshanou/microservices-practice-2420100402 |

## 环境检查

### 检查环境

| 项目 | 内容 |
| --- | --- |
| 操作系统 | Windows 11 (10.0.26200.8655) |
| 架构 | amd64 |
| 终端 | Git Bash (MINGW64) |

### 命令输出

以下为本机实际执行结果。

#### `java --version`

```text
java 17.0.11 2024-04-16 LTS
Java(TM) SE Runtime Environment (build 17.0.11+7-LTS-207)
Java HotSpot(TM) 64-Bit Server VM (build 17.0.11+7-LTS-207, mixed mode, sharing)
```

- 结论：Java 17 LTS 安装正常。选 17 是因为它是当前主流的企业级 LTS 版本，Spring Boot 3.x 也要求 Java 17 起步，后续课程的微服务框架能直接用。

#### `mvn --version`

```text
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: D:\问界\apache-maven-3.9.16-bin\apache-maven-3.9.16
Java version: 17.0.11, vendor: Oracle Corporation, runtime: C:\Java\jdk-17
Default locale: zh_CN, platform encoding: GBK
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

- 结论：Maven 3.9.16 安装正常，`Maven home` 与 `Java version` 均被正确识别。
- 注意：在 Git Bash 中必须使用 `mvn.cmd` 而非 `mvn`，否则会报错，原因见下方「问题记录」。

#### `git --version`

```text
git version 2.55.0.windows.3
```

- 结论：Git 安装正常。
- 已配置全局身份：`user.name = enshanou`，`user.email = enshanou@gmail.com`。

#### `docker version`

```text
Client:
 Version:           29.8.0
 API version:       1.56
 Go version:        go1.26.8
 Git commit:        88096ef
 Built:             Thu Sep  3 21:53:38 2026
 OS/Arch:           windows/amd64
 Context:           desktop-linux
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running:
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

- 结论：Docker **客户端** 29.8.0 安装正常，但**守护进程（daemon）未启动**，因此无法返回 Server 段信息。原因与解决计划见下方「问题记录」。

#### `docker compose version`

```text
Docker Compose version v5.5.1
```

- 结论：Docker Compose 插件安装正常，版本 v5.5.1，满足课程后续编排多容器服务的需要。
