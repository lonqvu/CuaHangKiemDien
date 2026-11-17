<script setup lang="ts">
// Trang thai rong / loi: noi ro vi sao trong va buoc tiep theo (nut hanh dong qua slot).
defineProps<{ title: string; hint?: string; error?: boolean }>()
</script>

<template>
  <div class="empty" :role="error ? 'alert' : undefined">
    <p class="t" :class="{ err: error }">{{ title }}</p>
    <p v-if="hint" class="muted h">{{ hint }}</p>
    <div class="a"><slot /></div>
  </div>
</template>

<style scoped>
.empty { padding: var(--space-3xl) var(--space-xl); text-align: center; }
.t { margin: 0; font-weight: 600; color: var(--ink); }
.t.err { color: var(--danger); }
.h { margin: var(--space-sm) 0 0; font-size: 14px; }
.a { margin-top: var(--space-lg); display: flex; justify-content: center; gap: var(--space-md); flex-wrap: wrap; }
.a:empty { display: none; }
</style>
