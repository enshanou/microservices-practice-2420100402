# Week 06 实际验证记录

验证日期：2026-11-01（Asia/Shanghai）。以下是实际命令结果，未制作或代截屏幕截图。

## 构建与测试

三个服务的本地 Maven 测试：user-service 3 项、course-service 6 项、task-service 8 项，合计 17 项，Failures=0、Errors=0、Skipped=0。Docker 多阶段构建再次运行各自测试并完成打包，三个镜像成功生成。

- [用户测试日志](evidence/user-tests.txt)
- [课程测试日志](evidence/course-tests.txt)
- [任务测试日志](evidence/task-tests.txt)
- [完整 Compose 构建日志](evidence/compose-build.txt)

## 编排和隔离

三个应用运行，三个 MySQL 实例均 healthy；本机端口分别为 18081、18082、18083。见 [Compose 状态](evidence/compose-ps.txt)。

以每个容器的业务账号执行 CURRENT_USER、SHOW DATABASES、SHOW TABLES，检查本库外键。各账号只看到自身业务库和系统元数据库，只拥有对应的业务表；没有跨库外键。见 [数据库实际查询](evidence/database-isolation.txt)。三个数据卷分别为 courselens-week06_user_data、courselens-week06_course_data、courselens-week06_task_data。

## HTTP 顺序流程和故障处理

分别运行 `bash scripts/smoke-test.sh` 和 `powershell -ExecutionPolicy Bypass -File .\docs\homework\week-06\Verify-Week06.ps1 -Persistence`，均输出 PASS all smoke checks。

1. 三个健康接口均返回 HTTP 200 / UP。
2. 创建用户 201，课程服务通过真实 HTTP 校验用户后创建课程 201，任务服务通过真实 HTTP 校验课程后创建任务 201。
3. 同课程重复创建活动任务返回 409；将原任务改为 RUNNING、SUCCEEDED 均返回 200，终态 activeCourseId 为 null。
4. 用户、课程、任务表相对每轮开始均增加 1 条。本次验证不会清空上轮演示记录。
5. 课程引用不存在用户返回 404，课程数量不变；任务引用不存在课程返回 404，任务数量不变。
6. 实际停止 course-service 后创建任务返回 503，任务数量不变；脚本恢复 course-service 后健康和原资源查询正常。
7. 执行 Compose down（保留卷）后 up -d，按原 ID 查询用户、课程和任务均返回 200，任务仍为 SUCCEEDED，持久化验证通过。

最初的容器重建持久化验证使用的 ID（对应完整 PowerShell 执行输出）：

- 用户：`7b47852a-54eb-4e5f-922d-a18863b14e48`
- 课程：`fec1ed35-1956-4aa2-ae14-5d0f61d9750b`
- 任务：`cc97a86b-bec4-46c4-8f9f-febae508f830`

原始请求、状态、返回数据与数量对比：

- [健康、成功及缺失依赖](evidence/core.txt)
- [依赖停机 503 和数据不变](evidence/unavailable.txt)
- [重建后原资源查询](evidence/recovered.txt)
- [Bash 完整执行输出](evidence/week06-smoke-bash.txt)
- [PowerShell 及持久化执行输出](evidence/week06-smoke-powershell.txt)

## 本人后续操作与提交前检查

本人再次运行测试并完成手动截图，测试截图仍为 3 / 6 / 8 项通过。最近一次本人验证的完整分段日志为 core.txt、unavailable.txt、recovered.txt，使用的资源 ID 为：

- 用户：`097987c9-8fe3-4fd8-b15a-ea4a32dd87f8`
- 课程：`3cd7e3f9-0c6c-43c3-b475-516e99d05a87`
- 任务：`02be0468-bc29-4e1e-8966-a20c31e7454d`

这轮三类数据从 2 条增加至 3 条；缺失用户/课程和课程服务不可用时均保持 3 条。不同轮次使用不同 UUID，历史完整执行日志与最新分段日志分别保留。

提交前重新检查 Compose 配置有效、三个数据库 healthy，并逐个请求本机 18081、18082、18083 健康接口，均为 HTTP 200 / UP，见 [健康复核](evidence/final-health.txt)。9 张本人截图已核对并嵌入本周 index.md；现有截图未显示健康接口的部分由完整日志补充。

## 提交状态

代码、配置、脚本、验证记录与 9 张本人截图已检查并纳入本次提交。作业系统仍需本人填写仓库完整链接、四项说明，并按截图目录的对应关系上传图片。
