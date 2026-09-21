# microservices-practice-2420100402

## 课程

微服务架构（Microservices Architecture）

## 仓库信息

| 项目 | 内容 |
| --- | --- |
| 姓名 |  |
| 学号 | 2420100402 |
| GitHub | [@enshanou](https://github.com/enshanou) |
| 仓库地址 | https://github.com/enshanou/microservices-practice-2420100402 |

> 姓名一栏待补充。

## 仓库用途

本仓库是《微服务架构》课程**唯一使用**的个人实践仓库，用于在整学期内持续沉淀课程的全部产出。
后续作业将在这个仓库中逐步完成需求分析、项目实现、测试、部署与文档整理，所有变更均通过 Git 提交记录留痕，保证过程可追踪、可复现。

## 目录结构

```text
.
├── README.md                        # 仓库说明：课程、姓名、学号、用途
├── docs/                            # 全部文档
│   └── homework/                    # 按周归档的作业
│       └── week-01/                 # 第 1 周作业
│           ├── index.md             # 环境检查 + 概念回答 + 问题记录
│           └── screenshots/         # 环境命令、仓库页面、提交记录截图
└── src/                             # 后续作业的源代码
```

## 作业进度

| 周次 | 主题 | 文档 | 状态 |
| --- | --- | --- | --- |
| week-01 | 开发环境与个人仓库 | [docs/homework/week-01/index.md](docs/homework/week-01/index.md) | 已完成 |

## 环境概览

| 工具 | 版本 | 状态 |
| --- | --- | --- |
| Java | 17.0.11 LTS | 正常 |
| Maven | 3.9.16 | 正常（需使用 `mvn.cmd`，详见 week-01 问题记录） |
| Git | 2.55.0.windows.3 | 正常 |
| Docker | Client 29.8.0 | 需启动 Docker Desktop 守护进程 |
| Docker Compose | v5.5.1 | 正常 |

## 提交规范

采用 Conventional Commits 风格，便于回溯每次作业的变更范围：

```text
<type>(<scope>): <描述>

feat     新增功能
fix      修复缺陷
docs     文档变更
chore    构建、配置、杂项
test     测试相关
refactor 重构
```

示例：`docs(week-01): 补充环境检查与概念回答`
