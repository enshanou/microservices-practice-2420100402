# Week 04：CourseLens 业务建模、单体应用、持久化与验证

沿用个人项目“课览 CourseLens”和第 03 周 `monolith/` Spring Boot 工程，基础包名统一为 `com.zjgsu.oes`。本次提交是个人项目作业，课程表示导入的学习资料。四项作业共用一条真实可运行的业务流程与数据库，没有另建校园选课项目。

## 业务建模

核心流程为：**登记课程资料 → 创建处理任务 → 记录处理状态 → 查询处理历史**。

| 实体 | 作用 | 关系 |
| --- | --- | --- |
| Course | 课程归属标识、标题、资料引用、创建时间 | 一门课程关联零个或多个任务 |
| ProcessingTask | 一次处理尝试的状态、失败原因与时间 | course_id 外键指向唯一课程 |

核心业务规则：同一课程已有 PENDING 或 RUNNING 任务时，不允许重复创建。结束任务保留，允许再次创建；状态只能按 `PENDING → RUNNING → SUCCEEDED/FAILED` 更新，失败必须填写原因。`CourseProcessingService` 检查规则，数据库行锁和活动任务唯一键防止并发重复创建。

完整流程、实体关系和理由见 [项目选题简案](../../project-proposal.md)。资料引用目前是元数据，成功/失败由调用方报告。本周完成课程与任务管理基础，尚未执行视频转写或 AI 内容生成。

## 单体应用实现

### 实现思路

两类 JPA 模型、两个 Repository、一个 Service、两个业务 Controller 和请求/响应 DTO 都在同一个 Spring Boot 应用中。Controller 负责 HTTP 输入输出与参数校验；Service 管理流程、状态规则和事务；Repository 负责数据库访问。异常统一转换为 400、404 或 409。

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| POST | /api/courses | 登记课程，成功 201 |
| GET | /api/courses | 查询课程列表 |
| GET | /api/courses/{courseId} | 查询课程详情 |
| POST | /api/courses/{courseId}/tasks | 创建处理任务，成功 201 |
| GET | /api/courses/{courseId}/tasks | 查询课程处理历史 |
| GET | /api/tasks/{taskId} | 查询任务详情 |
| PATCH | /api/tasks/{taskId}/status | 更新状态，记录失败原因 |

原 `/api/hello` 和 `/actuator/health` 保留。新增 7 个核心业务接口，均使用 `/api/` 前缀。

核心代码：[Course](../../../monolith/src/main/java/com/zjgsu/oes/model/Course.java)、[ProcessingTask](../../../monolith/src/main/java/com/zjgsu/oes/model/ProcessingTask.java)、[Service](../../../monolith/src/main/java/com/zjgsu/oes/service/CourseProcessingService.java)、[课程 Controller](../../../monolith/src/main/java/com/zjgsu/oes/controller/CourseController.java)、[任务 Controller](../../../monolith/src/main/java/com/zjgsu/oes/controller/ProcessingTaskController.java)、[DTO](../../../monolith/src/main/java/com/zjgsu/oes/dto/CourseDtos.java)。

### 启动与接口验证

本机在 PowerShell 中执行：

```powershell
cd 'D:\study\GitHub\microservices-practice-2420100402\monolith'
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Start-Local.ps1
```

脚本准备独立 MySQL 并启动应用，默认 HTTP 8080。另开一个 PowerShell 窗口：

```powershell
cd 'D:\study\GitHub\microservices-practice-2420100402\monolith'
curl.exe -i http://localhost:8080/api/courses
curl.exe -i -X POST http://localhost:8080/api/courses -H 'Content-Type: application/json' --data-binary '@scripts/requests/create-course.json'
.\scripts\Verify-Api.ps1
```

本次实测临时使用 HTTP 18080：启动时加 `-Port 18080`，验证脚本加 `-BaseUrl http://localhost:18080`。创建课程、创建任务分别返回 201；查询和合法更新返回 200；空标题/失败原因返回 400；不存在记录返回 404；重复活动任务或非法状态转换返回 409。可复用脚本实测 **19 项 HTTP 检查全部通过**，成功与失败两次处理都保留。

实际请求与 JSON 见 [API 原始证据](evidence/api-verification.json)。[创建响应截图](<screenshots/创建响应.png>)、[查询响应截图](<screenshots/查询响应.png>)和[核心代码截图](screenshots/controller.png) 可提交本小题。

## 数据持久化

### 实现思路

运行接入 MySQL。`courses` 和 `processing_tasks` 两张业务表以 1:N 外键关联；UUID 主键、非空与长度约束、活动任务唯一约束及状态 CHECK 约束都在 [建表脚本](../../../monolith/scripts/schema.sql) 中定义。数据访问在 `repository` 包；请求 DTO 不直接绑定实体。

生产配置 `ddl-auto: validate`，应用重启只校验结构，不重建或清空表。[示例数据脚本](../../../monolith/scripts/demo-data.sql) 使用固定 UUID 和幂等插入，可以重复执行。[数据库文档](../../database.md) 说明字段、关系、约束、事务及连接配置；[README](../../../README.md) 提供新电脑安装、建库、账号和环境变量步骤。

### 命令与实际结果

```powershell
.\scripts\Initialize-LocalMySql.ps1
.\scripts\Start-Local.ps1
```

本机使用 MySQL 8.0.45，独立端口 13306，数据和私密凭据在仓库外 `D:\courselens-local-mysql`。未改动原有 3306 MySQL 服务。

