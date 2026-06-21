<template>
  <div class="page-shell">
    <section class="soc-page-hero capability-hero">
      <div>
        <span class="soc-page-kicker">CYBERFUSION CAPABILITY MAP</span>
        <h1>能力地图</h1>
        <p>这个页面帮你了解当前平台覆盖了哪些安全能力、接入程序放在哪里，以及每项能力进入哪个处理页面。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag effect="plain">统一主线</el-tag>
        <el-tag effect="plain">能力域</el-tag>
        <el-tag effect="plain">本地接入</el-tag>
      </div>
    </section>

    <section class="source-strip">
      <span>产品链路</span>
      <strong>integrations/ 接入程序 -> 接入目录 API -> 统一导入 API -> 证据归一化 -> 告警 / 工单 / 报表</strong>
    </section>

    <section class="dashboard-topology">
      <div class="soc-panel integration-panel">
        <div class="panel-title">
          <div>
            <strong>CyberFusion 平台能力说明</strong>
            <span>主系统功能域与内置引擎协同</span>
          </div>
          <el-tag effect="plain">统一主线</el-tag>
        </div>
        <div class="integration-grid">
          <article
            v-for="item in integrationCards"
            :key="item.code"
            class="integration-card"
            :class="item.tone"
            role="link"
            tabindex="0"
            :aria-label="`进入 ${item.name}`"
            :title="`进入 ${item.name}`"
            @click="openIntegration(item)"
            @keydown.enter.prevent="openIntegration(item)"
            @keydown.space.prevent="openIntegration(item)"
          >
            <div class="integration-code">{{ item.code }}</div>
            <div class="integration-mark">{{ item.shortName }}</div>
            <strong>{{ item.name }}</strong>
            <span>{{ item.role }}</span>
            <p>{{ item.description }}</p>
            <div class="module-engines">
              <em v-for="engine in item.engines" :key="engine">{{ engine }}</em>
            </div>
            <small class="integration-path">{{ item.localPath }}</small>
            <footer>
              <i :class="item.status" />
              <b>{{ item.statusLabel }}</b>
              <em>{{ item.metricLabel }} {{ item.metric }}</em>
            </footer>
            <span class="integration-action">{{ item.targetLabel }}</span>
          </article>
        </div>
      </div>

      <aside class="soc-panel operations-workbench">
        <div class="panel-title">
          <div>
            <strong>规则与通知工作台</strong>
            <span>降噪、自动化、报告闭环</span>
          </div>
          <el-tag type="warning" effect="plain">演示态</el-tag>
        </div>
        <section class="rule-preview">
          <div class="rule-tabs">
            <span class="active">规则配置</span>
            <span>命中预览</span>
          </div>
          <label>
            <span>规则名称</span>
            <strong>企业出口代理地址白名单</strong>
          </label>
          <label>
            <span>来源模块</span>
            <strong>主机威胁中心 / 网络检测中心</strong>
          </label>
          <label>
            <span>匹配字段</span>
            <strong>source.ip in CIDR</strong>
          </label>
          <div class="rule-score">
            <div>
              <span>近 7 天命中</span>
              <strong>1,324</strong>
            </div>
            <div>
              <span>降噪命中率</span>
              <strong>67.4%</strong>
            </div>
          </div>
        </section>
        <section class="channel-list">
          <article v-for="channel in channelCards" :key="channel.name">
            <span>{{ channel.name }}</span>
            <strong>{{ channel.status }}</strong>
          </article>
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter, type RouteLocationRaw } from 'vue-router'

interface IntegrationCard {
  code: string
  shortName: string
  name: string
  role: string
  description: string
  engines: string[]
  status: string
  statusLabel: string
  metricLabel: string
  metric: string
  tone: string
  targetLabel: string
  target: RouteLocationRaw
  localPath: string
}

const router = useRouter()

