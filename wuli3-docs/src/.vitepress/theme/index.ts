import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import FlowDiagram from './FlowDiagram.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('FlowDiagram', FlowDiagram)
  },
} satisfies Theme
