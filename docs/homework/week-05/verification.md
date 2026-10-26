# Week 05 实际验证记录

验证日期：2026-10-26（北京时间）。以下结果来自本机实际执行，原始输出保存在 evidence 目录。本人截图已补齐，见 [截图索引](screenshots/README.md)。

## 镜像构建与启动

- 执行：仓库根目录 `docker compose up -d --build`。
- 构建上下文：`./monolith`。
- 构建镜像：`maven:3.9.16-eclipse-temurin-25`。
- 运行基础镜像：`eclipse-temurin:25.0.4.1_1-jre`。
- 最终镜像：`courselens:week05`，镜像 ID 前缀 `550679a8fff8`。
- 容器内 `mvn clean package`：`BUILD SUCCESS`，Tests run: 31, Failures: 0, Errors: 0, Skipped: 0。
- app：Up，`127.0.0.1:18080 -> 8080/tcp`。
- mysql：`mysql:8.4.11`，Up (healthy)。
- 原始记录：[完整构建输出](evidence/compose-build.txt)、[Compose 状态](evidence/compose-ps.txt)、[镜像列表](evidence/images.txt)。

首次尝试通用基础镜像标签时，Docker Hub 认证服务连接超时；改用本机已缓存的精确版本标签后构建成功。Dockerfile 保留上述精确标签，其他电脑首次构建仍需联网下载基础镜像和 Maven 依赖。

## 健康与个人业务 API

| 请求 | 实测结果 |
| --- | --- |
| `GET http://localhost:18080/actuator/health` | HTTP 200，`status: UP` |
| `POST http://localhost:18080/api/courses` | HTTP 201，返回课程 ID 和请求字段 |
| `GET http://localhost:18080/api/courses/f75de9e6-ed6e-43d6-9de4-a42299f5183c` | HTTP 200，返回创建的同一条课程 |

创建请求：

```json
{
  "ownerId": "2420100402",
  "title": "Week05 persistence 3c0d602e",
  "sourceReference": "imports/week05-demo.srt"
}
```

返回数据：

```json
{
  "id": "f75de9e6-ed6e-43d6-9de4-a42299f5183c",
  "ownerId": "2420100402",
  "title": "Week05 persistence 3c0d602e",
  "sourceReference": "imports/week05-demo.srt",
  "createdAt": "2026-10-26T15:16:04.521036Z"
}
```

此外执行上一周的 `monolith/scripts/Verify-Api.ps1 -BaseUrl http://localhost:18080`，19 项 HTTP 检查全部通过，覆盖课程创建/列表/详情、任务创建/查询/状态迁移/失败重试，以及 400、404、409 异常流程；任务历史保留两次处理记录。

原始记录：[业务接口验证](evidence/business-api-verification.txt)。

## 数据持久化

1. 创建上述课程并通过 GET 查询，保存 [重建前响应](evidence/course-before.json)。
2. 执行 `docker compose down`，实际删除 app、mysql 容器及共享网络，保留命名数据卷。
3. 执行 `docker compose up -d`，重新创建容器，MySQL 健康后启动 app。
4. 健康接口再次返回 HTTP 200 / UP。
5. 按原 ID 查询，返回 HTTP 200，保存 [重建后响应](evidence/course-after.json)。
6. 脚本逐项检查 id、ownerId、title、sourceReference、createdAt，全部相同，输出 PASS。

使用的命名卷为 `courselens-week05_mysql_data`，挂载到 MySQL 的 `/var/lib/mysql`。本次验证没有执行 `down -v`。

原始记录：[完整重建与前后查询过程](evidence/persistence-verification.txt)。

## 本人手工操作截图核对

9 张截图已核对，覆盖镜像构建、Compose 启动、健康检查、课程创建与查询、重建前后持久化验证。手工测试课程 ID 为 `222861c0-070f-42e4-8055-70f8c60ce542`，创建时间为 `2026-10-26T15:34:08.673849Z`，重建前后响应中的 ID 及主要字段完全一致。

此课程与上文脚本验证的 `f75de9e6-ed6e-43d6-9de4-a42299f5183c` 为两条不同测试记录，各自的创建、查询与重建对比均使用同一 ID。截图中的重复构建会重新生成镜像清单 ID，不影响相同镜像标签和应用功能。

截图见 [作业说明第 7 节](index.md#7-本人截图与验证结果)。根目录 `.env` 由 `.gitignore` 排除，不随本次作业上传。
