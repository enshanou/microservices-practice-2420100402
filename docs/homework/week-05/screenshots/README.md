# Week 05 截图索引

以下 9 张截图均为本人实际操作记录，已按老师要求核对，原图保留。

| 栏目 | 截图 | 内容 |
| --- | --- | --- |
| 1. Dockerfile 与镜像构建 | [01-image-build.png](01-image-build.png) | 多阶段构建完成、镜像名称、标签与大小 |
| 2. Compose 编排与启动 | [02-compose-running.png](02-compose-running.png) | 构建启动、app 运行、MySQL healthy、端口映射 |
| 2. Compose 编排与启动 | [03-logs.png](03-logs.png) | 服务状态、MySQL 8.4.11 连接、Tomcat 启动 |
| 3. 个人业务 API 验证 | [04-health.png](04-health.png) | 健康请求地址、HTTP 200、UP |
| 3. 个人业务 API 验证 | [05-course-create.png](05-course-create.png) | POST 地址、请求 JSON、HTTP 201 与课程 ID |
| 3. 个人业务 API 验证 | [06-course-query.png](06-course-query.png) | GET 请求、HTTP 200 与同一条课程 |
| 4. 数据持久化验证 | [07-before.png](07-before.png) | 重建前查询，HTTP 200 与原记录 |
| 4. 数据持久化验证 | [08-recreate.png](08-recreate.png) | down 删除容器、up -d 重建、服务恢复 |
| 4. 数据持久化验证 | [09-after.png](09-after.png) | 重建后查询原 ID，HTTP 200 与相同字段 |

手工验证课程 ID：`222861c0-070f-42e4-8055-70f8c60ce542`。

作业系统按栏目分别上传对应 1、2、3、3 张截图，均在每项 1～5 张的范围内。