实测 API 写入课程和待处理任务，SQL 读出相同 UUID。停止并重启应用后，原记录仍可查询。之后成功、失败两个处理记录均保存在 MySQL 中。并发两次创建返回 201、409，SQL 活动任务数为 1；元数据确认外键、唯一键和三个 CHECK 约束存在。

证据：[重启前 SQL](evidence/mysql-before-restart.txt)、[重启后 SQL](evidence/mysql-after-restart.txt)。[真实 MySQL 查询结果截图](<screenshots/真实 MySQL 查询结果.png>) 可提交本小题。

## 测试与验证

### 测试设计

| 测试类 | 数量 | 验证内容 |
| --- | ---: | --- |
| CourseLensApplicationTests | 1 | Spring Boot 上下文正常启动 |
| RepositoryTests | 5 | 课程与任务写入读取、关联、唯一约束、外键及结束后重试 |
| CourseProcessingServiceTests | 14 | 正常流程、重复任务、状态路径、无效输入、结束后重试及并发创建 |
| ApiTests | 11 | 真实 Controller/Service/Repository 的正常与错误 HTTP 响应、参数校验和原健康接口 |
| **总计** | **31** | **失败 0，错误 0，跳过 0** |

API 使用 MockMvc 发出 HTTP 测试请求，没有替换真实 Service 或 Repository。自动测试连接 H2，不需要运行 MySQL；每个 API/Service 用例自动执行 [test-data.sql](../../../monolith/src/test/resources/test-data.sql)，Repository 测试事务回滚。运行不会删除本地 MySQL 数据。

### 运行命令与结果

打开 PowerShell，在 `monolith/` 中执行：

```powershell
$env:JAVA_HOME = 'D:\study\jdk-25\jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = 'D:\study\maven-user-home'
$env:MAVEN_OPTS = '-Dmaven.repo.local=D:\study\maven-repository'
.\mvnw.cmd clean test
.\mvnw.cmd test
```

macOS/Linux 配置 JDK 25 后执行 `./mvnw clean test` 与 `./mvnw test`。其他 Windows 电脑调整上述安装路径。

2026-10-12 实测先清理构建并测试，再重复执行测试，两次均为：

```text
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

原始用例、汇总及两次构建日志见 [测试结果](evidence/test-results.txt)、[首次测试日志](evidence/clean-test.log)、[重复测试日志](evidence/repeat-test.log)。[测试通过截图](screenshots/maven.png) 可提交本小题。

## 截图来源与提交对应

以下截图由本人在 IntelliJ IDEA、PowerShell、浏览器和 MySQL Workbench 中截取，原始证据保存在 evidence/。作业系统每小题允许上传 1–5 张；优先使用下表中的截图。

| 作业小题 | 建议上传截图（点击可查看） |
| --- | --- |
| 单体应用实现 | [Controller 代码](screenshots/controller.png)、[Service 业务规则](screenshots/server.png)、[创建接口响应](<screenshots/创建响应.png>)、[查询接口响应](<screenshots/查询响应.png>) |
| 数据持久化 | [数据库设计](screenshots/database.png)、[Repository 代码](screenshots/repository.png)、[DTO 输入](screenshots/CourseDtos.png)、[真实 MySQL 两表查询结果](<screenshots/真实 MySQL 查询结果.png>) |
| 测试与验证 | [Repository 测试](screenshots/repositorytests.png)、[Service 测试](screenshots/servicetests.png)、[API 测试](screenshots/apitests.png)、[31 项测试通过](screenshots/maven.png) |

其他本人截图见 [幂等数据脚本](screenshots/demo-data.png)。

## 作业系统填写文字

GitHub 仓库完整链接：<https://github.com/enshanou/microservices-practice-2420100402>。推送本地修改后，再提交该链接。

**业务建模（超过 50 字）：**本周围绕课览 CourseLens 的课程资料处理流程，完成 Course 与 ProcessingTask 两类实体和一对多关系建模，实现课程登记、任务创建、状态更新及历史查询。Service 限制同一课程只能有一条未结束任务，并结合数据库行锁和唯一约束处理并发重复请求。任务结束后允许再次处理，历史记录保留；相关自动测试和真实 MySQL 验证通过。

**单体应用实现（超过 20 字）：**在同一个 Spring Boot 应用中完成课程和处理任务模型、Service、Controller 及七个 REST 接口，通过 DTO 校验输入。实测创建和查询成功，重复任务返回 409，无效输入返回 400，不存在记录返回 404，十九项 HTTP 检查全部通过。

**数据持久化（超过 20 字）：**接入本地 MySQL，使用 Spring Data JPA Repository 保存课程和处理任务，建立主外键、活动任务唯一约束与状态检查，提供可重复数据脚本。实测 API 写入与 SQL 读取一致，应用重启后记录保留，并发提交只有一条活动任务。

**测试与验证（超过 20 字）：**编写 Repository 读写、Service 业务规则及 API 正常与错误响应测试，使用独立 H2 自动准备测试数据。连续执行 clean test 和 test，三十一项测试两次全部通过，失败、错误及跳过均为零。

## 手动推送 GitHub

在仓库根目录打开 PowerShell：

```powershell
cd 'D:\study\GitHub\microservices-practice-2420100402'
git status
git add README.md docs monolith
git diff --cached --stat
git commit -m "feat: complete week 04 CourseLens API persistence and tests"
git push origin main
```

先检查暂存摘要都是本次要提交的文件。数据库密码和实际数据目录均在仓库外，不随提交上传。提交后打开 GitHub 仓库确认 week-04 文档、源码和截图已显示。
