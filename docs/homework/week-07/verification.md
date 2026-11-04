# Week 07 代码验证结果

验证日期：2026-11-02。使用本机 JDK 25.0.4.1、Maven Wrapper，在临时工作副本运行各服务的 `clean test`；测试不连接真实 Nacos 或 MySQL。

| 服务 | Tests run | Failures | Errors | 结果 |
| --- | --- | --- | --- | --- |
| user-service | 3 | 0 | 0 | BUILD SUCCESS |
| course-service | 8 | 0 | 0 | BUILD SUCCESS |
| task-service | 10 | 0 | 0 | BUILD SUCCESS |

共 21 项测试通过。业务调用测试通过手写 DiscoveryClient 和本地 HTTP 模拟验证成功、404、503、超时、无实例不写入与下一次操作重新发现；原任务活动槽位与状态流转测试继续通过。

另外检查了所有 POM/XML 与主/测试 YAML、Compose 七个服务和共享注册配置，`docker compose --env-file .env.example config --quiet` 通过；Git Bash `bash -n scripts/smoke-test.sh` 通过。使用本地模拟 HTTP 服务器验证烟测成功返回 0、模拟服务返回 503 时返回非零且不输出最终成功，详细记录见 script-contract-check.txt。

以上为 2026-11-02 代码与离线数据库测试。2026-11-03 本人实际部署截图和 2026-11-04 真实业务复核已另行归档，见 [Week 07 记录](index.md) 和 [真实 HTTP 验证日志](evidence/live-verification.txt)。复核确认正常创建、404、下线 503 无写入、恢复 201 且增加 1、烟测非零与恢复为 0。
