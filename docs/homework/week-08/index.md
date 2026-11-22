# Week 08 服务通信与负载均衡

公开仓库链接：[enshanou/microservices-practice-2420100402](https://github.com/enshanou/microservices-practice-2420100402)

## 选择一个跨服务业务流程

CourseLens 的课程处理任务关联现有、未归档的课程。客户端调用 `task-service` 的 `POST /api/tasks` 后，任务服务同步调用 `course-service` 的 `GET /api/courses/{id}`。课程校验通过后，任务服务写入 `PENDING` 任务并返回 HTTP 201 和任务 JSON。课程归档状态由课程服务管理，任务由任务服务写入自己的数据库。详见[调用链路图](../../service-communication.md)。

启动 Docker Desktop，沿用第七周的本地 `.env` 和 Nacos。首次部署可按[第七周说明](../week-07/index.md)完成 Nacos 初始化。在仓库根目录的 PowerShell 运行：

```powershell
docker compose -f compose.yaml -f deploy/compose.legacy-local.yaml up -d --no-build
docker compose ps
```

本机已生成验证用镜像，可直接使用上面的命令。换到没有镜像的机器或修改代码后，用 `docker compose -f compose.yaml -f deploy/compose.legacy-local.yaml up -d --build` 构建并启动。冷启动时可先用 `docker compose up -d nacos` 启动注册中心，等待 `http://127.0.0.1:18080/` 可访问。

在同一仓库根目录打开 Git Bash，运行：

```bash
bash scripts/e2e-test.sh
```

脚本创建测试用户和课程，再调用任务 API，并用任务 ID 回查已保存的 JSON。多次创建之间会取消上一条测试任务，释放活动任务槽位。每次运行使用新的测试课程。测试用户、课程和已取消的任务会留在本地数据库中。

## 使用 OpenFeign

任务服务通过 `@FeignClient(name = "course-service")` 同步查询课程。连接和读取超时默认分别为 1.5 秒和 2 秒。课程缺失返回 `404`，无实例、连接失败、超时或上游 5xx 返回 `503`，无效响应返回 `502`。错误 JSON 均包含 `error` 字段。

在 E2E 输出中查看 `PASS Feign task creation #1: HTTP 201` 和 `PASS missing course: HTTP 404`，分别对应任务创建成功和课程缺失。

## 负载均衡验证

根目录 `compose.yaml` 启动 `course-service` 和 `course-service-replica`。两个容器共用镜像、数据库和 Nacos 服务名，实例标识分别为 `course-1`、`course-2`。主实例保留本机端口 `18082`，副本通过 Compose 网络访问。

脚本对同一课程多次调用创建任务接口，并筛选该课程的 `task-service` 日志。日志中的 `instance=` 来自课程查询的响应头。同时观察到两个实例的响应才输出 `PASS load balancing`。

## 测试

在 PowerShell 中，从仓库根目录运行。下列路径对应本机的 JDK 25 和 Maven 缓存：

```powershell
$env:JAVA_HOME = 'D:\study\jdk-25\jdk-25.0.4.1+1'
$env:MAVEN_USER_HOME = 'D:\study\maven-user-home'
$env:MAVEN_OPTS = '-Dmaven.repo.local=D:\study\maven-repository'
Push-Location services/task-service
try { .\mvnw.cmd -B -ntp test } finally { Pop-Location }
```

`ApiTests` 用模拟服务发现和本地 HTTP 服务驱动真实 Feign 客户端，覆盖正常落库、课程缺失、无实例、上游错误、读超时和课程 ID 不匹配。测试共 19 项，失败和错误均为 0。

`bash scripts/e2e-test.sh` 停止两个课程容器后再次提交任务，断言 HTTP `503`、错误 JSON 及故障前后任务数相同。随后启动两个实例，检查恢复后返回 `201` 且任务数加一。断言失败会返回非零，停服期间中断时脚本会尝试重新启动课程实例。

客户端提交写请求后等待超时，任务可能已经提交，结果未知。可通过 `GET /api/tasks` 按 `courseId` 核对，再决定是否重试。测试中的 `503` 发生在 Feign 查询阶段，脚本检查该次失败没有写入。

## 本机实测结果

2026-11-11 从仓库根目录运行 E2E，脚本退出码为 0。同一课程的四次任务创建均返回 `201`，查询分别由 `course-1 → course-2 → course-1 → course-2` 响应。随机生成的不存在课程返回 `404`。停止两个课程实例后，提交任务返回 `503`，计数为 `before=4 after=4`。恢复后返回 `201`，该课程任务数增加至 5，输出 `PASS Week 08 E2E`。

课程镜像由 Compose 构建。任务镜像首次构建下载较慢，本机验证用 Maven Wrapper 打包通过测试的源码，再按任务服务 Dockerfile 的运行阶段生成镜像。所有验证请求均由实际 Compose 服务处理。

## 截图操作和上传对应关系

在 Windows 用 `Win+Shift+S` 框选。将 PNG 放入 `docs/homework/week-08/screenshots/`，并上传到对应小题。只需运行一遍 E2E，后续从同一份终端输出截取所需区域。

| 小题 | 文件名 | 操作和画面 |
| --- | --- | --- |
| 2 使用 OpenFeign | `01-feign-client.png` | 用编辑器打开 `services/task-service/src/main/java/com/zjgsu/oes/CourseClient.java`，截取文件名、`@FeignClient(name = "course-service")` 和 `@GetMapping("/api/courses/{id}")`。 |
| 2 使用 OpenFeign | `02-feign-success.png` | 运行 `bash scripts/e2e-test.sh`，截取 `Test courseId=` 和至少一条 `PASS Feign task creation ... HTTP 201`，让任务 JSON 中的 `courseId`、`status` 可读。 |
| 3 负载均衡验证 | `03-load-balancing.png` | 从同一输出截取 `Feign course-service courseId=...` 日志，画面同时包含同一课程的 `instance=course-1`、`instance=course-2` 和 `PASS load balancing`。 |
| 4 测试 | `04-task-tests.png` | 运行上面的 Maven 命令，截取末尾的 `Tests run`、`Failures: 0`、`Errors: 0` 和 `BUILD SUCCESS`。 |
| 4 测试 | `05-e2e-outage-recovery.png` | 从 E2E 输出尾部截取 `HTTP 404`、停服后的 `HTTP 503`、相等的 `before` / `after`、恢复后的 `HTTP 201` 和 `PASS Week 08 E2E`。可最大化窗口或缩小字号。 |

## 作业系统填报文字

1. **选择一个跨服务业务流程**
   我在 CourseLens 中实现了创建课程处理任务的跨服务流程。任务服务收到课程编号后，同步向课程服务查询该课程。课程存在且未归档才写入待处理任务，课程缺失或依赖不可用时返回错误。两个服务分别管理课程和任务数据，调用链路已记录在仓库文档中。
2. **使用 OpenFeign**
   任务服务通过 OpenFeign 以 course-service 服务名查询课程。用端到端脚本验证正常时返回 201 和任务 JSON，课程缺失时返回 404，依赖不可用时返回 503。
3. **负载均衡验证**
   Compose 启动两个同名注册的课程实例。端到端脚本对同一课程多次创建任务，检查任务服务日志中实际收到的 course-1、course-2 响应标识，验证请求访问到两个实例。
4. **测试**
   任务服务测试覆盖 Feign 成功、课程缺失、无实例、超时和无效响应。端到端脚本停掉两个课程实例，检查返回 503 且任务数不变，再验证恢复后可创建任务。写请求在客户端等待超时后，结果可能未知，需先查询再重试。

完成作业系统提交后，再将本周代码、文档和本人截图同步到 GitHub。
