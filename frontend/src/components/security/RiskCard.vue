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
  border: 1px solid rgba(190, 183, 171, 0.52);
  border-radius: var(--soc-radius-card);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.88), rgba(248, 244, 236, 0.64));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(18px) saturate(1.12);
}

.risk-card::before {
  position: absolute;
  inset: 12px 12px auto auto;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  content: "";
  background: rgba(57, 120, 216, 0.1);
  box-shadow: inset 0 0 0 1px rgba(57, 120, 216, 0.18);
}

.risk-card::after {
  position: absolute;
  inset: auto 0 0;
  height: 3px;
  content: "";
  background: linear-gradient(90deg, var(--soc-blue), transparent);
}

.risk-card.critical::before { background: rgba(229, 72, 77, 0.1); box-shadow: inset 0 0 0 1px rgba(229, 72, 77, 0.2); }
.risk-card.high::before { background: rgba(240, 138, 36, 0.12); box-shadow: inset 0 0 0 1px rgba(240, 138, 36, 0.2); }
.risk-card.medium::before { background: rgba(214, 162, 29, 0.12); box-shadow: inset 0 0 0 1px rgba(214, 162, 29, 0.2); }
.risk-card.low::before { background: rgba(21, 168, 189, 0.1); box-shadow: inset 0 0 0 1px rgba(21, 168, 189, 0.18); }
.risk-card.safe::before { background: rgba(36, 168, 101, 0.1); box-shadow: inset 0 0 0 1px rgba(36, 168, 101, 0.18); }

.risk-card.critical::after { background: linear-gradient(90deg, var(--soc-critical), transparent); }
.risk-card.high::after { background: linear-gradient(90deg, var(--soc-high), transparent); }
.risk-card.medium::after { background: linear-gradient(90deg, var(--soc-medium), transparent); }
.risk-card.low::after { background: linear-gradient(90deg, var(--soc-low), transparent); }
.risk-card.safe::after { background: linear-gradient(90deg, var(--soc-success), transparent); }

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
