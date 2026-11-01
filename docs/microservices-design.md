# CourseLens 服务拆分设计

第六周沿用课览的课程导入与处理任务主题，拆出用户、课程、处理任务三个独立 Spring Boot 应用。保留 `monolith/` 作为前几周的历史实现；Week 06 运行根目录 Compose 中的三个新服务，不再启动单体。

## 从单体到服务

| 原单体模块 | 新服务 | 职责 | 本机端口 / 容器端口 | 独立数据库 / 账号 | 拥有的数据表 |
| --- | --- | --- | --- | --- | --- |
| Course.ownerId 字段，原来没有独立用户模块 | user-service（新增最小用户管理） | 创建、查询用户，供课程服务验证用户存在 | 18081 / 8080 | courselens_user / user_app | users |
| CourseController、CourseRepository、CourseProcessingService 的课程方法 | course-service | 创建、查询课程；创建前调用用户 API | 18082 / 8080 | courselens_course / course_app | courses |
| ProcessingTaskController、ProcessingTaskRepository、CourseProcessingService 的任务方法 | task-service | 创建、查询处理任务，维护状态流转；创建前调用课程 API | 18083 / 8080 | courselens_task / task_app | processing_tasks |

每个服务独立 `pom.xml`、Maven Wrapper、启动类、Controller、Service、Repository、配置、SQL、Dockerfile 和集成测试。沿用原有 Spring Boot 4.0.8、Java 25。API 使用独立 JSON 记录，不共享 Java 实体包或数据库连接。

## 数据归属和调用

`courses.owner_id` 只存用户 UUID；`processing_tasks.course_id` 和 `active_course_id` 只存课程 UUID。拆除原先 `ProcessingTask.course` 的 ManyToOne 和 `fk_tasks_course` 外键，跨服务只保存 ID。业务账号只能访问各自 MySQL 实例；应用不会注入其他服务的 Repository。三个实例、账号、数据卷彼此独立。

课程创建先通过 RestTemplate 执行 `GET user-service:8080/api/users/{id}`，成功后才写课程库。任务创建先执行 `GET course-service:8080/api/courses/{id}`，成功后才写任务库。地址在各自 application.yml 配置，可由 USER_SERVICE_URL / COURSE_SERVICE_URL 覆盖。连接超时默认 1500ms、读取超时 2000ms，可分别由 CONNECT_TIMEOUT_MS / READ_TIMEOUT_MS 覆盖。

依赖返回 404 时，下游返回 404；连接拒绝、读取超时或其他远端错误返回 503；远端空响应返回 502；均不会继续写本地。无效请求返回 400。任务活动槽位唯一约束使重复活动任务返回 409，终态释放槽位。任务更新通过本地事务及行锁保证状态流转。

```mermaid
flowchart LR
    Client[客户端] --> U[user-service :18081]
    Client --> C[course-service :18082]
    Client --> T[task-service :18083]
    C -->|RestTemplate GET 用户| U
    T -->|RestTemplate GET 课程| C
    U --> UD[(user-db / users)]
    C --> CD[(course-db / courses)]
    T --> TD[(task-db / processing_tasks)]
```

## 业务流程与一致性窗口

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant U as 用户服务
    participant C as 课程服务
    participant T as 任务服务
    Client->>U: POST /api/users
    U-->>Client: 201 用户ID
    Client->>C: POST /api/courses (ownerId)
    C->>U: GET /api/users/{ownerId}
    U-->>C: 200
    C-->>Client: 201 课程ID
    Client->>T: POST /api/tasks (courseId)
    T->>C: GET /api/courses/{courseId}
    C-->>T: 200
    T-->>Client: 201 PENDING
    Client->>T: PATCH 状态 RUNNING
    Client->>T: PATCH 状态 SUCCEEDED
```

顺序验证覆盖用户创建、课程导入、任务提交与状态查询。状态更新只记录调用方报告的处理结果，本周没有实现 AI 推理或转写执行器。

远端校验成功后，本地写入仍可能因数据库故障或重复活动任务而失败。这就是“远端成功、本地失败”窗口：远端查询的成功不能保证本地提交成功。当前远端操作只有读取，不需要撤销远端写操作；调用方必须检查 201/409/5xx，不能把远端 200 当作整个流程成功。前面已成功创建的用户、课程不会因后续失败被自动删除。未来若加入远端扣费等写操作，需要额外设计幂等与补偿，本周不实现跨服务原子事务、自动补偿或消息机制。

参考：[Spring Framework REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)。本作业按题目要求使用 RestTemplate。
