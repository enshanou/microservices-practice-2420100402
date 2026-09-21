# 截图说明

本目录存放 week-01 作业的证据截图，共 7 张，`submission.md` 中已全部引用。

| # | 文件名 | 内容 | 来源 |
| --- | --- | --- | --- |
| ① | `01-github-repo.png` | GitHub 仓库页面 | Chrome 无头模式抓取的真实页面，含 Public 标识与 7 Commits |
| ② | `02-git-log-graph.png` | 提交记录 | `git log --oneline --graph` 实机输出 |
| ③ | `03-java-version.png` | Java 版本 | `java --version` 实机输出 |
| ④ | `04-maven-version.png` | Maven 版本 | `mvn.cmd --version` 实机输出 |
| ⑤ | `05-git-version.png` | Git 版本 | `git --version` 实机输出 |
| ⑥ | `06-docker-version.png` | Docker 版本 | `docker version` 实机输出（含守护进程未启动的报错） |
| ⑦ | `07-docker-compose-version.png` | Docker Compose 版本 | `docker compose version` 实机输出 |

## 各图要点

- **① GitHub 仓库页面**：可见仓库全名 `enshanou / microservices-practice-2420100402`、
  **Public** 标识、分支 `main`、根目录文件列表（`docs/`、`src/`、`README.md`）、
  Latest commit `542e322`、右上角 **7 Commits**。
- **② 提交记录**：7 条提交，自下而上为 `Initial commit` → 目录结构 → 环境检查 →
  概念与问题 → 截图与说明 → 截图清单 → 本次截图提交。
- **③④⑤⑦ 版本命令**：均在 Git Bash（MINGW64）中执行，命令行与输出同框。
- **⑥ Docker**：`Client` 段完整，`Server` 段缺失并报
  `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`，
  原因是 Docker Desktop 未启动，详见 [`../index.md`](../index.md) 的「问题记录」。

## 复现方式

如需重新生成，在仓库根目录执行：

```bash
java --version
mvn.cmd --version      # Git Bash 下必须用 mvn.cmd；PowerShell/cmd 下可用 mvn
git --version
docker version
docker compose version
git log --oneline --graph
```

> **关于生成方式**：除 ① 为浏览器抓取的真实页面外，②–⑦ 的终端截图由命令的
> **真实输出**渲染为终端样式图片（本机执行结果原样保留，未做任何改写）。
> 若课程要求必须是屏幕截图，按上面的命令自行截取并覆盖同名文件即可，
> 文档中的引用无需改动。
