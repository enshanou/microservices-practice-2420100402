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

在 `monolith/` 目录，使用 Java 25 执行（终端若仍保留旧的 `JAVA_HOME`，修正方法见根目录 README）：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

本机验证结果（2026-09-29，使用 `com.zjgs.oes` 包名复验）：

| 检查 | 实际结果 |
| --- | --- |
| `mvnw.cmd -version` | Maven 3.9.16，Java 25.0.4.1 |
| 启动测试 | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |
| 应用启动 | Tomcat 在 `8080` 端口启动 |
| `GET http://localhost:8080/api/hello` | HTTP 200，`CourseLens is running` |
| `GET http://localhost:8080/actuator/health` | HTTP 200，包含 `"status":"UP"` |

运行与测试证据：

| 内容 | 截图与说明 |
| --- | --- |
| 配置 | [application.yml 配置截图](screenshots/application_yml.png)；截图显示的是 `target/classes` 中的构建副本，实际编辑的源文件是 `monolith/src/main/resources/application.yml` |
| 接口代码与启动 | [HelloController 与 IDEA 启动日志](screenshots/HelloController及启动日志.png)，日志显示应用在 8080 端口启动 |
| 接口响应 | [GET /api/hello](screenshots/hello-api.png)，返回 `CourseLens is running` |
| 健康检查 | [GET /actuator/health](screenshots/health.png)，返回 `UP` |
| 启动测试 | [执行 `mvnw.cmd test` 的命令](screenshots/mvnw%20test_1.png)与[测试结果](screenshots/mvnw%20test_2.png)，1 个测试通过且 `BUILD SUCCESS` |

## 本周完成与后续

已提交项目选题简案、Spring Boot 工程、Maven Wrapper、启动测试、运行说明与验证证据。课程导入、异步任务、AI 笔记、数据库和业务接口仍处于后续阶段，本周没有实现这些能力。
