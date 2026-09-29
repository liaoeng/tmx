# Linux 主机部署

本配置沿用 host 网络及 `/docker/...` 数据目录，MySQL 与 Redis 只监听宿主机回环地址。远程开发通过 SSH 隧道访问；Windows / macOS 不直接套用此网络方案。

## 准备环境

1. 安装 Docker Engine 和 Docker Compose V2，准备 JDK 21 或兼容版本。
2. 在本目录复制 `.env.example` 为 `.env`，填写三个密码：`TMX_MYSQL_ROOT_PASSWORD`、`TMX_DB_PASSWORD`、`TMX_REDIS_PASSWORD`。应用账号默认为 `tmx_dev`，不要改成 `root`。密码含 `$`、`#`、空格时用单引号包裹。
3. 为 `.env` 设置仅部署用户可读权限。该文件已被 Git 忽略；不要提交实际密码。
4. 在仓库根目录执行：

```bash
bash mvnw -Pdev,gen -pl tmx-admin -am verify
docker build -t tmx/tmx-server:6.0.0 tmx-admin
```

镜像里的默认构建环境可以是 dev；Compose 显式传入 `SPRING_PROFILES_ACTIVE=prod`，不会误连开发隧道端口。

## 首次启动空环境

确认 `/docker/mysql/data` 是新建的空目录。随后在本目录执行：

```bash
# 只检查配置，不打印展开后的密码
docker compose config --quiet
# --wait 等待账号认证、表读取及 Redis PING 检查通过
docker compose up -d --wait mysql redis
docker compose up -d tmx-server1
docker compose logs --tail 100 tmx-server1
curl --fail http://127.0.0.1:8080/auth/code
```

MySQL 官方镜像首次初始化会创建 `tmx` 数据库及应用账号，账号权限限制在 `tmx` 库；按文件名顺序执行基础 SQL 和工作流 SQL。初始化数据仍沿用现有模板内容，登录后应调整默认账号和权限。

两个初始化 SQL 均通过 `SET NAMES utf8mb4` 指定导入连接字符集。服务端字符集配置不能替代客户端连接设置，否则中文可能被错误解码，出现乱码或 `Data too long`。CI 会校验工作流表及导入后的中文昵称。

Redis 从仓库挂载配置文件，密码由启动参数注入，与应用读取的 `TMX_REDIS_PASSWORD` 一致。宿主机 `/docker/redis/data` 挂载到官方镜像的工作目录 `/data`，由入口脚本设置 Redis 用户的写入权限；已有持久化文件仍保存在原宿主机目录。应用容器已接收数据库用户名、密码、主机与端口，以及 Redis 主机、端口和密码。

`depends_on` 使用健康检查决定首次启动顺序，不代表运行期间依赖故障会自动恢复所有业务请求。应用进程异常退出后由重启策略重新拉起。[Docker 启动顺序说明](https://docs.docker.com/compose/how-tos/startup-order/)

## 接入已有环境

**不要清空数据目录，也不要重新执行全量 SQL。** 已有 MySQL 数据目录会跳过初始化；更改 `.env` 不会自动修改现有数据库账号或密码。

先用管理账号确认应用账号已存在，并且有 `tmx` 库访问权限，再把其真实用户名、密码填入 `.env`。若表结构缺少工作流等模块，需要按已有数据库的版本进行人工核对，不能用首次初始化流程覆盖数据。MySQL 健康检查使用应用账号读取 `sys_user`，认证或表访问失败会阻止主应用启动，可通过 `docker inspect mysql` 查看健康状态。

如服务已经由别的 Compose 项目或手动容器管理，不要直接执行本配置的 `up`；应将凭据、回环监听和健康检查变更同步到原部署配置。此仓库修改不会自动操作远程服务器。

## 前端与可选服务

- Nginx：按原配置准备 `/docker/nginx/conf/nginx.conf` 和前端静态目录。默认 upstream 有 8080、8081 两个节点；只启动 `tmx-server1` 时删除 8081 那一行，再启动 `nginx-web`。
- 第二个主应用：使用 `docker compose up -d tmx-server2`，与第一实例共用凭据和生产环境配置。
- MinIO：按需设置至少 8 位的 `TMX_MINIO_PASSWORD` 并同步系统内对象存储配置，再启动 `minio`。未设置时的默认值只供本地示例。
- 监控、调度和 AI：单独准备各自镜像、数据库和配置后，指定服务名启动。本次改动只统一主应用、MySQL 和 Redis 的连接。

不带服务名的 `docker compose up -d` 会尝试启动全部服务，基础部署请使用上面的明确服务名。

## 验证与排查

- 缺少密码：`docker compose config --quiet` 会指出缺失变量。
- 数据库认证失败：确认 `.env` 与已有数据库的账号一致；初始化变量只对空数据目录生效。
- Redis 认证失败：确认应用和 Redis 容器已使用同一份新配置重新创建。
- Redis 日志出现 `Permission denied`：确认数据目录挂载到容器 `/data`，并且配置中的 `dir` 同样为 `/data`。
- 端口冲突：host 网络要求宿主机 3306、6379、8080 等端口空闲；不要同时启动另一套同端口服务。
- CI：GitHub Actions 使用临时测试密码验证配置、空库初始化和应用启动，不读取开发机环境变量或 SSH 私钥。
- CI 日志页面无法显示时：在本次运行的 Summary 页面下载「部署诊断日志」附件，查看容器状态、服务日志和健康检查输出；附件保留 7 天。