const integrationCards: IntegrationCard[] = [
  { code: '01', shortName: 'HT', name: '主机威胁中心', role: '终端 / 主机安全', description: '聚合登录、配置变更、文件完整性与主机告警。', engines: ['Wazuh', 'FIM'], status: 'ready', statusLabel: '运行中', metricLabel: '告警', metric: '12,832', tone: 'warm', targetLabel: '进入证据', target: { path: '/soc/alerts', query: { adapter: 'wazuh' } }, localPath: 'integrations/wazuh-sec-soc' },
  { code: '02', shortName: 'ND', name: '网络检测中心', role: '流量 / IDS 分析', description: '把连接日志和 IDS EVE 交融成统一网络事件。', engines: ['Zeek', 'Suricata'], status: 'ready', statusLabel: '运行中', metricLabel: '事件', metric: '17,040', tone: 'cyan', targetLabel: '进入证据', target: { path: '/soc/external-events', query: { domain: 'network' } }, localPath: 'integrations/zeek-traffic-platform + suricata-ids-console' },
  { code: '03', shortName: 'VM', name: '漏洞与暴露面', role: '漏洞 / Web 风险', description: '融合镜像漏洞、软件包风险和 Web 扫描发现。', engines: ['Trivy', 'ZAP'], status: 'ready', statusLabel: '运行中', metricLabel: '风险', metric: '4,254', tone: 'blue', targetLabel: '进入漏洞', target: { path: '/soc/vulnerabilities', query: { domain: 'exposure' } }, localPath: 'integrations/trivy-platform + zap-authorized-scan-platform' },
  { code: '04', shortName: 'TI', name: '威胁情报中心', role: 'IOC / 关联命中', description: '管理 IOC、情报命中和告警优先级提升。', engines: ['MISP'], status: 'ready', statusLabel: '运行中', metricLabel: 'IOC', metric: '4,128', tone: 'blue', targetLabel: '进入证据', target: { path: '/soc/external-events', query: { sourceType: 'misp' } }, localPath: 'integrations/misp-deploy' },
  { code: '05', shortName: 'DR', name: '检测规则中心', role: '规则 / 命中预览', description: '沉淀检测规则、白名单和降噪策略。', engines: ['Sigma', 'Rule'], status: 'active', statusLabel: '规则池', metricLabel: '规则', metric: '1,248', tone: 'warm', targetLabel: '进入规则', target: { path: '/soc/rules' }, localPath: 'integrations/sigma-manager' },
  { code: '06', shortName: 'FA', name: '字段分析工作台', role: '字段 / IOC 解析', description: '对 URL、IP、编码字段和可疑载荷做分析辅助。', engines: ['CyberChef'], status: 'active', statusLabel: '可用', metricLabel: '分析', metric: '860', tone: 'cyan', targetLabel: '进入分析', target: { path: '/soc/external-events', query: { tool: 'cyberchef' } }, localPath: 'integrations/cyberchef-deploy' },
  { code: '07', shortName: 'SO', name: '自动化编排中心', role: '通知 / SOAR 流程', description: '把告警、工单、通知和演示流程串成闭环。', engines: ['Shuffle', 'Webhook'], status: 'active', statusLabel: '可用', metricLabel: '流程', metric: '24', tone: 'warm', targetLabel: '进入通知', target: { path: '/soc/settings', query: { section: 'notifications' } }, localPath: 'integrations/shuffle-examples' },
  { code: '08', shortName: 'CA', name: '案件处置中心', role: '告警 / 工单 / 报表', description: '把统一告警转工单、跟踪 SLA 并生成综合报告。', engines: ['Ticket', 'Report'], status: 'ready', statusLabel: '运行中', metricLabel: '工单', metric: '212', tone: 'blue', targetLabel: '进入工单', target: { path: '/soc/tickets' }, localPath: '00-cyberfusion-platform backend/frontend' },
]

const channelCards = [
  { name: 'Shuffle demo workflow', status: '正常' },
  { name: 'Email 演示通道', status: '正常' },
  { name: 'Webhook dry-run', status: '正常' },
]

function openIntegration(item: IntegrationCard) {
  router.push(item.target)
}
</script>

<style scoped>
.source-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(190, 183, 171, 0.52);
  border-radius: var(--soc-radius-card);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 246, 232, 0.64));
  box-shadow: var(--soc-shadow-soft), var(--soc-glass-highlight);
  backdrop-filter: blur(18px) saturate(1.12);
  padding: 10px 14px;
  color: var(--soc-text-muted);
  font-size: 13px;
}

.source-strip strong {
  color: var(--soc-text);
  font-weight: 500;
}

.dashboard-topology {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 14px;
}

.integration-panel,
.operations-workbench {
  min-width: 0;
  padding: 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title span {
  color: var(--soc-text-muted);
  font-size: 13px;
}

.integration-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 10px;
  min-width: 0;
}

