# CourseLens 服务通信

## 创建课程处理任务

用户提交已有课程的 `courseId`。`task-service` 管理任务，`course-service` 管理课程和归档状态。两个课程实例共享课程库，任务服务在自己的数据库中保存任务。

```mermaid
sequenceDiagram
    actor U as 调用者
    participant T as task-service
    participant L as OpenFeign + Spring Cloud LoadBalancer
    participant N as Nacos
    participant C as course-service 实例 1 或 2
    participant CD as 课程库
    participant TD as 任务库
    U->>T: POST /api/tasks (courseId)
    T->>L: 查询课程
    L->>N: 发现 course-service
    N-->>L: 可用实例
    L->>C: GET /api/courses/{id}
    C->>CD: 查询课程及归档状态
    alt 课程存在且未归档
        C-->>L: 200 课程 JSON + X-Course-Instance
        L-->>T: 返回课程和实例标识
        T->>TD: 写入 PENDING 任务
        T-->>U: 201 任务 JSON
    else 课程缺失或已归档
        C-->>L: 404
        L-->>T: 课程不存在
        T-->>U: 404 错误 JSON
    else 无实例、调用失败或超时
        L-->>T: 调用失败
        T-->>U: 503 错误 JSON
    end
```

| 角色 | 工程 | 接口 |
| --- | --- | --- |
| 调用方 | `services/task-service/` | `POST /api/tasks` 接收 `{"courseId":"..."}` |
| 被调用方 | `services/course-service/` | `GET /api/courses/{id}` 返回课程 JSON，响应头包含 `X-Course-Instance` |
| 结果查询 | `services/task-service/` | `GET /api/tasks/{id}` 返回已保存的任务 JSON |

OpenFeign 使用服务名 `course-service`。两个课程容器注册同名服务，Spring Cloud LoadBalancer 从发现到的实例中轮询选择。任务服务检查响应课程 ID，再写入任务。无有效课程 ID、JSON 无法解析或收到意外的上游 4xx 时返回 HTTP 502。依赖调用失败的分支都在写入任务前结束。

课程查询是只读请求。若客户端等待 `POST /api/tasks` 响应时超时，任务可能已写入，结果未知。可通过 `GET /api/tasks` 按 `courseId` 核对，再决定是否重试。
