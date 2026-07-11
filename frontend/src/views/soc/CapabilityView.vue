<template>
  <div class="page-shell">
    <template v-if="activeCapability">
      <section class="soc-panel capability-detail-panel">
        <div class="capability-detail-head">
          <div class="capability-title-stack">
            <span>{{ activeCapability.code }} · {{ activeCapability.shortName }}</span>
            <strong>{{ activeCapability.name }}</strong>
            <em>{{ activeCapability.role }}</em>
          </div>
          <div class="capability-actions">
            <el-button type="primary" @click="router.push(activeCapability.useTarget)">{{ activeCapability.useLabel }}</el-button>
          </div>
        </div>
        <div class="capability-summary-grid">
          <article>
            <span>内置引擎</span>
            <strong>{{ activeCapability.engines.join(' / ') }}</strong>
          </article>
          <article>
            <span>当前状态</span>
            <strong>{{ activeCapability.statusLabel }}</strong>
          </article>
          <article>
            <span>{{ activeCapability.metricLabel }}</span>
            <strong>{{ activeCapability.metric }}</strong>
          </article>
          <article>
            <span>本地素材</span>
            <strong>{{ activeCapability.localPath }}</strong>
          </article>
        </div>
      </section>

      <section class="capability-detail-grid">
        <article class="soc-panel detail-section">
          <div class="panel-title">
            <div>
              <strong>工作原理</strong>
              <span>该能力在平台内的采集、归一化和关联方式</span>
            </div>
          </div>
          <ol class="detail-list ordered">
            <li v-for="step in activeCapability.principles" :key="step">{{ step }}</li>
          </ol>
        </article>

        <article class="soc-panel detail-section">
          <div class="panel-title">
            <div>
              <strong>产生结果</strong>
              <span>进入 SOC 后会形成的业务对象和可观察结果</span>
            </div>
          </div>
          <ul class="detail-list">
            <li v-for="result in activeCapability.results" :key="result">{{ result }}</li>
          </ul>
        </article>

        <article class="soc-panel detail-section wide">
          <div class="panel-title">
            <div>
              <strong>功能关联与关联方式</strong>
              <span>该能力如何和其他页面、数据对象、算法联动</span>
            </div>
          </div>
          <div class="relation-grid">
            <section v-for="relation in activeCapability.relations" :key="relation.title">
              <em>{{ relation.linkedTo }}</em>
              <strong>{{ relation.title }}</strong>
              <span>{{ relation.description }}</span>
            </section>
          </div>
        </article>

        <article class="soc-panel detail-section wide">
          <div class="panel-title">
            <div>
              <strong>在本系统中怎么使用</strong>
              <span>分析员在 CyberFusion 内的主要使用路径</span>
            </div>
          </div>
          <div class="usage-grid">
            <section v-for="usage in activeCapability.systemUse" :key="usage.title">
              <strong>{{ usage.title }}</strong>
              <span>{{ usage.description }}</span>
            </section>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeAlgorithm">
      <section class="soc-panel capability-detail-panel">
        <div class="capability-detail-head">
          <div class="capability-title-stack">
            <span>{{ activeAlgorithm.code }} · {{ activeAlgorithm.category }}</span>
            <strong>{{ activeAlgorithm.name }}</strong>
            <em>{{ activeAlgorithm.description }}</em>
          </div>
          <div class="capability-actions">
            <el-button type="primary" @click="router.push(activeAlgorithm.target)">{{ activeAlgorithm.targetLabel }}</el-button>
          </div>
        </div>
        <div class="capability-summary-grid">
          <article>
            <span>输入数据</span>
            <strong>{{ activeAlgorithm.inputs.join(' / ') }}</strong>
          </article>
          <article>
            <span>输出结果</span>
            <strong>{{ activeAlgorithm.outputs.join(' / ') }}</strong>
          </article>
          <article>
            <span>服务页面</span>
            <strong>{{ activeAlgorithm.usedBy }}</strong>
          </article>
          <article>
            <span>算法类型</span>
            <strong>{{ activeAlgorithm.category }}</strong>
          </article>
        </div>
      </section>

      <section class="capability-detail-grid">
        <article class="soc-panel detail-section">
          <div class="panel-title">
            <div>
              <strong>工作原理</strong>
              <span>算法如何从多源数据生成可解释结论</span>
            </div>
          </div>
          <ol class="detail-list ordered">
            <li v-for="step in activeAlgorithm.principles" :key="step">{{ step }}</li>
          </ol>
        </article>

        <article class="soc-panel detail-section">
          <div class="panel-title">
            <div>
              <strong>产生结果</strong>
              <span>算法进入业务页面后形成的指标和对象</span>
            </div>
          </div>
          <ul class="detail-list">
            <li v-for="result in activeAlgorithm.results" :key="result">{{ result }}</li>
          </ul>
        </article>

        <article class="soc-panel detail-section wide">
          <div class="panel-title">
            <div>
              <strong>算法关联与关联方式</strong>
              <span>该算法如何连接证据、能力域和处置页面</span>
            </div>
          </div>
          <div class="relation-grid">
            <section v-for="relation in activeAlgorithm.relations" :key="relation.title">
              <em>{{ relation.linkedTo }}</em>
              <strong>{{ relation.title }}</strong>
              <span>{{ relation.description }}</span>
            </section>
          </div>
        </article>

        <article class="soc-panel detail-section wide">
          <div class="panel-title">
            <div>
              <strong>在本系统中怎么使用</strong>
              <span>分析员看到该算法结果后的操作路径</span>
            </div>
          </div>
          <div class="usage-grid">
            <section v-for="usage in activeAlgorithm.systemUse" :key="usage.title">
              <strong>{{ usage.title }}</strong>
              <span>{{ usage.description }}</span>
            </section>
          </div>
        </article>
      </section>
    </template>

    <template v-else>
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

      <el-alert
        v-if="unknownCapabilityKey"
        title="未找到对应能力详情，已回到能力地图。"
        type="warning"
        show-icon
        :closable="false"
      />

      <section class="soc-panel integration-panel">
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
            :data-return-focus-key="item.key"
            :aria-label="`查看 ${item.name} 详情`"
            :title="`查看 ${item.name} 详情`"
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
            <span class="integration-action">查看详情</span>
          </article>
        </div>
      </section>

      <section class="soc-panel algorithm-panel">
        <div class="panel-title">
          <div>
            <strong>算法能力卡片</strong>
            <span>展示平台如何把证据转成评分、关联、降噪和处置优先级</span>
          </div>
          <el-tag effect="plain">输入 → 计算 → 详情说明</el-tag>
        </div>
        <div class="algorithm-grid">
          <article
            v-for="algorithm in algorithmCards"
            :key="algorithm.code"
            class="algorithm-card"
            :class="algorithm.tone"
            role="link"
            tabindex="0"
            :data-return-focus-key="algorithm.key"
            :aria-label="`查看 ${algorithm.name} 详情`"
            :title="`查看 ${algorithm.name} 详情`"
            @click="openAlgorithm(algorithm)"
            @keydown.enter.prevent="openAlgorithm(algorithm)"
            @keydown.space.prevent="openAlgorithm(algorithm)"
          >
            <div class="algorithm-head">
              <span>{{ algorithm.code }}</span>
              <em>{{ algorithm.category }}</em>
            </div>
            <strong>{{ algorithm.name }}</strong>
            <p>{{ algorithm.description }}</p>
            <div class="algorithm-flow">
              <section>
                <b>输入</b>
                <span>{{ algorithm.inputs.join(' / ') }}</span>
              </section>
              <section>
                <b>输出</b>
                <span>{{ algorithm.outputs.join(' / ') }}</span>
              </section>
            </div>
            <footer>
              <span>{{ algorithm.usedBy }}</span>
              <b>查看详情</b>
            </footer>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

