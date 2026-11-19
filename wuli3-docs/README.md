# Wuli3 文档站

面向使用者的中文设计与入门文档，按快速入门、核心设计、模块能力、扩展契约、工程交付逐步展开。详细 API 示例仍可在仓库 `guide/` 查阅；本站围绕职责、流程和使用边界重新组织内容。

## 本地运行

需要 Node.js 24 和 npm；文档站不依赖 Java 服务或中间件。

```bash
cd wuli3-docs
npm ci
npm run docs:dev
```

生产构建与预览：

```bash
npm run docs:build
npm run docs:preview
```

静态 HTML 输出到 `build/docs/`。从仓库根目录也可以运行 `./gradlew :wuli3-docs:build` 触发构建；`check` 只做校验，不构建文档站。Gradle 的 `npmCi` 会按锁文件安装依赖。

稳定版 VitePress 1.6.4 的 Vite/esbuild 开发工具链仍有 npm audit 报告，当前没有稳定版范围内的完整修复；本站的 dev/preview 默认只监听 `127.0.0.1`，不要作为对外服务运行。Vercel 仅部署静态产物，不运行该开发服务器。后续跟进 VitePress 稳定版升级，不跨主版本强制覆盖内部 Vite。

## Vercel 发布

文档站发布到 https://wuli3.wulicode.com，由 Vercel 连接 `weiran-tech/wuli3` 仓库，项目根目录为 `wuli3-docs`，使用 Node.js 24。

只有 `main` 更新后触发生产部署；创建或更新 PR、推送其他分支均不生成预览部署。项目设置关闭预览部署，`vercel.json` 同时声明仅允许 `main` 自动部署。

Vercel 使用 `npm ci` 安装依赖、`npm run docs:build` 构建，发布 `build/docs/`。站点使用根路径 `/`，`cleanUrls` 支持直接访问不带 `.html` 的章节地址。GitHub Pages 工作流已移除。

## 内容与图示维护

- 正文在 `src/`；导航与全文搜索配置在 `src/.vitepress/config.ts`。
- 首页与章节优先使用文字、表格、SVG 架构图；适合分阶段解释的流程使用全局 `FlowDiagram` Vue 组件，支持点击和键盘选择。
- 模块页说明默认条件、应用职责和验收方式，代码只保留必要命令、关键配置及扩展点。
- 以当前源码为事实来源；旧 `guide/` 的示例可能落后于实现。不要把草案、接口声明或测试夹具描述为生产能力。
- 构建默认检查站内死链。修改导航和组件后，还需检查移动端、深色主题、搜索和深层章节的直接访问。
