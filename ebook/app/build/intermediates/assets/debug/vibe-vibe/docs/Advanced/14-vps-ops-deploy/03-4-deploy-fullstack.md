---
title: "14.3.4 部署前后端分离应用"
---

# 14.3.4 部署前后端分离应用

> **本节目标**：掌握前后端分离架构的通用部署流程，学会用 Claude Code 辅助配置反向代理，适用于任何技术栈（Node.js / Python / PHP / Java / Go / .NET 等）。

::: tip 本节与 14.3.2 的区别
- **14.3.2**：适合 Next.js 这种"前后端一体"的全栈框架，一个容器搞定
- **本节（14.3.4）**：适合"前后端分离"的架构，前端和后端是独立的项目，需要分别部署

如果你的项目是 Next.js/Nuxt 等全栈框架，直接看 14.3.2 即可。如果你的前端是 React/Vue，后端是独立的 API 服务（Node.js/Python/Java 等），那就需要本节的方案。
:::

小明的全栈项目有前端、后端 API、数据库三个部分。"之前在 Vercel 上是一个项目搞定，现在要分开部署？"

老师傅说："Vercel 帮你把这些打包在一起了，但在自己的服务器上，你需要理解它们各自的角色。好处是——你对每一层都有完全的控制权。"

他画了一张图："前后端分离的架构长这样——"

## 前后端分离架构

```
用户浏览器
    │
    ▼
  OpenResty（反向代理）:80
    │
    ├── /          → 前端静态文件（HTML/CSS/JS）
    ├── /api/*     → 后端服务（容器端口 3000/8000/8080...）
    │
    └── 后端服务
            │
            ▼
        数据库（PostgreSQL/MySQL/MongoDB...）
```

三个服务各司其职：

- **OpenResty**：对外的唯一入口，负责分发请求
- **后端服务**：处理业务逻辑和数据库操作（Node.js / Python / Java / Go 等）
- **数据库**：存储数据

::: tip 什么是反向代理？
**反向代理**就像一个"前台接待员"：
- 用户的所有请求都先到前台（OpenResty）
- 前台看请求的路径，决定转给谁处理：
  - 访问 `/` → 返回前端页面
  - 访问 `/api/users` → 转发给后端服务
- 用户感觉只和一个服务器打交道，但背后可能有多个服务在协作

**好处**：
1. 统一入口：用户只需要记一个域名
2. 避免跨域：前端和 API 在同一个域名下
3. 安全：后端服务不直接暴露在公网
:::

小明画了一张简单的图：用户请求先到 OpenResty，OpenResty 看路径——如果是 `/api` 开头就转给后端，其他的就返回前端页面。"原来 Vercel 在背后也是这么干的，只不过它帮我把这些全藏起来了。"

它们都运行在 Docker 容器里，通过容器网络互相通信。

## 通用部署流程

无论你的后端是什么技术栈，部署流程都是类似的：

### 第一步：部署数据库

根据项目需求，在 1Panel 应用商店安装对应的数据库：

- **PostgreSQL**：适合关系型数据、复杂查询
- **MySQL**：适合传统 Web 应用
- **MongoDB**：适合文档型数据
- **Redis**：适合缓存、会话存储

安装完成后，创建应用专用数据库和用户，记下连接信息（主机名、端口、用户名、密码）。

::: tip 1Panel 数据库容器命名规则
1Panel 创建的数据库容器名遵循 `1Panel-{数据库类型}-{随机4位字母}` 的格式：
- PostgreSQL: `1Panel-postgresql-ukow`
- Redis: `1Panel-redis-w94p`
- MySQL: `1Panel-mysql-abcd`
- MongoDB: `1Panel-mongodb-xyz1`

**如何查看实际的容器名**：
在 1Panel 的「容器」页面，列表的第一列就是容器名。你也可以在「数据库」页面点击对应数据库的「详情」查看连接信息。

后端应用通过**容器名**（而非 `localhost`）连接数据库。
:::

::: details 常见数据库连接字符串示例

