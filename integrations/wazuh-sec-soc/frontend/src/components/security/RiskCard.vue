<template>
  <section class="risk-card" :class="tone">
    <span class="label">{{ label }}</span>
    <strong>{{ value }}</strong>
    <small>{{ delta }}</small>
  </section>
</template>

<script setup lang="ts">
defineProps<{ label: string; value: string | number; delta: string; tone?: 'critical' | 'high' | 'medium' | 'low' | 'safe' }>()
</script>

<style scoped>
.risk-card {
  position: relative;
  display: grid;
  gap: 8px;
  min-height: 112px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background: var(--soc-surface-raised);
}

.risk-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  content: "";
  background: var(--soc-blue);
}

.risk-card.critical::before { background: var(--soc-critical); }
.risk-card.high::before { background: var(--soc-high); }
.risk-card.medium::before { background: var(--soc-medium); }
.risk-card.low::before { background: var(--soc-low); }
.risk-card.safe::before { background: var(--soc-success); }

.label {
  color: var(--soc-text-muted);
  font-size: 13px;
}

strong {
  color: var(--soc-text);
  font-size: 30px;
  line-height: 1;
}

small {
  color: var(--soc-text-subtle);
}
</style>
