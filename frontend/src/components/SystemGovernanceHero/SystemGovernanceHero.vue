<template>
  <section class="soc-page-hero governance-hero">
    <div class="governance-copy">
      <span class="soc-page-kicker">CyberFusion Governance</span>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="governance-side">
      <span>{{ scope }}</span>
      <strong>{{ total }}</strong>
      <slot name="action" />
    </div>
    <div v-if="summary.length" class="governance-summary">
      <article v-for="item in summary" :key="item.label" class="governance-summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
export interface GovernanceSummaryItem {
  label: string
  value: string | number
  hint: string
}

withDefaults(defineProps<{
  title: string
  description: string
  scope: string
  total: string | number
  summary?: GovernanceSummaryItem[]
}>(), {
  summary: () => [],
})
</script>

<style scoped>
.governance-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.governance-copy {
  min-width: 0;
}

.governance-side {
  display: grid;
  min-width: 180px;
  justify-items: end;
  gap: 8px;
}

.governance-side span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 750;
}

.governance-side strong {
  color: var(--soc-text);
  font-size: 30px;
  line-height: 1;
}

.governance-summary {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.governance-summary-card {
  position: relative;
  overflow: hidden;
  min-width: 0;
  min-height: 86px;
  padding: 14px;
  border: 1px solid var(--soc-border);
  border-radius: var(--soc-radius-card);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(249, 245, 238, 0.64)),
    var(--soc-glass);
  box-shadow: var(--soc-glass-highlight);
}

.governance-summary-card::after {
  position: absolute;
  top: -42px;
  right: -28px;
  width: 112px;
  height: 150px;
  transform: rotate(18deg);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.7), rgba(212, 147, 74, 0.1));
  content: "";
  pointer-events: none;
}

.governance-summary-card span,
.governance-summary-card strong,
.governance-summary-card small {
  position: relative;
  z-index: 1;
}

.governance-summary-card span {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 750;
}

.governance-summary-card strong {
  display: block;
  margin-top: 8px;
  color: var(--soc-text);
  font-size: 24px;
  line-height: 1;
}

.governance-summary-card small {
  display: block;
  margin-top: 10px;
  color: var(--soc-text-subtle);
  font-size: 12px;
}

@media (max-width: 760px) {
  .governance-hero,
  .governance-summary {
    grid-template-columns: 1fr;
  }

  .governance-side {
    justify-items: start;
    min-width: 0;
  }
}
</style>