**PostgreSQL**:
```bash
DATABASE_URL="postgresql://用户名:密码@1Panel-postgresql-ukow:5432/数据库名"
```

**MySQL**:
```bash
DATABASE_URL="mysql://用户名:密码@1Panel-mysql-abcd:3306/数据库名"
```

**Redis（有密码）**:
```bash
REDIS_URL="redis://:密码@1Panel-redis-w94p:6379"
```

**Redis（无密码）**:
```bash
REDIS_URL="redis://1Panel-redis-w94p:6379"
```

**MongoDB**:
```bash
MONGODB_URI="mongodb://用户名:密码@1Panel-mongodb-xyz1:27017/数据库名"
```
:::

### 第二步：部署后端服务

根据后端技术栈选择部署方式：

| 技术栈 | 1Panel 运行环境(语言) | 启动命令参考 |
|--------|---------|-------------|
| Node.js / Next.js | Node.js | `git pull && pnpm build && pnpm start` |
| Python / FastAPI | Python | `git pull && pip install -r requirements.txt && uvicorn main:app --host 0.0.0.0` |
| PHP / Laravel | PHP | PHP-FPM 自动启动，通过 OpenResty 反向代理访问 |
| Java / Spring Boot | Java | `java -jar app.jar` |
| Go | Go | `./app` |
| .NET / ASP.NET Core | .NET | `dotnet run --urls http://0.0.0.0:5000` |

::: tip 运行环境 = Docker 容器
1Panel 的「运行环境」本质上是 Docker 容器。每种语言（Node.js、Python、PHP、Java、Go、.NET）对应一个预配置好的基础镜像，你选择语言和版本后，1Panel 会用 docker-compose 帮你管理容器的创建和启动。
:::

**关键配置**：

1. **环境变量**：数据库连接字符串、密钥等
2. **端口映射**：在高级配置中，把容器内部端口（如 3000）映射到服务器外部端口（如 3001）
3. **启动命令**：确保每次重启时自动拉取最新代码
4. **容器名称**：用于容器间通信（如反向代理通过容器名转发请求）

小明的项目是 Node.js，他在「运行环境」中创建了一个容器，填了项目目录、启动命令、环境变量。几分钟后，后端 API 跑起来了。他在浏览器访问 `http://服务器IP:3001/api/health`，看到了 JSON 响应。

### 第三步：构建并部署前端

前端通常需要先在本地构建，然后上传到服务器：

```bash
# 在本地电脑上
cd your-project

# 设置环境变量（指向服务器的后端 API）
echo "VITE_API_URL=http://你的服务器IP:3001" > .env.production

# 构建前端
npm run build  # 或 pnpm build / yarn build

# 构建产物通常在 dist/ 或 build/ 或 out/ 目录
```

上传到服务器：

```bash
# 方式一：使用 scp
scp -r dist/* root@你的服务器IP:/opt/your-frontend/

# 方式二：使用 FinalShell 拖拽上传
```

在 1Panel 中创建静态网站：

1. 进入「网站 > 网站」，点击「创建网站」
2. 选择「静态网站」
3. 主域名：`yourdomain.com`（或先用 IP 测试）
4. 网站目录：`/opt/your-frontend`
5. 点击确认

小明用 FinalShell 把构建好的前端文件拖到服务器上，然后在 1Panel 创建了静态网站。浏览器访问服务器 IP，前端页面出来了——但他试着登录，发现报错："跨域请求被阻止"。

### 第四步：配置反向代理（关键步骤）

现在前端和后端是分开的——前端在 80 端口，后端在 3001 端口。前端发起的 API 请求会遇到跨域问题。解决方案是配置反向代理，让前端和后端在同一个域名下。

#### 推荐工作流：让 Claude Code 帮你写配置

1. **获取 1Panel 的默认配置**

在 1Panel 中，进入刚创建的静态网站的设置页面，点击「配置文件」选项卡，复制整个配置内容。

2. **在本地用 Claude Code 完善配置**

打开本地的 Claude Code，发送以下 prompt：

