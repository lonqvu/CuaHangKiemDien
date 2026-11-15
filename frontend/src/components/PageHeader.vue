<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle" class="muted">{{ subtitle }}</p>
    </div>
    <div class="actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header { display: flex; flex-wrap: wrap; gap: var(--space-lg); align-items: flex-end; justify-content: space-between; margin-bottom: var(--space-xl); }
h1 { font-size: 22px; line-height: 1.25; }
p { margin: var(--space-xs) 0 0; font-size: 14px; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-md); }
</style>
