# CourseLens 工作台升级与验收

## 在 Windows 启动

打开 Docker Desktop，再在 Windows PowerShell 运行：

```powershell
cd 'D:\study\GitHub\microservices-practice-2420100402'
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1 -Build -RestartFrontend -RestartWorker
Start-Process 'http://127.0.0.1:18090/'
```

看到三个业务端口依次显示 Ready，最后出现 CourseLens workbench is ready。第一次升级用 -Build 重新构建业务镜像。任务服务启动时会为旧库增加扩展表。打开课程资料，已有课程、视频和笔记可继续使用。

## 工作台操作

| 功能 | 怎么操作 | 成功时看到什么 |
| --- | --- | --- |
| 术语和详略 | 上传视频、导入链接、从字幕生成，或在学习页点「重新校对并生成」。选详略、模型，按需填术语，例如「纳科斯 → Nacos」 | 新任务出现在任务记录。完成后打开「转写校对稿」，术语会结合上下文校对，证据不足的段落标有「待回看」，原始识别可切换查看 |
| 新平台 | 在课程资料点「链接导入」，粘贴公开 YouTube 或 Vimeo 单视频。填写课程标题后提交。B 站仍可选单集或合集 | 任务记录先显示等待获取链接视频，完成后学习页能播放已保存的视频并显示笔记 |
| 重试和恢复 | 在失败任务详情点「重试」。合集结束后，对失败分集点「重试此集」 | 产生新的处理任务。合集重试会更新对应分集，其他已完成分集继续可用 |
| 桌宠助手 | 在「课程笔记」里选一段文字，点「问问桌宠」，选择「换个说法」或「举个例子」 | 对话框先显示选段，然后展示模型回答。发送范围是课程标题及所选文字 |
| 导出 | 先保存修改，再点「导出笔记」，选择 Markdown 或纯文本 | 下载文件包含已保存的摘要、章节、要点和时间位置。手帐颜色和手绘式标注保留在工作台内 |
| 风格学习 | 保存有内容变动的正文，例如拆分主题、增加例子或解释原因。之后生成笔记时勾选「参考我保存过的笔记修订」 | 后续生成会参考该用户最近最多 12 份有正文修订的笔记所归纳的偏好。显式详略仍优先 |

个人风格由章节数、文本长度和解释表达的增减推断。系统保存的是每份笔记最近一次有效修改的趋势。关闭生成选项可跳过风格参考。单纯改变颜色、标注或导图不会产生风格样本。

YouTube 和 Vimeo 的预览校验链接格式，视频时长在下载时检查。Vimeo 使用公开播放器入口，需要视频允许公开嵌入。链接需要公开、可访问且含可转写音轨，最长 60 分钟。网站的网络限制和下载器变化会影响真实导入。链接转写继续使用本机 Python 环境的 yt-dlp、FFmpeg 和 Whisper。 [yt-dlp 支持站点](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)

