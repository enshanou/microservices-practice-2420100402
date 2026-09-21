# 截图清单

本目录用于存放 week-01 作业的证据截图。请按下表逐项截图，并把文件按**指定文件名**放在本目录下。

## 需要准备的截图

| # | 文件名 | 截图内容 | 操作要点 |
| --- | --- | --- | --- |
| ① | `01-github-repo.png` | GitHub 仓库页面 | 打开仓库首页，需包含仓库全名、**Public** 标识、分支 `main`、文件列表、Latest commit 时间线 |
| ② | `02-git-log-graph.png` | 提交记录 | 仓库根目录执行 `git log --oneline --graph`，连同命令行一起截 |
| ③ | `03-java-version.png` | Java 版本 | 执行 `java --version` |
| ④ | `04-maven-version.png` | Maven 版本 | 执行 `mvn --version` |
| ⑤ | `05-git-version.png` | Git 版本 | 执行 `git --version` |
| ⑥ | `06-docker-version.png` | Docker 版本 | 执行 `docker version` |
| ⑦ | `07-docker-compose-version.png` | Docker Compose 版本 | 执行 `docker compose version` |

## 建议的截图方式

**方式一：命令行截图（推荐）**

在 **PowerShell** 或 **Windows Terminal** 中打开仓库目录，依次执行五条版本命令，
让五条命令的输出留在同一个窗口里，然后截取整个窗口——这样版本信息与系统环境一目了然。

```powershell
cd D:\AAA学习\GitHub\microservices-practice-2420100402
java --version
mvn --version
git --version
docker version
docker compose version
git log --oneline --graph
```

> **注意**：在 Git Bash 中请使用 `mvn.cmd --version` 而不是 `mvn --version`，
> 否则会报 `ClassNotFoundException`。原因见 [`../index.md`](../index.md) 的「问题记录」。
> 若使用 PowerShell 或 cmd，直接执行 `mvn --version` 即可。

**方式二：单条命令分别截图**

如果希望每条命令一张图，按上表逐个执行并截图，保存为对应文件名。

## 截图要求

- **可读性**：文字清晰，不要过度缩放；版本号必须能看清。
- **完整性**：命令行本身要出现在截图里，只截输出会让人分不清是执行的哪条命令。
- **真实性**：必须是本机真实执行结果，不要使用示意图或他人截图。
- **命名**：严格使用上表指定的文件名，`submission.md` 中的图片链接依赖这些名字。

## 与文档的对应关系

截图放入本目录后，打开 [`../submission.md`](../submission.md)，
把各 **【截图位置】** 标记处被注释掉的图片语法取消注释，并删除对应的引用块说明即可。

例如把：

```markdown
<!-- ![GitHub 仓库页面](screenshots/01-github-repo.png) -->
```

改成：

```markdown
![GitHub 仓库页面](screenshots/01-github-repo.png)
```
