# CourseLens 网关与认证

## 认证流程

1. 客户端向网关发送 `POST /api/auth/login`，JSON 字段为 `username` 和 `password`。网关将登录请求转发到 `user-service`。
2. 用户服务以独立随机盐、PBKDF2-HMAC-SHA256 和 600,000 次迭代校验密码。验证通过后签发 HS256 JWT，响应包含 `accessToken`、`tokenType`、`expiresIn` 和 `role`。密码错误返回 HTTP 401。
3. 客户端以 `Authorization: Bearer <JWT>` 访问业务接口。网关检查签名、签发者、受众、身份、角色、签发时间和有效期。缺少、过期或校验失败的 JWT 返回 HTTP 401，角色不足返回 HTTP 403。
4. 网关删除客户端送来的 `X-Auth-*` 身份头，并从已验签的 JWT 重建 `X-Auth-User` 和 `X-Auth-Role`。`GET /api/auth/me` 由用户服务回显收到的账号和角色，可用来验证身份头。网关会在转发前删除原始 Bearer 头。

## 角色与路由

| 路径和方法 | 目标 | 角色 |
| --- | --- | --- |
| `GET /api/health` | 网关自身 | 公开 |
| `POST /api/auth/login` | `http://user-service:8080` | 公开 |
| `GET /api/auth/me` | `http://user-service:8080` | `USER`、`ADMIN` |
| `/api/users` 及其子路径 | `http://user-service:8080` | `ADMIN` |
| `DELETE /api/courses/*` | `http://course-service:8080` | `ADMIN` |
| `/api/courses` 及其余课程请求 | `http://course-service:8080` | `USER`、`ADMIN` |
| `/api/tasks` 及其子路径 | `http://task-service:8080` | `USER`、`ADMIN` |

账号 `week09-user` 对应 `USER`，`week09-admin` 对应 `ADMIN`。课程归档由课程服务现有的 DELETE 接口实现。业务用户表用于记录课览用户数据，与这两个本地测试登录账号分别管理。JWT 有效期为 15 分钟，过期后重新登录。初始化脚本只将密码哈希和新生成的密钥写入被 Git 忽略的 `.env`。

## 下游访问边界

第 9 周的根目录 Compose 只发布网关的业务端口，三个下游及课程副本只通过 Docker 网络的服务名访问。Nacos 的本地管理端口沿用前几周配置。来自宿主机的普通业务请求须通过网关，客户端送来的身份头会被清除。下游信任该 Compose 网络内的调用方，Docker 管理员和能加入该网络的其他容器属于信任边界内。

`deploy/compose.legacy-local.yaml` 给旧版工作台和第 7、8 周的直连脚本使用，会开启本机的旧版直连端口。第 9 周验证时只加载根目录 `compose.yaml`，用 `docker compose ps` 确认 user、course、task 只有容器端口或空白，网关一行显示 `127.0.0.1:8080->8080/tcp`。
