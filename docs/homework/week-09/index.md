# Week 09 API Gateway 与 JWT

公开仓库链接：[enshanou/microservices-practice-2420100402](https://github.com/enshanou/microservices-practice-2420100402)

## 1. API Gateway

`services/gateway-service` 是独立 Node.js 服务，使用根 Compose 中的三个服务名转发 `/api/users`、`/api/courses` 和 `/api/tasks`，保留请求方法、请求体和查询参数。`GET /api/health` 由网关返回 `{"status":"UP"}`。沿用前几周的 Nacos、数据库及命名卷。

已配置第 7 周环境的机器，在仓库根目录 PowerShell 运行：

```powershell
node scripts/init-week09-auth.mjs
docker compose up -d nacos
# 等待 http://127.0.0.1:18080/ 可打开，首次部署参照 Week 07 初始化 Nacos。
docker compose up -d --build
docker compose ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}"
curl.exe -i http://127.0.0.1:8080/api/health
```

网关健康检查预期返回 HTTP 200 和 `{"status":"UP"}`。业务路由通过第 4 节脚本分别验证，预期均为 HTTP 200。若旧工作台正在运行，关闭本地工作台和 worker 后再用这里的 Compose 启动。

本机已有本周验证用镜像时，可以用 `docker compose up -d --no-build` 复现。改动源码后使用上面的构建命令。

## 2. 认证服务

用户服务接收 `POST /api/auth/login`，成功后签发 15 分钟有效的 HS256 JWT；密码错误返回 HTTP 401。`week09-user` 为 `USER`，`week09-admin` 为 `ADMIN`。本地初始化脚本为两个密码分别生成盐，用 PBKDF2-HMAC-SHA256 派生哈希。初始化时保存终端给出的密码，`.env` 中保存哈希与签名密钥。忘记密码可执行 `node scripts/init-week09-auth.mjs --rotate`，再运行 `docker compose up -d` 重建使用新配置的容器。

运行第 4 节脚本，查看两种角色登录的 HTTP 200、角色与有效期的断言，以及错误密码的 HTTP 401。脚本只显示状态与角色。

## 3. 网关鉴权

网关验证 Bearer JWT，验证失败返回 HTTP 401。课程和任务允许两种角色访问，用户管理以及课程归档只允许 `ADMIN`，普通角色访问返回 HTTP 403。网关清除客户端的 `X-Auth-*`，用验签得到的账号和角色写入转发请求。业务容器不发布宿主机端口。详见 [认证流程、权限与路由](../../security.md)。

运行第 4 节脚本，检查无 Token、无效签名和伪造身份的拒绝结果。已登录的普通用户即使携带伪造管理员头，`/api/auth/me` 仍应由下游返回 `week09-user / USER`，访问用户管理应返回 HTTP 403。

## 4. 测试脚本

服务启动后，在同一个仓库根目录打开 Git Bash：

```bash
bash scripts/auth-test.sh
echo "exit=$?"
```

按提示分别粘贴已生成的测试密码，输入时终端保持静默。脚本发起真实 HTTP 请求，检查网关健康、三条业务路由、登录与签发、验签、身份头覆盖和角色授权。每条断言显示 `PASS` 或 `FAIL`，全通过时最后显示 `PASS Week 09 auth tests` 和 `exit=0`。断言失败退出非零。脚本不创建测试业务数据。

## 本机验证记录

2026-11-18 用户服务 Maven 测试通过 3 项。在独立 Compose 项目中运行网关、用户、课程、任务和 MySQL 容器，使用 19080 作为临时验证端口并关闭 Nacos 注册。任务服务复用本机已有镜像。脚本全通过并退出 0，使用错误的测试密码重跑时退出 1。`docker compose ps` 显示网关为 healthy，四个业务容器（含课程副本）的端口均只开放在容器网络内。

2026-11-22 在本机根目录 Compose 环境重新核对，Nacos 注册已启用。用户、课程、任务的 Maven 测试分别通过 3、9、19 项，工作台 Node 测试通过 10 项。完整认证脚本通过并退出 0，覆盖两种角色登录、错误密码、无效签名、伪造身份头与越权拒绝。用户服务采用 `restart: on-failure`，启动阶段注册失败时自动重试。空密码输入会重新提示。

辅助编排已移入 `deploy/`。根配置、历史单体配置、旧版直连和工作台合并配置均通过 Compose 检查。运行证据见 [本机认证验证输出](evidence/auth-test.txt)。

## 截图与上传

先运行第 1 节和第 4 节命令。Windows 用 `Win+Shift+S` 框选，保存为 PNG 到本周的 `screenshots/`。作业系统每小题上传对应的 1–5 张图片。截图范围避开初始化输出中的密码、`.env` 内容和完整 Token。

| 作业小题 | 建议文件名 | 截图内容 |
| --- | --- | --- |
| 1. API Gateway | `01-gateway-health.png` | PowerShell 的 `docker compose ps` 和健康检查。保留 gateway 的 8080 映射、后端无宿主机映射、HTTP 200 和 `status: UP`。 |
| 1. API Gateway | `02-gateway-routes.png` | Git Bash 的三条 HTTP 200 路由结果，分别带 `course-service`、`task-service` 和 `user-service`。 |
| 2. 认证服务 | `03-auth-login.png` | 截取 `[Login and JWT]` 输出，保留错误密码 HTTP 401、两种角色登录 HTTP 200 和 900 秒有效期。 |
| 3. 网关鉴权 | `04-gateway-authorization.png` | 截取无效签名 HTTP 401、`downstream received username=week09-user role=USER`、伪造 ADMIN 头访问管理接口 HTTP 403 以及 ADMIN 访问成功。 |
| 4. 测试脚本 | `05-auth-test.png` | 最大化 Git Bash 后截取脚本输出，尽量包含匿名 401、登录 200、受保护接口 200、越权 403、最后一行 PASS 和 `exit=0`。 |

### 已归档截图

| 作业小题 | 已保存的截图 |
| --- | --- |
| 1. API Gateway | [01 网关状态与健康检查](screenshots/01-gateway-health.png)、[02 三条业务路由](screenshots/02-gateway-routes.png) |
| 2. 认证服务 | [03 两种角色登录和有效期](screenshots/03-auth-login.png) |
| 3. 网关鉴权 | [04 无效签名与身份覆盖](screenshots/04-gateway-authorization.png)、[02 普通用户越权拒绝和管理员成功](screenshots/02-gateway-routes.png) |
| 4. 测试脚本 | [05 完整测试及退出码](screenshots/05-auth-test.png) |

## 作业系统可填写文字

1. **API Gateway**
   我新增了监听本机 8080 的 API Gateway，按 Compose 服务名转发用户、课程和任务请求。通过网关健康检查和脚本访问三类业务接口，验证健康入口与三个路由均返回 HTTP 200。
2. **认证服务**
   用户服务提供登录接口，校验经过独立加盐的 PBKDF2 密码哈希，签发有效期为 15 分钟的 JWT。通过测试脚本验证普通用户与管理员登录返回 200，错误密码返回 401。
3. **网关鉴权**
   网关验证 JWT 签名与有效期，并用签名中的身份覆盖客户端请求头。通过脚本验证无 Token 和无效签名返回 401，普通用户伪造管理员头仍返回 403，管理员访问用户管理接口返回 200。
4. **测试脚本**
   我编写了可重复运行的 scripts/auth-test.sh，对匿名访问、登录、携带 Token 访问和低权限越权逐项检查 HTTP 状态。全部通过时输出 PASS 和退出码 0，断言失败时返回非零。
