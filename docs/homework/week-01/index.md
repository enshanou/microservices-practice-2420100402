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

## 概念回答

以下均为结合课程理解后的个人表述，非照抄教材定义。

### 1. 什么是微服务架构？

微服务架构是一种把**一个完整应用拆成多个能独立部署的小服务**的架构风格。每个小服务只负责一块明确的业务能力（比如用户服务只管用户、订单服务只管订单），它们各自跑在独立的进程里，通过轻量的网络接口（最典型的是 HTTP/REST、消息队列、gRPC）互相调用。

关键点在于「独立」二字：每个服务可以有**自己的代码仓库、自己的构建流水线、自己的数据库、自己的发布节奏**，改一个服务不需要把整个系统重新打包上线。它本质上是把过去「一个程序干所有事」的思维，换成了「一组小团队各管一摊、通过约定好的契约协作」的组织方式——所以微服务既是一种技术架构，也是一种团队协作方式。

### 2. 微服务和单体架构的主要区别是什么？

我理解最核心的区别可以归到这几个维度：

| 维度 | 单体架构 | 微服务架构 |
| --- | --- | --- |
| 部署单元 | 整个应用打成一个包，一起上线 | 每个服务单独打包、单独部署 |
| 数据存储 | 通常共用一个数据库，表之间直接关联 | 每个服务独享自己的库，跨服务只能通过接口取数 |
| 故障影响 | 一个模块内存泄漏或死循环可能拖垮整个应用 | 单个服务挂掉，其他服务仍可继续对外服务 |
| 技术选型 | 全项目统一语言和框架 | 每个服务可按场景选不同技术栈 |
| 扩展方式 | 只能整体水平复制，浪费资源 | 只对压力大的那个服务单独扩容 |
| 团队协作 | 多人改同一份代码，容易互相阻塞和冲突 | 团队按服务边界划分，能并行开发 |
| 复杂度 | 代码耦合度高，但调用关系直观、排查简单 | 代码解耦了，但引入了分布式复杂度：网络不可靠、数据一致性、链路追踪、服务发现 |

一句话概括：**单体是「用代码内部的模块化换来了简单的运维」，微服务是「用运维和分布式复杂度换来了独立性与可扩展性」**。微服务不是纯粹的升级，而是一次权衡取舍——规模小、团队小的项目用单体往往更划算。

### 3. 为什么本课程先实现单体系统，再逐步拆分为微服务？

我认为这样安排有三个理由：

1. **先保证业务是对的，再谈架构。** 如果一上来就拆微服务，会同时面对「业务逻辑还没理顺」和「分布式问题一大堆」两重困难，出问题时根本分不清是业务写错了还是网络/服务调用出了问题。先把单体跑通，业务逻辑和数据库设计都是确定的，后面拆分才是在**一个已知正确的整体**上做手术。
2. **拆分需要真实存在的边界，而边界只能从单体的演进中观察出来。** 微服务最怕拆错粒度——拆太细变成分布式单体，拆太粗等于没拆。只有先在单体里把模块划分、调用关系、数据依赖都写清楚，才能看出哪些模块天然内聚、适合切出去。凭空设计的边界往往是拍脑袋。
3. **对比才能体现价值。** 先经历单体的痛点（改一处要全量回归、一个模块拖垮全站、无法单独扩容），再去拆微服务，才能真切理解微服务到底解决了什么问题、又新引入了什么代价。否则只是照着教程抄架构，学不到判断力。

### 4. 为什么作业需要提供可重复运行的测试或验证脚本？

因为**没有脚本的作业，别人无法验证，自己也无法复现**。具体来说：

- **可重复性 = 可验证性。** 老师或同学拿到仓库，一条命令就能跑出和我一样的结果，这才叫证据；否则只能靠截图和口头描述，说服力很弱。
- **脚本把「结论」变成了「过程」。** 截图只能证明某一次是成功的，脚本能证明**每次**都能成功，排除了偶然性。
- **微服务场景下这件事尤其重要。** 微服务天然是多进程、多容器、依赖网络的，手工点几下根本没法稳定复现。用 `docker compose up` 加一个脚本一键起停整套环境，是这个领域的基本功。
- **它是后续作业的地基。** 后面每次拆分服务、改接口，都要靠这套脚本做回归，确保没有把已有功能改坏。测试和验证脚本本身就是课程要求交付的产物之一，越早建立越好。

## 问题记录

### 问题 1：Git Bash 中执行 `mvn --version` 报类找不到异常

**现象**

```text
错误: 找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher
原因: java.lang.ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher
```

**排查过程**

