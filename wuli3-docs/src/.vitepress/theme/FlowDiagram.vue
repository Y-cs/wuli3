<script setup lang="ts">
import { ref, useId } from 'vue'

type Step = { title: string; detail: string }
defineProps<{ title: string; steps: Step[] }>()
const selected = ref(0)
const panelId = useId()
</script>

<template>
  <div class="flow-diagram" role="group" :aria-label="title">
    <div class="flow-diagram__label">{{ title }}</div>
    <div class="flow-diagram__track">
      <template v-for="(step, index) in steps" :key="step.title">
        <button class="flow-diagram__step" type="button" :aria-pressed="selected === index"
          :aria-controls="panelId" @click="selected = index">
          <span class="flow-diagram__index">0{{ index + 1 }}</span>
          <strong>{{ step.title }}</strong>
        </button>
        <span v-if="index < steps.length - 1" class="flow-diagram__arrow" aria-hidden="true">→</span>
      </template>
    </div>
    <p :id="panelId" class="flow-diagram__detail" aria-live="polite">
      <strong>{{ steps[selected]?.title }}</strong> · {{ steps[selected]?.detail }}
    </p>
    <p class="flow-diagram__hint">选择节点，查看这一阶段的职责。</p>
  </div>
</template>
