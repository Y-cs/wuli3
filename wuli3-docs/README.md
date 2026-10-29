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

静态 HTML 输出到 `build/docs/`。从仓库根目录也可以运行 `./gradlew :wuli3-docs:check` 或 `./gradlew :wuli3-docs:build`；根构建的 `check` / `build` 包含文档构建，因此需要 Node 和 npm。Gradle 的 `npmCi` 会按锁文件安装依赖。

稳定版 VitePress 1.6.4 的 Vite/esbuild 开发工具链仍有 npm audit 报告，当前没有稳定版范围内的完整修复；本站的 dev/preview 默认只监听 `127.0.0.1`，不要作为对外服务运行。Pages 仅部署静态产物，不运行该开发服务器。后续跟进 VitePress 稳定版升级，不跨主版本强制覆盖内部 Vite。

## GitHub Pages

`.github/workflows/docs.yml` 对 PR 执行构建检查，对 main 的文档变更和手动运行执行部署。首次部署前在 GitHub 仓库 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**，并确保仓库允许 Pages 部署。

工作流读取 `configure-pages` 的 `base_path`，适配项目站、组织站和自定义域名。部署 job 使用 `github-pages` environment；如该 environment 配置保护规则，部署会按 GitHub 的规则等待审核。

可在本地验证项目子路径：

```bash
DOCS_BASE=/wuli3/ npm run docs:build
DOCS_BASE=/wuli3/ npm run docs:preview
```

预览服务的入口以控制台显示的地址为准，使用相同 `DOCS_BASE` 打开 `/wuli3/`。恢复根路径构建时重新运行 `npm run docs:build`。

## 内容与图示维护

- 正文在 `src/`；导航与全文搜索配置在 `src/.vitepress/config.ts`。
- 首页与章节优先使用文字、表格、SVG 架构图；适合分阶段解释的流程使用全局 `FlowDiagram` Vue 组件，支持点击和键盘选择。
- 模块页说明默认条件、应用职责和验收方式，代码只保留必要命令、关键配置及扩展点。
- 以当前源码为事实来源；旧 `guide/` 的示例可能落后于实现。不要把草案、接口声明或测试夹具描述为生产能力。
- 构建默认检查站内死链。修改导航和组件后，还需检查移动端、深色主题、搜索和 Pages 子路径。