1. 先确认 Maven 是否装坏。检查安装目录 `D:\问界\apache-maven-3.9.16-bin\apache-maven-3.9.16`，`bin/`、`boot/`、`lib/` 齐全，其中 `boot/plexus-classworlds-2.11.0.jar` 存在，`lib/` 下有 77 个依赖包 —— 安装是完整的，排除「安装损坏」。
2. 用 `bash -x mvn --version` 追踪启动脚本，发现真正执行的命令是：

   ```text
   exec /c/Java/jdk-17/bin/java \
     -classpath /d/问界/apache-maven-3.9.16-bin/apache-maven-3.9.16/boot/plexus-classworlds-2.11.0.jar \
     -Dclassworlds.conf=/d/问界/.../bin/m2.conf \
     -Dmaven.home=/d/问界/.../apache-maven-3.9.16 \
     org.codehaus.plexus.classworlds.launcher.Launcher --version
   ```

3. 关键就在这里：`-classpath` 传的是 `/d/问界/...` 这种 **Unix 风格路径**，而 `/c/Java/jdk-17/bin/java` 是 **Windows 版 JVM**，它不认识 `/d/...`，于是找不到 jar 包，就报「找不到主类」。

**根因**

在 Git Bash（MINGW64）里敲 `mvn`，命中的是 Maven 的 POSIX shell 脚本 `bin/mvn`。该脚本的 Cygwin 路径转换分支只对 `uname` 返回 `CYGWIN*` 的情况生效，而 Git Bash 返回的是 `MINGW64_NT-*`，走了 `mingw` 分支却**没有把路径转回 Windows 格式**，于是把 Unix 路径直接交给了 Windows JVM。这是 Maven 启动脚本在 Git Bash 下的已知兼容问题，**与 Maven 本身是否安装正确无关**。

**验证**

同一环境下改用 Windows 批处理启动器：

```bash
mvn.cmd --version
```

输出完全正常（见上方「环境检查」的 `mvn --version` 小节）。同时，用 Windows 风格路径手工拼出等价的 java 启动命令也能成功运行 —— 两个方向都确认了根因是路径格式。

**解决方案**

在 Git Bash 中统一使用 `mvn.cmd` 代替 `mvn`。如需长期生效，可在 `~/.bashrc` 中加别名：

```bash
alias mvn='mvn.cmd'
```

或在 PowerShell / cmd 中执行 Maven 命令（Windows 批处理脚本 `mvn.cmd` 的路径处理是正确的）。

**遗留风险**

`alias` 只对交互式 Git Bash 生效；如果后续 CI 脚本或 IDE 内置终端仍调用 `mvn`，需要确认其 shell 环境，必要时改用绝对路径调用 `mvn.cmd`。

### 问题 2：Docker 守护进程未启动

**现象**

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running:
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

**排查过程**

1. `docker version` 能打印出完整的 `Client` 段（Version 29.8.0、API version 1.56、OS/Arch windows/amd64），说明 **Docker CLI 本身可用**。
2. `docker compose version` 正常返回 `v5.5.1`，说明 **Compose 插件已正确安装**。
3. 检查安装位置 `C:\Users\24625\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe` 存在，且 PATH 中已包含 `...\DockerDesktop\resources\bin` —— **Docker Desktop 已安装完成**。
4. 报错信息指向命名管道 `npipe:////./pipe/dockerDesktopLinuxEngine` 不存在，说明**客户端在找后端，但后端（Docker Engine）没有在跑**。

**根因**

Docker Desktop 采用 C/S 架构：`docker` 命令只是客户端，真正的引擎跑在 Docker Desktop 启动的 Linux 虚拟机里，两者通过 Windows 命名管道通信。当前 **Docker Desktop 未启动**，命名管道尚未创建，所以客户端连接失败。

**当前系统版本**

- Windows 11，版本号 10.0.26200.8655，架构 amd64
- Docker Desktop 已安装（含 Client 29.8.0 与 Compose v5.5.1）
- 未安装 `gh` CLI（与 Docker 无关，此处一并记录）

**解决计划**

1. **短期（本次作业内）**：手动启动 Docker Desktop，等待系统托盘鲸鱼图标变为绿色（表示引擎就绪），再重新执行 `docker version`，此时应能同时看到 `Client` 和 `Server` 两段输出。若首次启动提示需要启用 WSL2 或 Hyper-V，按引导完成即可。
2. **验证**：`docker run --rm hello-world` 能正常拉取并运行，即证明引擎完全可用。
3. **中期**：在 Docker Desktop 设置中勾选开机自启，避免每次上课前都要手动启动。
4. **兜底方案**：若 Docker Desktop 在本机始终无法启动（例如虚拟化被占用、企业策略限制），则改用 **远程 Docker 主机** 方案 —— 在 `DOCKER_HOST` 环境变量中指向可用的远程引擎；课程后续涉及容器编排的作业可暂时用远程环境完成，并在本周文档中补充说明。

**说明**

本次作业对 Docker 的要求是「完成环境检查」，不要求必须跑通容器。因此 Docker 暂未启动**不影响本次作业的完成度**，按作业要求已在此记录原因、系统版本与后续解决计划。