interface CapabilityUsage {
  title: string
  description: string
}

interface CapabilityRelation {
  linkedTo: string
  title: string
  description: string
}

interface IntegrationCard {
  key: string
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
  useLabel: string
  useTarget: RouteLocationRaw
  localPath: string
  principles: string[]
  results: string[]
  relations: CapabilityRelation[]
  systemUse: CapabilityUsage[]
}

interface AlgorithmCard {
  key: string
  code: string
  name: string
  category: string
  description: string
  inputs: string[]
  outputs: string[]
  usedBy: string
  target: RouteLocationRaw
  targetLabel: string
  tone: string
  principles: string[]
  results: string[]
  relations: CapabilityRelation[]
  systemUse: CapabilityUsage[]
}

const router = useRouter()
const route = useRoute()

const capabilityKey = computed(() => routeParam(route.params.capabilityKey))
const activeCapability = computed(() => integrationCards.find((item) => item.key === capabilityKey.value))
const activeAlgorithm = computed(() => algorithmCards.find((item) => item.key === capabilityKey.value))
const unknownCapabilityKey = computed(() => Boolean(capabilityKey.value && !activeCapability.value && !activeAlgorithm.value))

const integrationCards: IntegrationCard[] = [
  {
    key: 'host-threat',
    code: '01',
    shortName: 'HT',
    name: '主机威胁中心',
    role: '终端 / 主机安全',
    description: '聚合登录、配置变更、文件完整性与主机告警。',
    engines: ['Wazuh', 'FIM'],
    status: 'ready',
    statusLabel: '运行中',
    metricLabel: '告警',
    metric: '12,832',
    tone: 'warm',
    useLabel: '进入告警中心',
    useTarget: { path: '/soc/alerts', query: { adapter: 'wazuh' } },
    localPath: 'integrations/wazuh-sec-soc',
    principles: [
      '采集终端登录、进程、配置变更、文件完整性和主机告警记录。',
      '把 Wazuh、FIM 等主机侧字段归一化为资产、账号、事件类型、严重级别和原始证据。',
      '按资产和规则命中结果生成统一告警，并参与资产风险评分和事件簇关联。',
    ],
    results: ['主机侧 SOC 告警', '文件完整性变更记录', '资产风险因子', '可转工单的处置证据'],
    relations: [
      { linkedTo: '告警中心', title: '主机事件转统一告警', description: '按规则命中、资产和严重级别把 Wazuh/FIM 事件汇入告警列表。' },
      { linkedTo: '资产风险', title: '主机证据参与风险评分', description: '登录异常、配置变更和文件完整性记录会作为资产风险分值的输入。' },
      { linkedTo: '事件簇', title: '同资产证据合并', description: '相同资产上的主机、网络、漏洞和 IOC 证据按时间窗口合并研判。' },
    ],
    systemUse: [
      { title: '告警研判', description: '在告警中心按 Wazuh 或主机资产过滤，确认是否为真实异常。' },
      { title: '资产复核', description: '在资产风险和员工终端态势中查看主机风险来源。' },
      { title: '处置闭环', description: '将主机告警转工单，跟踪责任人、SLA 和处理结果。' },
    ],
  },
  {
    key: 'network-detection',
    code: '02',
    shortName: 'ND',
    name: '网络检测中心',
    role: '流量 / IDS 分析',
    description: '把连接日志和 IDS EVE 交融成统一网络事件。',
    engines: ['Zeek', 'Suricata'],
    status: 'ready',
    statusLabel: '运行中',
    metricLabel: '事件',
    metric: '17,040',
    tone: 'cyan',
    useLabel: '进入证据中心',
    useTarget: { path: '/soc/external-events', query: { domain: 'network' } },
    localPath: 'integrations/zeek-traffic-platform + suricata-ids-console',
    principles: [
      '接收 Zeek 连接日志和 Suricata EVE 告警，保留五元组、协议、规则和时间线。',
      '将网络记录映射为外部事件，再按源/目的资产、规则和 IOC 进行关联。',
      '高风险 IDS 命中会升级为统一告警，支撑网络侧溯源和横向移动判断。',
    ],
    results: ['网络外部事件', 'IDS 统一告警', '事件时间线', '网络侧资产风险证据'],
    relations: [
      { linkedTo: '证据中心', title: '网络日志转外部事件', description: 'Zeek/Suricata 原始记录以 sourceType、五元组和规则 ID 进入统一证据。' },
      { linkedTo: '告警中心', title: '高危 IDS 命中升级', description: '高严重级别网络命中会按资产和规则生成 SOC 告警。' },
      { linkedTo: '事件簇', title: '网络会话关联攻击链', description: '源/目的资产、IOC、端口和时间窗口用于连接主机与漏洞证据。' },
    ],
    systemUse: [
      { title: '证据检索', description: '在证据中心按 network、zeek 或 suricata 查看原始记录。' },
      { title: '告警关联', description: '由网络事件进入事件簇，和主机、漏洞、情报证据一起研判。' },
      { title: '报告引用', description: '安全验证报告会引用网络证据说明攻击链路和阻断结果。' },
    ],
  },
  {
    key: 'exposure-management',
    code: '03',
    shortName: 'VM',
    name: '漏洞与暴露面',
    role: '漏洞 / Web 风险',
    description: '融合镜像漏洞、软件包风险和 Web 扫描发现。',
    engines: ['Trivy', 'ZAP'],
    status: 'ready',
    statusLabel: '运行中',
    metricLabel: '风险',
    metric: '4,254',
    tone: 'blue',
    useLabel: '进入漏洞中心',
    useTarget: { path: '/soc/vulnerabilities', query: { domain: 'exposure' } },
    localPath: 'integrations/trivy-platform + zap-authorized-scan-platform',
    principles: [
      '导入 Trivy 依赖漏洞、镜像风险和 ZAP Web 风险发现。',
      '统一 CVE、软件包、URL、资产和修复建议字段，形成可排序的风险条目。',
      '按严重级别、资产暴露面和是否存在告警证据计算修复优先级。',
    ],
    results: ['漏洞台账', 'Web 风险发现', '资产暴露面评分', '修复建议和验证证据'],
    relations: [
      { linkedTo: '漏洞中心', title: '扫描发现进入漏洞台账', description: 'Trivy/ZAP 结果按 CVE、包、URL、资产和状态归档。' },
      { linkedTo: '资产风险', title: '暴露面拉高资产优先级', description: '严重漏洞、外部暴露和关联告警会共同影响资产风险分数。' },
      { linkedTo: '报告中心', title: '修复建议进入验证报告', description: '漏洞证据、受影响组件和建议动作会进入安全验证或运营报告。' },
    ],
    systemUse: [
      { title: '漏洞排查', description: '在漏洞中心查看受影响资产、CVE、包版本和状态流转。' },
      { title: '资产优先级', description: '在资产风险中把漏洞、告警和责任人合并排序。' },
      { title: '验收报告', description: '安全验证流程会把漏洞发现和修复建议写入报告。' },
    ],
  },
  {
    key: 'threat-intel',
    code: '04',
    shortName: 'TI',
    name: '威胁情报中心',
    role: 'IOC / 关联命中',
    description: '管理 IOC、情报命中和告警优先级提升。',
    engines: ['MISP'],
    status: 'ready',
    statusLabel: '运行中',
    metricLabel: 'IOC',
    metric: '4,128',
    tone: 'blue',
    useLabel: '进入证据中心',
    useTarget: { path: '/soc/external-events', query: { sourceType: 'misp' } },
    localPath: 'integrations/misp-deploy',
    principles: [
      '接入 MISP IOC、标签、事件来源和情报上下文。',
      '把 IP、域名、URL、文件哈希等 IOC 和现有告警、网络事件、主机记录做匹配。',
      '命中情报后提升告警优先级，并补充来源、置信度和关联解释。',
    ],
    results: ['IOC 证据记录', '情报命中事件', '告警优先级提升', '事件簇关联线索'],
    relations: [
      { linkedTo: '证据中心', title: 'IOC 作为外部情报证据', description: 'MISP IOC 保留来源、标签、置信度和命中上下文。' },
      { linkedTo: '告警中心', title: '情报命中提升优先级', description: 'IP、域名、URL 或哈希命中 IOC 后会补充告警解释并提高处置顺序。' },
      { linkedTo: '事件簇', title: '同 IOC 跨资产聚合', description: '同一 IOC 命中的不同资产会被聚合成可复盘的攻击线索。' },
    ],
    systemUse: [
      { title: '情报检索', description: '在证据中心按 MISP 来源查看 IOC 和命中上下文。' },
      { title: '告警增强', description: '告警中心会展示情报来源，帮助判断是否需要优先处置。' },
      { title: '事件关联', description: '事件簇会把同一 IOC 关联到不同资产和时间线。' },
    ],
  },
  {
    key: 'detection-rules',
    code: '05',
    shortName: 'DR',
    name: '检测内容规则设置',
    role: '规则 / 命中预览',
    description: '沉淀检测内容、字段映射和发布状态，形成可追溯的告警生成依据。',
    engines: ['Sigma', 'Rule'],
    status: 'active',
    statusLabel: '规则池',
    metricLabel: '规则',
    metric: '1,248',
    tone: 'warm',
    useLabel: '进入检测内容规则设置',
    useTarget: { path: '/soc/rules' },
    localPath: 'integrations/sigma-manager',
    principles: [
      '维护 Sigma、字段匹配、规则标签和适配器映射。',
      '对导入事件执行规则命中预览，校验字段是否能稳定生成告警。',
      '将已发布规则的命中结果与统一告警等级关联，形成可复核的告警依据。',
    ],
    results: ['检测规则台账', '命中预览', '告警生成依据', '发布版本记录'],
    relations: [
      { linkedTo: '告警中心', title: '规则命中生成告警依据', description: '事件字段匹配 Sigma 或本地规则后形成告警原因和命中解释。' },
      { linkedTo: '治理策略', title: '规则变更可审计', description: '规则、字段映射和发布状态变更会保留命中预览与审计上下文。' },
    ],
    systemUse: [
      { title: '规则维护', description: '在检测内容规则设置中维护检测内容、等级、发布状态和命中结果。' },
      { title: '审计追踪', description: '规则调整后可以通过命中预览验证是否影响告警质量。' },
    ],
  },
  {
    key: 'field-analysis',
    code: '06',
    shortName: 'FA',
    name: '字段分析工作台',
    role: '字段 / IOC 解析',
    description: '对 URL、IP、编码字段和可疑载荷做分析辅助。',
    engines: ['CyberChef'],
    status: 'active',
    statusLabel: '可用',
    metricLabel: '分析',
    metric: '860',
    tone: 'cyan',
    useLabel: '进入证据中心',
    useTarget: { path: '/soc/external-events', query: { tool: 'cyberchef' } },
    localPath: 'integrations/cyberchef-deploy',
    principles: [
      '抽取 URL、IP、Base64、编码字段、User-Agent 和可疑载荷片段。',
      '通过分析工作台对字段做解码、拆分、IOC 提取和上下文补充。',
      '把分析结果回填到证据解释和告警详情，减少人工复制粘贴。',
    ],
    results: ['字段解析结果', 'IOC 候选值', '证据解释文本', '告警详情补充信息'],
    relations: [
      { linkedTo: '证据中心', title: '原始字段补充解释', description: '对 URL、编码字段和载荷片段做解析后回填证据说明。' },
      { linkedTo: '威胁情报中心', title: '解析 IOC 进入命中链路', description: '从字段中提取 IP、域名、哈希后可继续做情报匹配。' },
      { linkedTo: '报告中心', title: '可解释字段进入报告', description: '关键字段的解码结果会写入工单备注或验证报告。' },
    ],
    systemUse: [
      { title: '字段复核', description: '在证据中心查看原始事件时，对可疑字段做解析辅助。' },
      { title: 'IOC 提取', description: '把解析出的 IP、域名或哈希用于情报匹配和事件关联。' },
      { title: '报告说明', description: '将解码后的关键字段写入验证报告或工单备注。' },
    ],
  },
  {
    key: 'automation',
    code: '07',
    shortName: 'SO',
    name: '自动化编排中心',
    role: '通知 / SOAR 流程',
    description: '把告警、工单、通知和演示流程串成闭环。',
    engines: ['Shuffle', 'Webhook'],
    status: 'active',
    statusLabel: '可用',
    metricLabel: '流程',
    metric: '24',
    tone: 'warm',
    useLabel: '进入接入与诊断设置',
    useTarget: { path: '/soc/settings', query: { section: 'notifications' } },
    localPath: 'integrations/shuffle-examples',
    principles: [
      '监听告警、工单、报告生成和安全验证流程中的关键节点。',
      '通过 Webhook 或 Shuffle dry-run 流程写入通知日志和编排结果。',
      '默认不发送真实外部通知，先保留可审计的 dry-run 记录。',
    ],
    results: ['编排执行记录', 'dry-run 通知日志', '工单和报告联动记录', '自动化状态诊断'],
    relations: [
      { linkedTo: '工单中心', title: '告警和事件触发流程节点', description: '转工单、状态变化和超时节点会触发编排记录。' },
      { linkedTo: '报告中心', title: '报告生成后写入通知日志', description: '报告完成、导出和安全验证节点会生成 dry-run 通知记录。' },
      { linkedTo: '系统日志', title: 'Webhook 执行结果留痕', description: 'Shuffle/Webhook 的请求、状态和失败原因会进入诊断记录。' },
    ],
    systemUse: [
      { title: '通知诊断', description: '在接入与诊断设置中检查通知通道和 dry-run 记录。' },
      { title: '流程串联', description: '安全验证中心会把告警、工单、通知和报告串成工作流。' },
      { title: '审计留痕', description: '所有自动化触发结果保留在系统日志或通知记录中。' },
    ],
  },
  {
    key: 'case-response',
    code: '08',
    shortName: 'CA',
    name: '案件处置中心',
    role: '告警 / 工单 / 报表',
    description: '把统一告警转工单、跟踪 SLA 并生成综合报告。',
    engines: ['Ticket', 'Report'],
    status: 'ready',
    statusLabel: '运行中',
    metricLabel: '工单',
    metric: '212',
    tone: 'blue',
    useLabel: '进入工单中心',
    useTarget: { path: '/soc/tickets' },
    localPath: '00-cyberfusion-platform backend/frontend',
    principles: [
      '把告警、事件簇、漏洞和资产风险转化为可分派的处置任务。',
      '跟踪状态、责任人、SLA、时间线、员工待办和处置证据。',
      '把处置结果、风险摘要和推荐动作汇总成安全运营或安全验证报告。',
    ],
    results: ['工单记录', '处置时间线', 'SLA 指标', '安全运营报告和验证报告'],
    relations: [
      { linkedTo: '告警中心', title: '告警转工单', description: '确认后的告警会带着资产、证据和严重级别进入工单队列。' },
      { linkedTo: '事件簇', title: '事件簇转处置任务', description: '多源证据聚合后可生成一张包含攻击链上下文的处置工单。' },
      { linkedTo: '报告中心', title: '处置结果汇总报告', description: '工单状态、SLA、推荐动作和员工待办会汇总为运营报告。' },
    ],
    systemUse: [
      { title: '工单处理', description: '在工单中心分派负责人、推进状态并补充处置证据。' },
      { title: '报告生成', description: '在报告中心生成周报、月报或安全验证报告。' },
      { title: '闭环复盘', description: '从工作台查看告警、工单、推荐动作和员工待办完成情况。' },
    ],
  },
]