```
我需要配置 OpenResty 反向代理，实现前后端分离部署。

当前 1Panel 默认配置：
[粘贴你复制的配置]

我的需求：
- 前端静态文件在 /opt/your-frontend
- 后端 API 容器名是 your-backend，端口 3000
- 所有 /api/* 请求转发到后端
- 其他请求返回前端页面（SPA 路由支持）

请帮我完善这个配置。
```

Claude Code 会根据你的实际情况生成完整的配置，包括：
- 正确的 `proxy_pass` 地址
- 必要的请求头设置
- 超时配置
- SPA 路由支持（`try_files`）

3. **复制回服务器**

把 Claude Code 生成的配置复制回 1Panel 的「配置文件」编辑框，点击「保存并重载」。

小明把 1Panel 的默认配置复制到本地 Claude Code，说明了自己的需求。Claude Code 很快生成了完整的配置，包括反向代理规则、请求头设置、超时配置。小明复制回 1Panel，点击保存——刷新浏览器，这次登录成功了！

::: tip 为什么推荐这个工作流？

1. **Claude Code 理解你的项目**：它知道你的容器名、端口、路由结构
2. **避免手动出错**：容器名拼写错误、路径配置错误是常见问题
3. **自动补全细节**：超时设置、请求头、错误处理等容易遗漏的配置
4. **可以反复调整**：配置不对可以继续让 Claude Code 修改

:::

::: warning 如果 Claude Code 生成的配置有问题怎么办？

1. **查看错误日志**：在「应用商店 → 已安装 → OpenResty」中查看错误日志
2. **把错误信息发给 Claude Code**：复制错误日志，告诉 Claude Code "我的反向代理配置有问题，错误日志是..."
3. **Claude Code 会帮你修正**：它会分析错误原因，生成修正后的配置
4. **常见错误**：
   - 容器名拼写错误 → 检查容器名是否正确
   - 端口号错误 → 检查后端服务的实际端口
   - 路径配置错误 → 检查 `proxy_pass` 的路径是否正确

如果多次尝试仍然失败，可以参考本节末尾的"典型的反向代理配置示例"手动修改。
:::

#### 典型的反向代理配置示例