.integration-card {
  position: relative;
  display: grid;
  gap: 6px;
  min-width: 0;
  min-height: 184px;
  overflow: hidden;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(248, 244, 237, 0.56)),
    rgba(255, 255, 255, 0.42);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.integration-card::after {
  position: absolute;
  top: -44px;
  right: -44px;
  width: 104px;
  height: 104px;
  border-radius: 999px;
  background: rgba(212, 147, 74, 0.12);
  content: "";
}

.integration-card:hover,
.integration-card:focus-visible {
  border-color: rgba(212, 147, 74, 0.64);
  box-shadow: 0 16px 34px rgba(91, 77, 53, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  outline: 0;
  transform: translateY(-1px);
}

.integration-card.cyan::after {
  background: rgba(16, 179, 199, 0.14);
}

.integration-card.blue::after {
  background: rgba(57, 120, 216, 0.12);
}

.integration-code {
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 760;
}

.integration-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(255, 246, 232, 0.96), rgba(235, 249, 251, 0.88));
  color: var(--soc-warm-strong);
  box-shadow: 0 10px 22px rgba(91, 77, 53, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-weight: 800;
}

.integration-card.cyan .integration-mark {
  color: #08798b;
}

.integration-card.blue .integration-mark {
  color: var(--soc-blue);
}

.integration-card strong {
  position: relative;
  z-index: 1;
  overflow: hidden;
  color: var(--soc-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-card > span {
  position: relative;
  z-index: 1;
  overflow: hidden;
  color: var(--soc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-card p {
  position: relative;
  z-index: 1;
  min-height: 36px;
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.module-engines {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.module-engines em {
  max-width: 100%;
  overflow: hidden;
  padding: 2px 6px;
  border: 1px solid rgba(57, 120, 216, 0.16);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--soc-text-muted);
  font-size: 10px;
  font-style: normal;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-path {
  position: relative;
  z-index: 1;
  display: block;
  overflow: hidden;
  color: #7a879a;
  font-size: 10px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-card footer {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 6px;
  align-items: center;
  margin-top: auto;
  color: var(--soc-text-muted);
  font-size: 11px;
}

.integration-card footer i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--soc-success);
  box-shadow: 0 0 0 4px rgba(36, 168, 101, 0.1);
}

.integration-card footer i.active {
  background: var(--soc-blue);
  box-shadow: 0 0 0 4px rgba(57, 120, 216, 0.1);
}

.integration-card footer b {
  color: var(--soc-text);
  font-weight: 680;
}

.integration-card footer em {
  grid-column: 1 / -1;
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-action {
  position: relative;
  z-index: 1;
  color: var(--soc-warm-strong);
  font-size: 11px;
  font-weight: 760;
}

.operations-workbench {
  display: grid;
  align-content: start;
  gap: 14px;
}

.rule-preview {
  display: grid;
  gap: 10px;
}

.rule-tabs {
  display: flex;
  gap: 12px;
  border-bottom: 1px solid var(--soc-border);
}

.rule-tabs span {
  padding-bottom: 8px;
  color: var(--soc-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.rule-tabs .active {
  color: var(--soc-warm-strong);
  box-shadow: inset 0 -2px 0 var(--soc-warm);
}

.rule-preview label {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.52);
}

.rule-preview label span,
.rule-score span {
  color: var(--soc-text-muted);
  font-size: 11px;
}

.rule-preview label strong {
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 13px;
}

.rule-score {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rule-score > div {
  padding: 10px;
  border: 1px solid rgba(212, 147, 74, 0.2);
  border-radius: 8px;
  background: rgba(255, 248, 238, 0.68);
}

.rule-score strong {
  display: block;
  margin-top: 4px;
  color: var(--soc-warm-strong);
  font-size: 20px;
}

.channel-list {
  display: grid;
  gap: 8px;
}

.channel-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(179, 173, 163, 0.38);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.52);
}

.channel-list span {
  color: var(--soc-text);
  font-size: 12px;
  font-weight: 680;
}

.channel-list strong {
  color: var(--soc-success);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .dashboard-topology {
    grid-template-columns: 1fr;
  }

  .integration-card {
    padding: 10px;
  }

  .integration-mark {
    width: 36px;
    height: 36px;
    font-size: 12px;
  }

  .integration-card strong {
    font-size: 13px;
  }
}

@media (max-width: 980px) {
  .source-strip {
    align-items: flex-start;
    flex-direction: column;
  }

  .integration-grid {
    grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  }
}
</style>