const algorithmCards: AlgorithmCard[] = [
  {
    key: 'algorithm-risk-scoring',
    code: 'ALG-01',
    name: '资产风险评分',
    category: 'Risk Scoring',
    description: '把告警、漏洞、基线和员工待办统一折算为资产风险分值，支撑优先处置排序。',
    inputs: ['告警严重级别', '漏洞暴露面', '基线结果', '员工待办'],
    outputs: ['资产风险分', '优先级', '推荐动作'],
    usedBy: '资产风险 / 工作台 / 报告中心',
    target: { path: '/soc/assets' },
    targetLabel: '进入资产风险',
    tone: 'warm',
    principles: [
      '读取告警、漏洞、基线、员工待办和资产重要性，把不同来源统一映射为风险因子。',
      '按严重级别、未关闭状态、影响资产、时间窗口和重复证据计算权重。',
      '输出风险分值、分值来源和推荐动作，避免只给不可解释的数字。',
    ],
    results: ['资产风险分', '风险等级', '风险来源解释', '推荐处置动作'],
    relations: [
      { linkedTo: '资产风险', title: '分值落到资产视图', description: '算法结果按资产聚合，用于资产列表、资产详情和风险排序。' },
      { linkedTo: '安全运营工作台', title: '驱动今日优先级', description: '高风险资产会影响工作台的待处置资产和优先处理建议。' },
      { linkedTo: '报告中心', title: '形成风险摘要', description: '报告会引用分值、来源因子和处置建议，说明风险变化原因。' },
    ],
    systemUse: [
      { title: '先看高分资产', description: '分析员从资产风险页按分值排序，优先处理高风险资产。' },
      { title: '复核分值来源', description: '进入资产详情查看告警、漏洞、基线等来源因子是否合理。' },
      { title: '转处置动作', description: '把高风险资产关联的告警或漏洞转成工单，进入闭环处理。' },
    ],
  },
  {
    key: 'algorithm-event-correlation',
    code: 'ALG-02',
    name: '事件簇关联',
    category: 'Correlation',
    description: '按资产、IOC、规则命中和时间窗口把多源证据合并为可研判的事件簇。',
    inputs: ['同资产证据', 'IOC 命中', '规则 ID', '时间窗口'],
    outputs: ['事件簇', '关联强度', '转工单建议'],
    usedBy: '安全事件簇 / 告警中心 / 工单中心',
    target: { path: '/soc/incidents' },
    targetLabel: '进入安全事件簇',
    tone: 'cyan',
    principles: [
      '把主机、网络、漏洞和情报证据统一到资产、时间、IOC 和规则维度。',
      '用同资产、近时间窗口、同 IOC、同规则族等条件计算关联强度。',
      '将离散证据合并成事件簇，并给出是否需要转工单的建议。',
    ],
    results: ['事件簇', '关联证据列表', '攻击链上下文', '转工单建议'],
    relations: [
      { linkedTo: '告警中心', title: '告警进入事件簇', description: '确认后的告警会按资产和时间窗口进入关联计算。' },
      { linkedTo: '证据中心', title: '外部事件补充上下文', description: 'Zeek、Suricata、MISP 等证据用于解释事件簇来源。' },
      { linkedTo: '工单中心', title: '簇级别转处置', description: '高关联强度事件簇可生成一张包含完整上下文的处置工单。' },
    ],
    systemUse: [
      { title: '合并重复研判', description: '分析员从事件簇页查看同一资产或同一 IOC 的多源证据。' },
      { title: '判断攻击链', description: '通过关联原因确认是否存在连续攻击路径或误报堆叠。' },
      { title: '生成处置任务', description: '把确认后的事件簇转工单，减少逐条告警处理成本。' },
    ],
  },
  {
    key: 'algorithm-sla-priority',
    code: 'ALG-04',
    name: '处置优先级与 SLA',
    category: 'SLA Priority',
    description: '结合严重级别、资产风险、工单状态和超时窗口，生成今日优先处理队列。',
    inputs: ['严重级别', '资产分值', '工单状态', 'SLA 截止时间'],
    outputs: ['处置队列', '超时风险', '报告摘要'],
    usedBy: '工单中心 / 我的待办 / 报告中心',
    target: { path: '/soc/tickets' },
    targetLabel: '进入工单中心',
    tone: 'warm',
    principles: [
      '把告警严重级别、资产风险分、工单状态和 SLA 截止时间转成统一排序因子。',
      '对即将超时、影响核心资产、仍未归档的事项提高优先级。',
      '输出今日处置队列、超时风险和可写入报告的摘要口径。',
    ],
    results: ['今日处置队列', 'SLA 超时风险', '待办优先级', '报告摘要指标'],
    relations: [
      { linkedTo: '工单中心', title: '工单排序依据', description: '算法结果决定工单列表中优先处理和超时提醒的展示顺序。' },
      { linkedTo: '我的待办', title: '员工任务同步', description: '员工侧待办会接收和展示与自身相关的优先任务。' },
      { linkedTo: '安全运营工作台', title: '形成今日建议', description: '工作台优先处理建议会引用该算法的排序结果。' },
    ],
    systemUse: [
      { title: '先处理超时风险', description: '运营人员优先查看即将超时或影响核心资产的工单。' },
      { title: '协调责任人', description: '根据员工待办和工单责任人推进处置状态。' },
      { title: '沉淀复盘指标', description: '报告中心会引用关闭率、超时数和推荐动作采纳情况。' },
    ],
  },
  {
    key: 'algorithm-ioc-extraction',
    code: 'ALG-05',
    name: '字段 IOC 提取',
    category: 'IOC Extraction',
    description: '从 URL、载荷、编码字段和日志文本中提取可复核 IOC，补充情报命中链路。',
    inputs: ['URL', 'IP/域名', '哈希', '编码载荷'],
    outputs: ['IOC 候选', '解析说明', '证据补充'],
    usedBy: '证据中心 / 威胁情报中心 / 报告中心',
    target: { path: '/soc/external-events', query: { tool: 'cyberchef' } },
    targetLabel: '进入证据中心',
    tone: 'cyan',
    principles: [
      '从原始事件字段中识别 URL、IP、域名、哈希、Base64 和可疑载荷片段。',
      '对编码字段进行解码和拆分，提取可复核的 IOC 候选值。',
      '将 IOC 候选回填到证据解释，并触发情报命中和事件关联。',
    ],
    results: ['IOC 候选列表', '字段解析说明', '情报匹配输入', '报告证据补充'],
    relations: [
      { linkedTo: '证据中心', title: '解析结果回填证据', description: 'IOC 候选和解析说明跟随原始事件展示。' },
      { linkedTo: '威胁情报中心', title: 'IOC 进入情报命中', description: '提取出的 IP、域名、URL 或哈希会参与 MISP 命中。' },
      { linkedTo: '事件簇', title: '同 IOC 串联多源证据', description: '相同 IOC 可以把主机、网络和告警证据合并到同一事件簇。' },
    ],
    systemUse: [
      { title: '解析可疑字段', description: '分析员在证据中心查看原始字段时使用解析结果减少人工判断。' },
      { title: '补充情报命中', description: '提取出的 IOC 可继续用于情报中心匹配和优先级提升。' },
      { title: '写入报告证据', description: '报告中心可引用解码后的字段和 IOC 作为可解释证据。' },
    ],
  },
]

