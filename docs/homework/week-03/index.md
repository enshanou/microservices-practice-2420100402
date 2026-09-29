# Week 03：Spring Boot 工程与运行验证

## 本周计划与范围

沿用第 02 周确定的“课览 CourseLens”项目。先建立 `monolith/` 单体工程，验证 Java 25、Spring Boot 4.0.x、Maven Wrapper、问候接口和健康检查；后续再实现课程、任务等业务模型。项目名称、目标用户、优先场景和两个计划中的核心模型见[项目选题简案](../../project-proposal.md)。

## 工程内容

| 项目 | 本周实现 |
| --- | --- |
| 工程目录 | `monolith/`，独立 Maven 工程 |
| Java / Spring Boot | Java 25 / Spring Boot 4.0.8 |
| Group / Package | `com.zjgs.oes`（区恩善姓名拼音首字母） |
| 依赖 | Spring Web MVC、Actuator、Spring Boot Test |
| 配置 | `src/main/resources/application.yml`，端口 8080 |
| 验证接口 | `GET /api/hello`、`GET /actuator/health` |
| 自动测试 | `@SpringBootTest` 的 `contextLoads` |

## 运行与验证记录

在 `monolith/` 目录，使用 Java 25 执行（本机临时切换命令见根目录 README）：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

本机验证结果（2026-09-29，包名更正后复验）：

| 检查 | 实际结果 |
| --- | --- |
| `mvnw.cmd -version` | Maven 3.9.16，Java 25.0.4.1 |
| 启动测试 | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |
| 应用启动 | Tomcat 在 `8080` 端口启动 |
| `GET http://localhost:8080/api/hello` | HTTP 200，`CourseLens is running` |
| `GET http://localhost:8080/actuator/health` | HTTP 200，包含 `"status":"UP"` |

运行与测试截图：

- [启动测试结果](screenshots/context-loads.png)
- [问候接口响应](screenshots/hello-api.png)
- [健康检查响应](screenshots/health.png)

## 本周完成与后续

已提交项目选题简案、Spring Boot 工程、Maven Wrapper、启动测试、运行说明与验证证据。课程导入、异步任务、AI 笔记、数据库和业务接口仍处于后续阶段，本周没有实现这些能力。