Claude Code 生成的配置通常长这样：

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # 前端静态文件
    location / {
        root /opt/your-frontend;
        try_files $uri $uri/ /index.html;  # SPA 路由支持
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://your-backend:3000/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### 第五步：更新前端 API 地址

反向代理配置好后，前端的 API 地址应该改成相对路径（因为前端和后端在同一个域名下了）：

```bash
# 在本地电脑上
echo "VITE_API_URL=/api" > .env.production  # 或 NEXT_PUBLIC_API_URL=/api

# 重新构建
npm run build

# 重新上传到服务器
scp -r dist/* root@你的服务器IP:/opt/your-frontend/
```

小明重新构建并上传了前端。刷新浏览器，所有功能都正常了——"前端、后端、数据库，三个部分在自己的服务器上协同工作了。"

## 不同技术栈的差异点

虽然部署流程类似，但不同技术栈有一些细节差异：

### Node.js / Next.js

- **运行环境语言**：Node.js
- **版本选择**：选择和本地一致的 Node.js 版本（最新版本 24.10.0）
- **启动命令**：`pnpm build && pnpm start`
- **端口**：通常 3000
- **环境变量**：`DATABASE_URL`、`NODE_ENV=production`

### Python / FastAPI

- **运行环境语言**：Python
- **版本选择**：选择和本地一致的 Python 版本（最新版本 3.14.0）
- **启动命令**：`pip install -r requirements.txt && uvicorn main:app --host 0.0.0.0 --port 8000`
- **端口**：通常 8000
- **环境变量**：`DATABASE_URL`、`PYTHONUNBUFFERED=1`

### PHP / Laravel

- **运行环境语言**：PHP
- **版本选择**：选择和项目要求一致的 PHP 版本（最新版本 8.5.2）
- **部署方式**：PHP 运行环境提供 PHP-FPM 服务（默认端口 9000），需要配合 OpenResty 使用
- **启动命令**：PHP-FPM 自动启动，无需配置启动命令
- **环境变量**：`DB_CONNECTION`、`DB_HOST`（填容器名）、`APP_ENV=production`
- **注意**：PHP 项目通常通过 OpenResty 反向代理到 PHP-FPM，而不是独立运行 HTTP 服务器

### Java / Spring Boot

- **运行环境语言**：Java
- **版本选择**：选择和项目一致的 JDK 版本（最新版本 22）
- **启动命令**：`java -jar app.jar`
- **端口**：通常 8080
- **环境变量**：`SPRING_DATASOURCE_URL`、`SPRING_PROFILES_ACTIVE=prod`

### Go

- **运行环境语言**：Go
- **版本选择**：选择和项目一致的 Go 版本（最新版本 1.25）
- **启动命令**：`./app`
- **端口**：自定义（如 8080）
- **环境变量**：根据项目自定义

### .NET / ASP.NET Core

- **运行环境语言**：.NET
- **版本选择**：选择和项目一致的 .NET 版本（最新版本 10.0）
- **启动命令**：`dotnet run --urls http://0.0.0.0:5000`
- **端口**：通常 5000
- **环境变量**：`ConnectionStrings__DefaultConnection`、`ASPNETCORE_ENVIRONMENT=Production`

## 测试完整流程

部署完成后，测试以下功能确保一切正常：

1. **访问首页**：`http://你的服务器IP` → 看到前端页面
2. **API 请求**：前端发起的 API 请求能正常返回数据
3. **数据持久化**：刷新页面后数据仍然存在
4. **用户认证**：登录、退出、权限验证等功能正常

小明把这些功能都测试了一遍，全部正常。他打开开发者工具的 Network 面板，看到前端页面是从 `/` 加载的，API 请求是发往 `/api/xxx` 的——"用户完全感觉不到后面有三个服务在跑。"

## 常见问题排查

| 现象 | 可能原因 | 解决方案 |
|------|---------|---------|
| 前端页面打不开 | 安全组没开 80 端口 | 去云厂商控制台开放 80 端口 |
| API 请求 404 | 反向代理配置错误 | 检查 `proxy_pass` 的容器名和路径 |
| API 请求 502 | 后端容器没启动 | 在「运行环境」中查看日志，重启容器 |
| 数据库连接失败 | `DATABASE_URL` 主机名填错 | 用容器名（如 `postgresql`）替代 `localhost` |
| 跨域错误 | 反向代理未生效 | 确认 OpenResty 配置已保存并重载 |
| SPA 路由 404 | 缺少 `try_files` 配置 | 让 Claude Code 补充 SPA 路由支持 |

::: tip 遇到问题时的调试流程

1. **先看日志**：在「容器」或「运行环境」中查看日志，90% 的问题日志里有答案
2. **检查容器状态**：确认所有容器都在运行中
3. **测试容器间通信**：进入后端容器终端，`ping postgresql` 测试网络连通性
4. **让 Claude Code 帮忙**：把错误日志发给 Claude Code，让它帮你分析

:::

## 架构优势总结

通过前后端分离部署，你获得了：

1. **统一入口**：用户只需要记一个域名，不需要知道后端在哪个端口
2. **避免跨域**：前端和 API 在同一个域名下，不存在跨域问题
3. **安全**：后端服务不直接暴露在公网，只有 OpenResty 对外
4. **灵活扩展**：前端、后端、数据库可以独立升级和扩容
5. **完全控制**：你对每一层都有完全的控制权，不受平台限制

小明回想起在 Vercel 上部署时，一个 `vercel deploy` 就搞定了。现在虽然步骤多了，但他理解了每一步在做什么——"Vercel 帮我做的事情，我现在自己也能做了。而且有 Claude Code 帮忙写配置，也没那么难。"

---

::: info 下一步
应用部署完成了，但现在只能通过 IP 访问，不够专业。接下来配置域名和 HTTPS——[14.4 配置域名与证书](./04-domain-ssl.md)。
:::