function openIntegration(item: IntegrationCard) {
  router.push({ path: `/soc/capabilities/${item.key}` })
}

function openAlgorithm(item: AlgorithmCard) {
  router.push({ path: `/soc/capabilities/${item.key}` })
}

function routeParam(value: unknown) {
  return Array.isArray(value) ? value[0] : typeof value === 'string' ? value : ''
}
</script>

<style scoped>
.integration-panel,
.algorithm-panel {
  min-width: 0;
  padding: 16px;
}

.capability-detail-panel,
.detail-section {
  min-width: 0;
  padding: 16px;
}

.capability-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.capability-title-stack {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.capability-title-stack span {
  color: var(--soc-warm-strong);
  font-size: 12px;
  font-weight: 760;
}

.capability-title-stack strong {
  color: var(--soc-text);
  font-size: 24px;
  line-height: 1.15;
}

.capability-title-stack em {
  color: var(--soc-text-muted);
  font-size: 13px;
  font-style: normal;
}

.capability-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.capability-summary-grid,
.capability-detail-grid,
.usage-grid,
.relation-grid,
.algorithm-grid {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.capability-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.capability-detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.capability-summary-grid article,
.usage-grid section,
.relation-grid section,
.algorithm-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.capability-summary-grid span,
.usage-grid span,
.relation-grid span,
.relation-grid em,
.detail-list li {
  color: var(--soc-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.capability-summary-grid strong,
.usage-grid strong,
.relation-grid strong,
.algorithm-card strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--soc-text);
  font-size: 14px;
}

.detail-section.wide {
  grid-column: 1 / -1;
}

.detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 18px;
}

.detail-list li::marker {
  color: var(--soc-warm-strong);
  font-weight: 800;
}

.usage-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.relation-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.relation-grid em {
  width: fit-content;
  padding: 2px 7px;
  border: 1px solid rgba(57, 120, 216, 0.18);
  border-radius: 6px;
  background: rgba(239, 246, 255, 0.66);
  color: var(--soc-blue);
  font-style: normal;
  font-weight: 760;
}

.algorithm-grid {
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.algorithm-card {
  position: relative;
  align-content: start;
  min-height: 218px;
  border-left: 3px solid rgba(212, 147, 74, 0.78);
  scroll-margin-top: 112px;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.algorithm-card:hover,
.algorithm-card:focus-visible {
  border-color: rgba(212, 147, 74, 0.66);
  border-left-color: var(--soc-warm-strong);
  box-shadow: 0 16px 34px rgba(91, 77, 53, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  outline: 0;
  transform: translateY(-1px);
}

.algorithm-card.cyan {
  border-left-color: rgba(16, 179, 199, 0.78);
}

.algorithm-card.blue {
  border-left-color: rgba(57, 120, 216, 0.78);
}

.algorithm-head,
.algorithm-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.algorithm-head span,
.algorithm-head em {
  color: var(--soc-text-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 760;
}

.algorithm-head em {
  overflow: hidden;
  padding: 2px 6px;
  border: 1px solid rgba(179, 173, 163, 0.36);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.62);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.algorithm-card p {
  margin: 0;
  color: var(--soc-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.algorithm-flow {
  display: grid;
  gap: 6px;
}

.algorithm-flow section {
  display: grid;
  gap: 3px;
  padding: 8px;
  border-radius: 7px;
  background: rgba(248, 244, 237, 0.48);
}

.algorithm-flow b {
  color: var(--soc-text);
  font-size: 11px;
  font-weight: 760;
}

.algorithm-flow span,
.algorithm-card footer span {
  color: var(--soc-text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.algorithm-card footer {
  margin-top: auto;
}

.algorithm-card footer b {
  flex: 0 0 auto;
  color: var(--soc-warm-strong);
  font-size: 11px;
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
  scroll-margin-top: 112px;
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

@media (max-width: 1280px) {
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
  .integration-grid {
    grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  }

  .capability-detail-head {
    align-items: stretch;
    flex-direction: column;
  }

  .capability-actions {
    justify-content: flex-start;
  }

  .capability-summary-grid,
  .capability-detail-grid,
  .usage-grid,
  .relation-grid,
  .algorithm-grid {
    grid-template-columns: 1fr;
  }

  .detail-section.wide {
    grid-column: auto;
  }
}
</style>
