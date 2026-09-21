# 作业 01：开发环境与个人仓库

| 项目 | 内容 |
| --- | --- |
| 姓名 | 区恩善 |
| 学号 | 2420100402 |
| 作业 | week-01 |
| 完成日期 | 2026-09-21 |
| 仓库 | https://github.com/enshanou/microservices-practice-2420100402 |

## 环境检查

| 项目 | 内容 |
| --- | --- |
| 操作系统 | Windows 11 (10.0.26200.8655) |
| 架构 | amd64 |
| 终端 | Git Bash (MINGW64) |

#### java --version

![java --version](screenshots/java-version.png)

#### mvn --version

![mvn --version](screenshots/mvn-version.png)

#### git --version

![git --version](screenshots/git-version.png)

#### docker version

![docker version](screenshots/docker-version.png)

> 说明：`docker` 客户端 29.8.0 已安装可用，但守护进程尚未启动，
> 报错 `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`。
> 根因与排查过程见文末「问题记录 · 问题 2」，待守护进程正常后再补 `Server` 段截图。

#### docker compose version

![docker compose version](screenshots/docker-compose-version.png)

## 概念回答

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

本机已按上述方式在 `~/.bashrc` 中配置别名，并实测验证：

```text
$ type mvn
mvn is aliased to `mvn.cmd'

$ mvn --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Java version: 17.0.11, vendor: Oracle Corporation, runtime: C:\Java\jdk-17
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

> 补充一个配置细节：Git for Windows 的 `/etc/profile.d/bash_profile.sh` 在检测到存在
> `~/.bashrc` 却没有 `~/.bash_profile` 时会自动生成后者来加载前者；本机已显式创建
> `~/.bash_profile`（内容为 `test -f ~/.bashrc && . ~/.bashrc`），保证别名一定被加载。

**遗留风险**

`alias` **只在交互式 shell 中展开**，且 `~/.bashrc` 也只在交互式 shell 启动时被加载。因此：

- 手动在 Git Bash 里敲 `mvn` —— 别名生效，一切正常；
- 但如果某个**脚本**（CI、构建脚本、`bash xxx.sh`）里调用 `mvn`，别名不会生效，
  仍会撞上同样的报错。这类场景需要**显式写 `mvn.cmd`**，或改用绝对路径调用。

同理，IDE（如 IntelliJ IDEA）内置终端若配置为 Git Bash 也会受影响；若使用其自带的
Maven 集成则完全绕过 shell，不受此问题影响。

### 问题 2：Docker 守护进程未启动

**现象**

`docker version` 能打印出 Client 信息，但连接服务端时报错：

```text
Client:
 Version:           29.8.0
 OS/Arch:           windows/amd64
 Context:           desktop-linux
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running:
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

**排查过程**

这个问题表面看是「没启动 Docker Desktop」，但实际点开启动之后仍然起不来，逐层往下查才发现根因不在 Docker 本身：

1. **确认客户端确实装了。** `docker.exe` 位于 `C:\Users\24625\AppData\Local\Programs\DockerDesktop\resources\bin\`（用户级安装，不在 `Program Files`），说明客户端与 Compose 插件都正常，问题只出在引擎侧。
2. **确认引擎服务状态。** `com.docker.service` 服务不存在，说明 Docker Desktop 的后台引擎从未成功初始化过。
3. **检查 WSL。** 执行 `wsl --status` 提示「适用于 Linux 的 Windows 子系统未安装」，建议运行 `wsl.exe --install`。Docker Desktop 在 Windows 上默认使用 **WSL2 后端**，WSL 不可用则引擎无法创建运行容器的 Linux 虚拟机。
4. **检查 Windows 可选功能。**

   ```text
   VirtualMachinePlatform             = Disabled
   Microsoft-Windows-Subsystem-Linux  = Disabled
   ```

   这两个功能是 WSL2 的前置依赖，二者均为禁用状态 —— 这才是守护进程起不来的**直接原因**。
5. **尝试启用功能，失败。** 执行 `Enable-WindowsOptionalFeature` 启用上述功能时，DISM 报「组件存储已损坏」，无法完成功能启用。
6. **确认系统版本。** 本机为 **Windows 11 家庭版 25H2（Build 26200）**。家庭版不含 Hyper-V（查询 `Microsoft-Hyper-V-All` 返回 `CBS_E_UNKNOWN_UPDATE`），因此**不存在「改用 Hyper-V 后端」这条退路**，WSL2 是唯一可行的后端方案。

**根因**

这是一条三层依赖链，缺一环都不行：

```text
Docker Desktop 引擎
   └─ 依赖 WSL2
        └─ 依赖 Windows 功能 VirtualMachinePlatform + Microsoft-Windows-Subsystem-Linux
             └─ 依赖健康的组件存储（WinSxS）
                  └─ ✗ 本机组件存储已损坏，功能无法启用
```

因此「守护进程未启动」只是最表层的表现，真正卡住的是**组件存储损坏导致 WSL2 无法启用**。

**解决方案**

按依赖顺序自下而上修复（需管理员权限，且组件存储修复耗时较长）：

```powershell
# 1) 先修复组件存储（需 30–60 分钟，期间勿中断）
Start-Service BITS          # BITS 必须运行，否则修复用的组件包会下载卡死
Start-Service wuauserv
Repair-WindowsImage -Online -RestoreHealth
# 完成后重启系统

# 2) 再启用 WSL2 所需功能
Start-Service BITS
Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -All -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -All -NoRestart
# 再次重启系统

# 3) 安装/更新 WSL 内核并设为默认版本 2
wsl --update
wsl --set-default-version 2

# 4) 启动 Docker Desktop，验证引擎就绪
docker version          # 应能看到 Server 段
docker run --rm hello-world
```

**遗留风险**

- 组件存储修复依赖 Windows Update 下载组件包，若 BITS / wuauserv 被停止，下载会卡在固定百分比（本次即卡在 50%），需先启动这两个服务。
- 家庭版没有 Hyper-V 作为备选后端，WSL2 一旦不可用就没有兜底方案；必要时只能改用远程 Docker 主机（`DOCKER_HOST` 指向远端引擎）。
- 本次作业只要求完成环境检查，不要求跑通容器；Docker 客户端已确认正常，该问题不影响本次作业的完成度。


