# 课览业务工作台

原生 HTML/CSS/JavaScript 页面，Node 标准库提供静态资源与同源 API 代理，无第三方 npm 依赖。页面连接三个现有业务服务，不生成示例数据。

## 启动

在仓库根目录 PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1
```

打开 http://127.0.0.1:18090/ 。脚本先启动 Docker/Nacos，等待就绪后启动业务服务，最后启动工作台。第一次使用新环境须先按照根 README 初始化 Nacos 密码；此脚本不会代替管理员初始化。

已有后端运行时可以使用 `-SkipBackend`。或者在 frontend 目录运行 `node server.mjs`。需要 Node.js 18+；当前机器的启动脚本也能查找现有本机 Node 运行时。

## 如何操作

1. 右上角选用户，或点击 ＋ 创建演示用户（不是账户注册/登录）。
2. 点击“新建课程”，填写名称及资料引用。保存后课程出现在卡片列表。
3. 点击“提交任务”。后端校验课程成功后保存 PENDING 任务，页面显示“待处理”。
4. 点击“查看记录”或左侧“任务记录”，按状态筛选、查看失败原因和任务详情。
5. 支持课程搜索、活动任务筛选、手动刷新，以及页面可见且未打开表单时每 10 秒刷新。

本地工作台通过 deploy/compose.legacy-local.yaml 访问三个业务服务，并使用工作台会话管理访问。第 9 周的 JWT 登录、角色权限与统一网关入口见 [安全说明](../docs/security.md)。统计来自服务端数据，课程按 ownerId 归属筛选。

第 2 步已接入字幕笔记。在课程卡片点击「上传字幕」，保存 UTF-8 的 SRT、VTT 或 TXT 后点击「生成笔记」。后台执行模型请求，任务记录完成后显示「查看笔记」。对尚未上传字幕的课程，「提交任务」沿用原有手动流程。参见 [字幕操作与验收](../docs/workbench/subtitle-notes.md)。

## 视频与思维导图

在课程卡片点击「上传视频」。任务完成后在笔记弹窗中切换笔记、导图与转写原文。支持最长 60 分钟、最大 1 GB 的 MP4、MOV、WebM 和 MKV。演示课程、样例、实现路径和实际结果见 [视频演示与验收](../docs/workbench/video-mind-map.md)。

## 请求如何进入已有 Java 代码

```text
页面点击“新建课程”
  → app.js 中 course-form 的 submit
  → POST /api/courses（同源）
  → server.mjs 将请求转发给 18082
  → CourseController.create → CourseService.create
  → 经 Nacos 发现 user-service 并校验用户
  → 保存课程到原有 MySQL
```

提交任务同理：POST /api/tasks → 18083 → TaskController → TaskService → DiscoveryClient 找课程 → 保存任务。前端代理只负责让浏览器同源访问现有 API，不替代业务服务间的 Nacos 发现。

默认代理目标：users 18081、courses 18082、tasks 18083。仅工作台代理可通过 WORKBENCH_USER_URL、WORKBENCH_COURSE_URL、WORKBENCH_TASK_URL 覆盖目标；这与微服务内部的 dependency.service-name 无关。工作台只监听 127.0.0.1，不把数据库或凭据发给浏览器。

## 测试

在 frontend 目录执行 `node --test`。代理测试验证真实 HTTP 转发、UTF-8 内容、原始 201/404/409 状态、健康接口、离线 503、请求大小与静态文件边界。UI 联调另行检查真实服务的创建、筛选和故障响应。

后台进程日志为 frontend/workbench.log、frontend/workbench-error.log。Node 在终端直接运行时按 Ctrl+C 停止。使用启动脚本时可查看 frontend/.workbench.pid；停止前核对该 PID 当前仍是此工作台的 node server.mjs 进程，避免停止复用该 PID 的其他程序。
