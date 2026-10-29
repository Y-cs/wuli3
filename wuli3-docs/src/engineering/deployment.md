---
description: GitHub Pages 发布、项目站 base 路径和生产部署边界。
---

# 部署与发布

文档站构建成静态 HTML，GitHub Actions 把构建产物发布到 GitHub Pages。应用本身的数据库、消息、认证和网络部署仍由业务环境负责。

## Pages 工作流的关键条件

工作流需要：

- build job 使用 Node 24、npm ci 和 npm run docs:build。
- deploy job 依赖 build，环境为 github-pages。
- 权限包含 pages: write 和 id-token: write。
- 使用 configure-pages 的 base_path 生成项目站路径。
- Pages 设置中的 Source 选择 GitHub Actions。

项目站的 base 来自实际仓库名，例如当前远端仓库 wuli3 对应 `/wuli3/`；用户站、自定义域名或本地预览则可能是 `/`。配置从 Action 输出读取，仓库目录重命名不会影响部署路径。

## 静态产物检查

部署前检查：

1. build/docs/index.html 存在。
2. 页面中的脚本、样式和 logo 以当前 base 路径开头。
3. 刷新一个深层路径不会出现 404 资源请求。
4. 本地 preview 和 Pages 子路径都能打开全文搜索。

## 应用服务的部署边界

生成器和 starter 不会替应用创建数据库、消息 topic、权限系统或网关认证。生产部署需要单独确认：入口是否清洗身份头、服务是否阻断绕过网关的访问、下游客户端是否避免把内部身份传播到第三方、批量和分页是否有成本上限。

对于真实应用，发布前应将这些边界转成联调场景，而不是把静态站构建成功当作系统上线证明。