使用 YouTube 时，在本机转写使用的 Python 环境安装带 EJS 依赖的 yt-dlp。下面的命令把依赖装到本课程仓库忽略的目录。确保 Node.js 22 或更新版本在 PATH 中。B 站和 Vimeo 沿用现有下载环境。 [yt-dlp EJS 配置](https://github.com/yt-dlp/yt-dlp/wiki/EJS)

```powershell
$videoPython = Read-Host '粘贴 .env 中 VIDEO_PYTHON 对应的 python.exe 完整路径'
& $videoPython -m pip install --target .\scripts\.video-python --upgrade 'yt-dlp[default]'
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1 -SkipBackend -RestartWorker
```

若当前 Python 提示找不到 pip，可以用另一套装有 pip 的 Python 执行安装命令，保留 --target 所指定的课程目录。

## 启用指定模型

保留 .env 中原有的 NOTES_API_BASE_URL、NOTES_API_KEY 和 NOTES_MODEL 可以继续使用原配置。也可以从 [.env.example](../../.env.example) 复制某一家对应的变量名到本机 .env，填可用的 API 密钥和准确的模型 ID。

| 选项 | 变量前缀 | API 地址 |
| --- | --- | --- |
| GPT | NOTES_GPT_ | 默认 https://api.openai.com/v1 |
| DeepSeek | NOTES_DEEPSEEK_ | 默认 https://api.deepseek.com |
| Claude | NOTES_CLAUDE_ | 默认 https://api.anthropic.com/v1 |
| Kimi | NOTES_KIMI_ | 填写账号对应站点提供的 OpenAI 兼容 API 基址 |

每组用 API_KEY 和 MODEL，按需加 API_BASE_URL。重启任务服务后，已配置的选项会出现在生成对话框里。GPT、DeepSeek 和 Kimi 使用 Chat Completions 接口，Claude 使用 Messages 接口。 [OpenAI API](https://developers.openai.com/api/reference/go/resources/chat/subresources/completions/methods/create) · [DeepSeek API](https://api-docs.deepseek.com/api/create-chat-completion/) · [Claude API](https://platform.claude.com/docs/en/api/messages)

```powershell
docker compose up -d task-service
(Invoke-RestMethod 'http://127.0.0.1:18083/api/tasks/notes/config').providers | Format-Table
```

## 启用登录和 Notion

本机演示默认可继续切换演示用户。需要账号隔离时，先把登录账号绑定到已有的课程用户：

```powershell
Invoke-RestMethod 'http://127.0.0.1:18081/api/users' | Format-Table id, displayName
$userId = Read-Host '粘贴要绑定的用户 id'
powershell -ExecutionPolicy Bypass -File .\scripts\Set-WorkbenchAccount.ps1 -Username student -UserId $userId
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1 -SkipBackend -RestartFrontend
```

为账号输入至少 12 字符的密码。看到 CourseLens login configured for student 后，刷新工作台应弹出登录框。输入 student 和密码后只能查看该账号名下的课程、笔记、任务和视频。每增加一个账号就执行一次设置脚本。Node 重启会清除会话。

需要 Notion 时，创建具有插入内容权限的内部连接，在目标 Notion 页面上添加该连接。在本机的 frontend/.workbench-settings.json 配置目标页面 ID 和 token。以下是字段示意。已有账号配置时，在对应的 accounts.student 对象里加 notion 字段，保留原来的盐和密码哈希。本机演示模式使用根级 notion 字段。

```json
"notion": {
  "pageId": "填目标父页面的ID",
  "token": "填内部连接的密钥"
}
```

重启网页服务后，学习页的「导出笔记」会出现「发送到 Notion」。成功时可点「打开 Notion 笔记」。单次 API 写入超过 100 个内容块时，可下载 Markdown，在 Notion 中通过导入功能上传。 [Notion 创建页面](https://developers.notion.com/reference/post-page) · [Notion 请求限制](https://developers.notion.com/reference/request-limits)

## 用容器运行网页入口

网页容器使用同一套后端。先按上节创建至少一个登录账号，在 frontend/.workbench-settings.json 的根级加入 "publicOrigin": "http://127.0.0.1:18090"，然后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1 -Build -SkipFrontend
docker compose -f compose.yaml -f deploy/compose.workbench.yaml up -d workbench
docker compose -f compose.yaml -f deploy/compose.workbench.yaml ps workbench
```

先退出占用 18090 的本机 Node 网页服务。看到 workbench 容器状态为 healthy 后，通过 http://127.0.0.1:18090/ 登录。Windows 转写程序继续由启动脚本运行。对外提供演示时，将 HTTPS 反向代理指向网页入口，publicOrigin 写实际 HTTPS 地址。三个 Java API 端口保留在本机回环地址，作为管理和转写接口。

## 重试与重启验收

集成测试使用三集模拟合集，第一集制造过期租约，第二集模拟失败后重试，第三集先完成，最终检查三集均完成。B 站目录和模型回复来自本地测试服务。

```powershell
Push-Location .\services\task-service
.\mvnw.cmd test
Pop-Location
Push-Location .\frontend
npm test
Pop-Location
```

两组测试应显示 0 项失败。现场可以选一个短视频，在任务显示「处理中」时运行以下命令观察恢复。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Workbench.ps1 -SkipBackend -RestartWorker
```

转写工作进程突然退出后，旧租约最多约 6 分钟失效，任务会自动重新排队一次。笔记生成中断后约 3 分钟回收。自动恢复从当前分集或当前笔记任务开头重跑。已完成的分集仍可打开。若自动恢复后再次中断，可手动重试。

提交前确认本机的配置文件已被忽略：

```powershell
git status --short
git diff --check
git check-ignore .env frontend/.workbench-settings.json
```

最后一条应显示两个忽略路径。

### 本机验收记录

2026-11-09 使用现有 Docker MySQL 数据卷启动升级版。确认旧 Axure 首集笔记可读取、视频可分段播放、可导出 Markdown，YouTube 和 Vimeo 可校验单集链接。Chrome 验证了旧学习页的桌宠选段和三种详略控件。YouTube 公共样片已下载并通过 FFprobe 的音视频轨道校验。Vimeo 播放器能读取格式元数据，本次两个样片的下载分别受到 DRM 和 HTTP 401 限制。Notion 发送和新增模型的实际调用需要配置对应账号。
