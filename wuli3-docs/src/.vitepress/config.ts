import { defineConfig } from 'vitepress'
import { fileURLToPath, URL } from 'node:url'

const base = process.env.DOCS_BASE || '/'
const outDir = process.env.DOCS_OUT_DIR || fileURLToPath(new URL('../../build/docs', import.meta.url))

export default defineConfig({
  lang: 'zh-CN',
  title: 'Wuli3',
  description: '从业务入口到基础设施边界的 Wuli3 设计与使用指南。',
  base: base.endsWith('/') ? base : `${base}/`,
  outDir,
  cleanUrls: true,
  lastUpdated: true,
  appearance: true,
  themeConfig: {
    logo: '/brand-mark.svg',
    siteTitle: 'Wuli3 / architecture notes',
    darkModeSwitchLabel: '切换主题',
    lightModeSwitchTitle: '切换到浅色主题',
    darkModeSwitchTitle: '切换到深色主题',
    sidebarMenuLabel: '章节导航',
    returnToTopLabel: '返回顶部',
    outlineTitle: '本页内容',
    docFooter: { prev: '上一篇', next: '下一篇' },
    lastUpdated: { text: '最后更新', formatOptions: { dateStyle: 'short' } },
    outline: 'deep',
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            displayDetails: '显示详细内容', resetButtonTitle: '清空搜索', backButtonTitle: '返回',
            noResultsText: '没有找到相关内容',
            footer: { selectText: '选择', selectKeyAriaLabel: '回车键', navigateText: '切换', navigateUpKeyAriaLabel: '向上', navigateDownKeyAriaLabel: '向下', closeText: '关闭', closeKeyAriaLabel: 'Escape' },
          },
        },
      },
    },
    nav: [
      { text: '快速开始', link: '/getting-started/quick-start' },
      { text: '理解设计', link: '/concepts/overview' },
      { text: '能力地图', link: '/capabilities/foundation' },
      { text: '工程实践', link: '/engineering/build' },
    ],
    sidebar: {
      '/getting-started/': [{ text: '上手路线', items: [
        { text: '快速入门', link: '/getting-started/quick-start' },
        { text: '接入已有项目', link: '/getting-started/adoption' },
        { text: '生成器与项目骨架', link: '/getting-started/generator' },
      ] }],
      '/concepts/': [{ text: '设计语言', items: [
        { text: '总览', link: '/concepts/overview' },
        { text: 'DDD 边界', link: '/concepts/ddd' },
        { text: '请求生命周期', link: '/concepts/request-lifecycle' },
        { text: '扩展契约', link: '/extensions/contracts' },
      ] }],
      '/extensions/': [{ text: '扩展设计', items: [
        { text: '扩展契约', link: '/extensions/contracts' },
        { text: '设计总览', link: '/concepts/overview' },
        { text: 'Web 与权限', link: '/capabilities/web-security' },
        { text: '上下文传播', link: '/capabilities/context' },
        { text: '事件与消息', link: '/capabilities/events' },
        { text: '运行与运维', link: '/capabilities/operations' },
      ] }],
      '/capabilities/': [
        { text: '基础能力', items: [
          { text: '基础契约与数据表达', link: '/capabilities/foundation' },
          { text: 'Web 与权限', link: '/capabilities/web-security' },
          { text: '数据访问', link: '/capabilities/data' },
          { text: '上下文传播', link: '/capabilities/context' },
          { text: '事件与消息', link: '/capabilities/events' },
        ] },
        { text: '外部能力', items: [
          { text: '外部系统集成', link: '/capabilities/integrations' },
          { text: '运行与运维', link: '/capabilities/operations' },
        ] },
      ],
      '/engineering/': [{ text: '交付与维护', items: [
        { text: '构建与验证', link: '/engineering/build' },
        { text: '部署与发布', link: '/engineering/deployment' },
      ] }],
    },
    socialLinks: [{ icon: 'github', link: 'https://github.com/weiran-tech/wuli3' }],
    footer: {
      message: 'Wuli3 · Java 21 · Spring Boot 3.5.x',
      copyright: '围绕真实边界设计，按证据持续验证。',
    },
  },
  markdown: { theme: 'github-dark', codeTransformers: [] },
  vite: { server: { host: '127.0.0.1', strictPort: true } },
})
