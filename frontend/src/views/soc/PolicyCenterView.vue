<template>
  <div class="page-shell policy-center-page">
    <section class="soc-page-hero">
      <div>
        <span class="soc-page-kicker">POLICY & RULE CENTER</span>
        <h1>策略与规则中心</h1>
        <p>这个页面帮助安全工程师维护本机只读检查策略，并查看事件适配和告警联动规则的治理边界。</p>
      </div>
      <div class="soc-page-tags">
        <el-tag effect="plain">只读检查</el-tag>
        <el-tag effect="plain">安全预检</el-tag>
        <el-tag effect="plain">变更审计</el-tag>
      </div>
    </section>

    <el-alert
      title="本页面只维护只读检查命令，不支持任意 shell，不允许攻击、扫描、写文件或外部访问。"
      type="warning"
      show-icon
      :closable="false"
      class="policy-alert"
    />

    <el-tabs v-model="activeTab" class="policy-tabs">
      <el-tab-pane label="本机检查策略" name="local-check">
        <section class="soc-panel panel-pad">
          <div class="soc-filter-bar">
            <el-input v-model="query.keyword" clearable placeholder="搜索名称、commandKey、说明" @keyup.enter="loadPolicies" />
            <el-select v-model="query.osType" clearable placeholder="OS">
              <el-option label="Linux" value="Linux" />
              <el-option label="macOS" value="macOS" />
              <el-option label="Windows" value="Windows" />
            </el-select>
            <el-select v-model="query.status" clearable placeholder="状态">
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="active" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button @click="loadPolicies">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
            <el-button v-permission="'soc:policy:create'" type="primary" @click="openCreate">新增策略</el-button>
          </div>
        </section>

        <section class="table-panel">
          <el-alert
            v-if="localPolicyFallback"
            title="正在使用内置默认策略"
            description="后端策略表暂不可用或尚未初始化，页面已回退展示只读安全默认策略。内置策略只能查看，不能编辑、发布或停用。"
            type="info"
            show-icon
            :closable="false"
            class="policy-alert"
          />
          <el-table v-loading="loading" :data="rows" empty-text="暂无本机检查策略">
            <el-table-column prop="displayName" label="命令名称" min-width="180" />
            <el-table-column prop="osType" label="OS" width="96" />
            <el-table-column prop="category" label="分类" width="130" />
            <el-table-column prop="commandKey" label="commandKey" min-width="150" />
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="86">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'soc:policy:update'" text :disabled="isFallbackPolicy(row)" @click="openEdit(row)">编辑</el-button>
                <el-button text :disabled="isFallbackPolicy(row)" @click="precheck(row)">预检</el-button>
                <el-button v-permission="'soc:policy:publish'" text :disabled="isFallbackPolicy(row)" @click="publish(row)">发布</el-button>
                <el-button v-permission="'soc:policy:disable'" text :disabled="isFallbackPolicy(row)" :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ total }} 条策略</span>
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @change="loadPolicies"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="事件适配映射" name="adapter">
        <el-alert
          title="事件适配映射只支持字段路径、固定转换和占位符模板；不支持脚本、表达式、SQL、正则执行或外部调用。"
          type="info"
          show-icon
          :closable="false"
          class="policy-alert"
        />

        <section class="soc-panel panel-pad">
          <div class="soc-filter-bar">
            <el-input v-model="adapterQuery.keyword" clearable placeholder="搜索来源、名称、说明" @keyup.enter="loadAdapters" />
            <el-select v-model="adapterQuery.sourceType" clearable placeholder="sourceType">
              <el-option v-for="source in adapterSources" :key="source" :label="source" :value="source" />
            </el-select>
            <el-select v-model="adapterQuery.status" clearable placeholder="状态">
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="active" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button @click="loadAdapters">查询</el-button>
            <el-button @click="resetAdapterFilters">重置</el-button>
            <el-button v-permission="'soc:policy:create'" type="primary" @click="openAdapterCreate">新增适配器</el-button>
          </div>
        </section>

        <section class="table-panel">
          <el-table v-loading="adapterLoading" :data="adapterRows" empty-text="暂无事件适配映射">
            <el-table-column prop="sourceType" label="sourceType" width="120" />
            <el-table-column prop="displayName" label="适配器名称" min-width="190" />
            <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="86">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="sampleFile" label="样例文件" min-width="230" show-overflow-tooltip />
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
            <el-table-column label="操作" width="340" fixed="right">
              <template #default="{ row }">
                <el-button text @click="openAdapterDetail(row)">映射</el-button>
                <el-button v-permission="'soc:policy:update'" text @click="openAdapterEdit(row)">编辑</el-button>
                <el-button text @click="validateAdapterRow(row)">校验</el-button>
                <el-button v-permission="'soc:policy:publish'" text @click="publishAdapterRow(row)">发布</el-button>
                <el-button v-permission="'soc:policy:disable'" text type="warning" @click="disableAdapterRow(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ adapterTotal }} 条适配器</span>
            <el-pagination
              v-model:current-page="adapterQuery.pageNum"
              v-model:page-size="adapterQuery.pageSize"
              layout="total, sizes, prev, pager, next"
              :total="adapterTotal"
              @change="loadAdapters"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="处置剧本" name="playbook">
        <el-alert
          title="处置剧本只生成建议、工单任务和时间线，不支持脚本、攻击、扫描、写文件、自动修复或外部系统调用。"
          type="warning"
          show-icon
          :closable="false"
          class="policy-alert"
        />
        <section class="soc-panel panel-pad">
          <div class="soc-filter-bar">
            <el-input v-model="playbookQuery.keyword" clearable placeholder="搜索剧本名称、编码、说明" @keyup.enter="loadPlaybooks" />
            <el-select v-model="playbookQuery.sourceType" clearable placeholder="sourceType">
              <el-option v-for="source in [...adapterSources, 'suricata,zeek']" :key="source" :label="source" :value="source" />
            </el-select>
            <el-select v-model="playbookQuery.status" clearable placeholder="状态">
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="active" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button @click="loadPlaybooks">查询</el-button>
            <el-button @click="resetPlaybookFilters">重置</el-button>
            <el-button v-permission="'soc:policy:create'" type="primary" @click="openPlaybookCreate">新增剧本</el-button>
          </div>
        </section>
        <section class="table-panel">
          <el-table v-loading="playbookLoading" :data="playbookRows" empty-text="暂无处置剧本">
            <el-table-column prop="playbookName" label="剧本名称" min-width="190" />
            <el-table-column prop="playbookKey" label="剧本编码" min-width="150" />
            <el-table-column prop="sourceType" label="来源" width="120" />
            <el-table-column prop="eventType" label="事件类型" min-width="170" show-overflow-tooltip />
            <el-table-column prop="minSeverity" label="最低等级" width="100" />
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button text @click="openPlaybookDetail(row)">详情</el-button>
                <el-button v-permission="'soc:policy:update'" text @click="openPlaybookEdit(row)">编辑</el-button>
                <el-button text @click="validatePlaybookRow(row)">校验</el-button>
                <el-button v-permission="'soc:policy:publish'" text @click="publishPlaybookRow(row)">发布</el-button>
                <el-button v-permission="'soc:policy:disable'" text type="warning" @click="disablePlaybookRow(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ playbookTotal }} 条剧本</span>
            <el-pagination
              v-model:current-page="playbookQuery.pageNum"
              v-model:page-size="playbookQuery.pageSize"
              layout="total, sizes, prev, pager, next"
              :total="playbookTotal"
              @change="loadPlaybooks"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="事件关联规则" name="correlation">
        <el-alert
          title="事件关联规则只支持结构化字段、阈值和时间窗口，不支持脚本、外部查询、扫描或自动修复。"
          type="warning"
          show-icon
          :closable="false"
          class="policy-alert"
        />
        <section class="soc-panel panel-pad">
          <div class="soc-filter-bar">
            <el-input v-model="correlationQuery.keyword" clearable placeholder="搜索规则编码、名称" @keyup.enter="loadCorrelationRules" />
            <el-select v-model="correlationQuery.type" clearable placeholder="规则类型">
              <el-option label="event_count" value="event_count" />
              <el-option label="value_count" value="value_count" />
              <el-option label="frequency" value="frequency" />
              <el-option label="temporal" value="temporal" />
              <el-option label="temporal_ordered" value="temporal_ordered" />
              <el-option label="cross_source_chain" value="cross_source_chain" />
            </el-select>
            <el-select v-model="correlationQuery.status" clearable placeholder="状态">
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="active" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button @click="loadCorrelationRules">查询</el-button>
            <el-button @click="resetCorrelationFilters">重置</el-button>
            <el-button v-permission="'soc:correlation-rule:create'" type="primary" @click="openCorrelationCreate">新增规则</el-button>
          </div>
        </section>
        <section class="table-panel">
          <el-table v-loading="correlationLoading" :data="correlationRows" empty-text="暂无事件关联规则">
            <el-table-column prop="ruleName" label="规则名称" min-width="190" />
            <el-table-column prop="ruleKey" label="规则编码" min-width="170" />
            <el-table-column prop="ruleType" label="类型" width="160" />
            <el-table-column prop="threshold" label="阈值" width="80" />
            <el-table-column prop="timeframeSeconds" label="窗口秒数" width="110" />
            <el-table-column prop="severityFloor" label="最低等级" width="100" />
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="86">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'soc:correlation-rule:update'" text @click="openCorrelationEdit(row)">编辑</el-button>
                <el-button text @click="validateCorrelationRow(row)">校验</el-button>
                <el-button v-permission="'soc:correlation-rule:publish'" text @click="publishCorrelationRow(row)">发布</el-button>
                <el-button v-permission="'soc:correlation-rule:disable'" text type="warning" @click="disableCorrelationRow(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ correlationTotal }} 条规则</span>
            <el-pagination
              v-model:current-page="correlationQuery.pageNum"
              v-model:page-size="correlationQuery.pageSize"
              layout="total, sizes, prev, pager, next"
              :total="correlationTotal"
              @change="loadCorrelationRules"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="告警联动规则" name="alert-link">
        <section class="placeholder-panel">
          <h2>告警联动规则</h2>
          <p>当前阶段不开放在线编辑联动规则，避免错误配置造成告警风暴。演示批次仍复用后端强校验的 linkAlerts 逻辑。</p>
          <el-button @click="router.push('/soc/alerts')">进入告警处置</el-button>
        </section>
      </el-tab-pane>

      <el-tab-pane label="风险评分策略" name="risk-scoring">
        <el-alert
          title="风险评分策略只维护数字权重和说明文本，不支持脚本、表达式、扫描、自动修复或外部调用。"
          type="warning"
          show-icon
          :closable="false"
          class="policy-alert"
        />
        <section class="soc-panel panel-pad">
          <div class="soc-filter-bar">
            <el-input v-model="riskQuery.keyword" clearable placeholder="搜索策略编码、名称、说明" @keyup.enter="loadRiskPolicies" />
            <el-select v-model="riskQuery.status" clearable placeholder="状态">
              <el-option label="草稿" value="draft" />
              <el-option label="已发布" value="active" />
              <el-option label="已停用" value="disabled" />
            </el-select>
            <el-button @click="loadRiskPolicies">查询</el-button>
            <el-button @click="resetRiskFilters">重置</el-button>
            <el-button v-permission="'soc:risk-policy:create'" type="primary" @click="openRiskCreate">新增策略</el-button>
            <el-button v-permission="'soc:risk-score:recalculate'" @click="recalculateAllRisks">重新计算全部资产</el-button>
          </div>
        </section>
        <section class="table-panel">
          <el-table v-loading="riskLoading" :data="riskRows" empty-text="暂无风险评分策略">
            <el-table-column prop="policyName" label="策略名称" min-width="180" />
            <el-table-column prop="policyCode" label="策略编码" min-width="170" />
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="criticalAlertWeight" label="严重告警" width="100" />
            <el-table-column prop="criticalVulnerabilityWeight" label="严重漏洞" width="100" />
            <el-table-column prop="overdueTicketWeight" label="超时工单" width="100" />
            <el-table-column prop="maxScore" label="上限" width="80" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'soc:risk-policy:update'" text @click="openRiskEdit(row)">编辑</el-button>
                <el-button text @click="validateRisk(row)">校验</el-button>
                <el-button v-permission="'soc:risk-policy:publish'" text @click="publishRisk(row)">发布</el-button>
                <el-button v-permission="'soc:risk-policy:disable'" text type="warning" @click="disableRisk(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <span>共 {{ riskTotal }} 条策略</span>
            <el-pagination
              v-model:current-page="riskQuery.pageNum"
              v-model:page-size="riskQuery.pageSize"
              layout="total, sizes, prev, pager, next"
              :total="riskTotal"
              @change="loadRiskPolicies"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="变更审计" name="audit">
        <section class="table-panel">
          <el-table v-loading="auditLoading" :data="auditRows" empty-text="暂无策略变更记录">
            <el-table-column prop="displayName" label="策略" min-width="180" />
            <el-table-column prop="commandKey" label="commandKey" min-width="150" />
            <el-table-column prop="osType" label="OS" width="96" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="updatedBy" label="更新人" width="100" />
            <el-table-column prop="approvedBy" label="发布人" width="100" />
            <el-table-column prop="approvedAt" label="发布时间" min-width="172" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑本机检查策略' : '新增本机检查策略'" width="720px">
      <el-form :model="form" label-width="130px" class="policy-form">
        <el-form-item label="命令名称"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="commandKey"><el-input v-model="form.commandKey" placeholder="identity / network / custom_key" /></el-form-item>
        <el-form-item label="OS">
          <el-segmented v-model="form.osType" :options="['Linux', 'macOS', 'Windows']" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category">
            <el-option label="身份检查" value="identity" />
            <el-option label="网络连接" value="network" />
            <el-option label="进程观察" value="process" />
            <el-option label="启动项" value="startup" />
            <el-option label="主机信息" value="host" />
            <el-option label="自定义只读" value="custom_readonly" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="argv JSON">
          <el-input v-model="form.commandArgvJson" type="textarea" :rows="4" placeholder='["id"]' />
        </el-form-item>
        <el-form-item label="执行限制">
          <div class="inline-controls">
            <el-input-number v-model="form.timeoutSeconds" :min="1" :max="30" />
            <span>秒超时</span>
            <el-input-number v-model="form.outputLimitKb" :min="1" :max="256" />
            <span>KB 输出上限</span>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="form.status" :options="['draft', 'active', 'disabled']" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="10000" /></el-form-item>
        <el-form-item label="安全说明"><el-input v-model="form.safetyNote" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="precheckForm">安全预检</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adapterDialogVisible" :title="editingAdapterId ? '编辑事件适配器' : '新增事件适配器'" width="680px">
      <el-form :model="adapterForm" label-width="120px" class="policy-form">
        <el-form-item label="sourceType">
          <el-select v-model="adapterForm.sourceType" :disabled="Boolean(editingAdapterId)">
            <el-option v-for="source in adapterSources" :key="source" :label="source" :value="source" />
          </el-select>
        </el-form-item>
        <el-form-item label="适配器名称"><el-input v-model="adapterForm.displayName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="adapterForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="adapterForm.status" :options="['draft', 'active', 'disabled']" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="adapterForm.enabled" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="adapterForm.sortOrder" :min="0" :max="10000" /></el-form-item>
        <el-form-item label="样例文件"><el-input v-model="adapterForm.sampleFile" placeholder="demo-data/waf-demo-events.jsonl" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adapterSaving" @click="saveAdapterProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="riskDialogVisible" :title="editingRiskId ? '编辑风险评分策略' : '新增风险评分策略'" width="860px">
      <el-alert
        title="只能配置 0-100 的数字权重。降低项仅用于体现已关闭工单和已完成剧本任务，不会触发自动处置。"
        type="info"
        show-icon
        :closable="false"
        class="policy-alert"
      />
      <el-form :model="riskForm" label-width="140px" class="policy-form">
        <el-form-item label="策略名称"><el-input v-model="riskForm.policyName" /></el-form-item>
        <el-form-item label="策略编码"><el-input v-model="riskForm.policyCode" placeholder="DEFAULT_ASSET_RISK_V1" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="riskForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="riskForm.status" :options="['draft', 'active', 'disabled']" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="riskForm.enabled" /></el-form-item>
        <div class="risk-weight-grid">
          <el-form-item v-for="item in riskWeightFields" :key="item.key" :label="item.label">
            <el-input-number v-model="riskForm[item.key]" :min="0" :max="100" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="riskDialogVisible = false">取消</el-button>
        <el-button v-if="editingRiskId" @click="validateRiskById(editingRiskId)">安全校验</el-button>
        <el-button type="primary" :loading="riskSaving" @click="saveRiskPolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="playbookDialogVisible" :title="editingPlaybookId ? '编辑处置剧本' : '新增处置剧本'" width="820px">
      <el-alert
        title="请只填写人工处置说明和预期证据，不要填写命令、脚本、攻击 payload、扫描或自动修复步骤。"
        type="warning"
        show-icon
        :closable="false"
        class="policy-alert"
      />
      <el-form :model="playbookForm" label-width="120px" class="policy-form">
        <el-form-item label="剧本名称"><el-input v-model="playbookForm.playbookName" /></el-form-item>
        <el-form-item label="剧本编码"><el-input v-model="playbookForm.playbookKey" placeholder="PB-WAF-BLOCK" /></el-form-item>
        <el-form-item label="sourceType"><el-input v-model="playbookForm.sourceType" placeholder="waf / suricata,zeek" /></el-form-item>
        <el-form-item label="eventType"><el-input v-model="playbookForm.eventType" placeholder="waf_block,upload_block 或 *" /></el-form-item>
        <el-form-item label="ruleId"><el-input v-model="playbookForm.ruleIdPattern" placeholder="* 或规则 ID 片段" /></el-form-item>
        <el-form-item label="最低等级">
          <el-segmented v-model="playbookForm.minSeverity" :options="['low', 'medium', 'high', 'critical']" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="playbookForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="playbookForm.status" :options="['draft', 'active', 'disabled']" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="playbookForm.enabled" /></el-form-item>
        <el-form-item label="安全说明"><el-input v-model="playbookForm.safetyNote" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="步骤 JSON">
          <el-input v-model="playbookStepsJson" type="textarea" :rows="12" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="playbookDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="playbookSaving" @click="savePlaybook">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="correlationDialogVisible" :title="editingCorrelationId ? '编辑事件关联规则' : '新增事件关联规则'" width="820px">
      <el-alert
        title="事件关联规则只能配置结构化字段、阈值、时间窗口和来源序列；禁止脚本、表达式、外部查询、扫描或自动修复。"
        type="warning"
        show-icon
        :closable="false"
        class="policy-alert"
      />
      <el-form :model="correlationForm" label-width="130px" class="policy-form">
        <el-form-item label="规则名称"><el-input v-model="correlationForm.ruleName" /></el-form-item>
        <el-form-item label="规则编码"><el-input v-model="correlationForm.ruleKey" placeholder="same_asset_event_count" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="correlationForm.ruleType">
            <el-option label="event_count" value="event_count" />
            <el-option label="value_count" value="value_count" />
            <el-option label="frequency" value="frequency" />
            <el-option label="temporal" value="temporal" />
            <el-option label="temporal_ordered" value="temporal_ordered" />
            <el-option label="cross_source_chain" value="cross_source_chain" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="correlationForm.status" :options="['draft', 'active', 'disabled']" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="correlationForm.enabled" /></el-form-item>
        <div class="risk-weight-grid">
          <el-form-item label="阈值"><el-input-number v-model="correlationForm.threshold" :min="1" :max="1000" /></el-form-item>
          <el-form-item label="窗口秒数"><el-input-number v-model="correlationForm.timeframeSeconds" :min="60" :max="604800" /></el-form-item>
          <el-form-item label="版本"><el-input-number v-model="correlationForm.version" :min="1" :max="999" /></el-form-item>
          <el-form-item label="最低等级">
            <el-select v-model="correlationForm.severityFloor" clearable>
              <el-option label="low" value="low" />
              <el-option label="medium" value="medium" />
              <el-option label="high" value="high" />
              <el-option label="critical" value="critical" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="来源 JSON">
          <el-input v-model="correlationForm.sourceTypesJson" type="textarea" :rows="2" placeholder='["waf","zap","wazuh"]' />
        </el-form-item>
        <el-form-item label="事件类型 JSON">
          <el-input v-model="correlationForm.eventTypesJson" type="textarea" :rows="2" placeholder='["waf_block","web_app_finding"]' />
        </el-form-item>
        <el-form-item label="分组字段 JSON">
          <el-input v-model="correlationForm.groupByJson" type="textarea" :rows="2" placeholder='["assetIp","batchId","demoCaseId"]' />
        </el-form-item>
        <el-form-item label="序列 JSON">
          <el-input v-model="correlationForm.sequenceJson" type="textarea" :rows="2" placeholder='["waf","zap","wazuh"]' />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="correlationForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="安全说明"><el-input v-model="correlationForm.safetyNote" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correlationDialogVisible = false">取消</el-button>
        <el-button v-if="editingCorrelationId" @click="validateCorrelationById(editingCorrelationId)">安全校验</el-button>
        <el-button type="primary" :loading="correlationSaving" @click="saveCorrelationRule">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="adapterDrawerVisible" size="64%" title="事件适配映射详情">
      <template v-if="selectedAdapter">
        <section class="adapter-summary">
          <div>
            <span class="soc-page-kicker">{{ selectedAdapter.sourceType }}</span>
            <h2>{{ selectedAdapter.displayName }}</h2>
            <p>{{ selectedAdapter.description || '暂无说明' }}</p>
          </div>
          <div class="adapter-actions">
            <el-button @click="openMappingsEditor">编辑映射 JSON</el-button>
            <el-button @click="validateAdapterRow(selectedAdapter)">校验配置</el-button>
            <el-button type="primary" @click="previewAdapter">预览归一化</el-button>
          </div>
        </section>

        <el-tabs v-model="adapterDetailTab">
          <el-tab-pane label="字段映射" name="fields">
            <el-table :data="adapterMappings.fieldMappings" size="small" empty-text="暂无字段映射">
              <el-table-column prop="sourceFieldPath" label="source field" min-width="180" />
              <el-table-column prop="normalizedField" label="normalized field" min-width="150" />
              <el-table-column prop="transformType" label="transform" width="140" />
              <el-table-column label="必填" width="80">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="defaultValue" label="默认值" min-width="120" />
              <el-table-column prop="exampleValue" label="样例值" min-width="160" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="严重级别" name="severity">
            <el-table :data="adapterMappings.severityMappings" size="small" empty-text="暂无严重级别映射">
              <el-table-column prop="sourceValue" label="source value" min-width="160" />
              <el-table-column prop="normalizedSeverity" label="severity" width="140" />
              <el-table-column prop="riskScore" label="risk score" width="120" />
              <el-table-column label="启用" width="80">
                <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="告警联动" name="alert">
            <el-table :data="adapterMappings.alertLinkRules" size="small" empty-text="暂无告警联动规则">
              <el-table-column prop="eventType" label="eventType" width="140" />
              <el-table-column prop="minSeverity" label="min severity" width="130" />
              <el-table-column prop="alertRuleIdField" label="ruleId 字段" width="150" />
              <el-table-column prop="alertNameTemplate" label="告警标题模板" min-width="200" />
              <el-table-column prop="dedupKeyFieldsJson" label="dedup key" min-width="240" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="预览" name="preview">
            <div class="preview-grid">
              <el-input v-model="adapterPreviewPayload" type="textarea" :rows="12" placeholder='{"sourceType":"waf","eventType":"waf_block"}' />
              <div class="preview-result">
                <h3>归一化结果</h3>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="severity">{{ adapterPreviewResult?.severity || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="dedupKey">{{ adapterPreviewResult?.dedupKey || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="willCreateAlert">{{ adapterPreviewResult?.willCreateAlert ? '是' : '否' }}</el-descriptions-item>
                </el-descriptions>
                <pre>{{ formattedPreview }}</pre>
                <el-alert
                  v-if="adapterPreviewResult?.validationErrors?.length"
                  type="warning"
                  :closable="false"
                  :title="adapterPreviewResult.validationErrors.join('；')"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="mappingEditorVisible" title="编辑事件适配映射 JSON" width="860px">
      <el-alert
        title="请仅填写字段路径、固定转换和字段名数组；禁止脚本、表达式、SQL、命令、外部访问。保存后需要校验并发布。"
        type="warning"
        show-icon
        :closable="false"
        class="policy-alert"
      />
      <el-tabs v-model="mappingEditorTab">
        <el-tab-pane label="字段映射" name="fields">
          <el-input v-model="fieldMappingJson" type="textarea" :rows="14" />
        </el-tab-pane>
        <el-tab-pane label="严重级别" name="severity">
          <el-input v-model="severityMappingJson" type="textarea" :rows="14" />
        </el-tab-pane>
        <el-tab-pane label="告警联动" name="alert">
          <el-input v-model="alertRuleJson" type="textarea" :rows="14" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="mappingEditorVisible = false">取消</el-button>
        <el-button type="primary" :loading="mappingSaving" @click="saveMappingsJson">保存映射</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  changeLocalCheckPolicyEnabled,
  createCorrelationRule,
  createLocalCheckPolicy,
  createResponsePlaybook,
  createEventAdapter,
  createRiskScoringPolicy,
  disableCorrelationRule,
  disableLocalCheckPolicy,
  disableResponsePlaybook,
  disableEventAdapter,
  disableRiskScoringPolicy,
  eventAdapterMappings,
  listLocalCheckPolicies,
  listCorrelationRules,
  listResponsePlaybooks,
  listEventAdapters,
  listRiskScoringPolicies,
  localCheckPolicyAudits,
  previewEventAdapter,
  precheckLocalCheckPolicy,
  publishLocalCheckPolicy,
  publishCorrelationRule,
  publishResponsePlaybook,
  publishEventAdapter,
  publishRiskScoringPolicy,
  recalculateAllAssetRisks,
  responsePlaybookDetail,
  updateEventAdapter,
  updateEventAdapterMappings,
  updateCorrelationRule,
  updateLocalCheckPolicy,
  updateResponsePlaybook,
  updateRiskScoringPolicy,
  validateEventAdapter,
  validateCorrelationRule,
  validateResponsePlaybook,
  validateLocalCheckPolicy,
  validateRiskScoringPolicy,
  type EventAdapterMappingsPayload,
  type EventAdapterPreviewResult,
  type EventAdapterProfileItem,
  type EventAdapterProfilePayload,
  type CorrelationRuleItem,
  type CorrelationRulePayload,
  type LocalCheckPolicyItem,
  type LocalCheckPolicyPayload,
  type ResponsePlaybookItem,
  type ResponsePlaybookPayload,
  type ResponsePlaybookStep,
  type RiskScoringPolicyItem,
  type RiskScoringPolicyPayload,
} from '@/api/soc'

const router = useRouter()
const activeTab = ref('local-check')
const loading = ref(false)
const saving = ref(false)
const auditLoading = ref(false)
const rows = ref<LocalCheckPolicyItem[]>([])
const auditRows = ref<LocalCheckPolicyItem[]>([])
const total = ref(0)
const localPolicyFallback = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', osType: '', status: '' })
const dialogVisible = ref(false)
const editingId = ref<number>()
const adapterSources = ['waf', 'zap', 'trivy', 'wazuh', 'suricata', 'zeek']
const adapterLoading = ref(false)
const adapterSaving = ref(false)
const adapterRows = ref<EventAdapterProfileItem[]>([])
const adapterTotal = ref(0)
const adapterQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', status: '' })
const adapterDialogVisible = ref(false)
const editingAdapterId = ref<number>()
const adapterForm = reactive({
  sourceType: 'waf',
  displayName: '',
  description: '',
  status: 'draft',
  enabled: true,
  sortOrder: 10,
  sampleFile: '',
})
const adapterDrawerVisible = ref(false)
const selectedAdapter = ref<EventAdapterProfileItem>()
const adapterDetailTab = ref('fields')
const adapterMappings = reactive<EventAdapterMappingsPayload>({
  fieldMappings: [],
  severityMappings: [],
  alertLinkRules: [],
})
const adapterPreviewPayload = ref('{\n  "sourceType": "waf",\n  "eventType": "waf_block",\n  "severity": "high",\n  "assetIp": "10.20.1.15",\n  "targetUrl": "https://demo.internal.local/admin",\n  "action": "block",\n  "ruleId": "WAF-DEMO-1001",\n  "ruleName": "Demo WAF policy",\n  "requestId": "preview-0001"\n}')
const adapterPreviewResult = ref<EventAdapterPreviewResult>()
const riskLoading = ref(false)
const riskSaving = ref(false)
const riskRows = ref<RiskScoringPolicyItem[]>([])
const riskTotal = ref(0)
const riskQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })
const riskDialogVisible = ref(false)
const editingRiskId = ref<number>()
const playbookLoading = ref(false)
const playbookSaving = ref(false)
const playbookRows = ref<ResponsePlaybookItem[]>([])
const playbookTotal = ref(0)
const playbookQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', sourceType: '', status: '' })
const correlationLoading = ref(false)
const correlationSaving = ref(false)
const correlationRows = ref<CorrelationRuleItem[]>([])
const correlationTotal = ref(0)
const correlationQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', type: '', status: '' })
const correlationDialogVisible = ref(false)
const editingCorrelationId = ref<number>()
const playbookDialogVisible = ref(false)
const editingPlaybookId = ref<number>()
const playbookForm = reactive({
  playbookKey: '',
  playbookName: '',
  sourceType: 'waf',
  eventType: '',
  ruleIdPattern: '*',
  minSeverity: 'medium',
  description: '',
  status: 'draft',
  enabled: true,
  sortOrder: 10,
  safetyNote: '只生成处置建议和任务清单，不执行命令、不自动修复、不调用外部系统。',
})
const playbookStepsJson = ref('[]')
const correlationForm = reactive(defaultCorrelationForm())
const mappingEditorVisible = ref(false)
const mappingSaving = ref(false)
const mappingEditorTab = ref('fields')
const fieldMappingJson = ref('[]')
const severityMappingJson = ref('[]')
const alertRuleJson = ref('[]')
const form = reactive({
  commandKey: '',
  displayName: '',
  osType: 'Linux',
  category: 'identity',
  description: '',
  commandArgvJson: '["id"]',
  timeoutSeconds: 2,
  outputLimitKb: 8,
  enabled: true,
  status: 'draft',
  sortOrder: 10,
  safetyNote: '仅执行只读观察命令，不写文件、不扫描公网、不发送外部请求。',
})
const riskWeightFields = [
  { key: 'criticalAssetWeight', label: '关键资产' },
  { key: 'internetExposedWeight', label: '暴露面' },
  { key: 'criticalAlertWeight', label: '严重告警' },
  { key: 'highAlertWeight', label: '高危告警' },
  { key: 'mediumAlertWeight', label: '中危告警' },
  { key: 'criticalVulnerabilityWeight', label: '严重漏洞' },
  { key: 'highVulnerabilityWeight', label: '高危漏洞' },
  { key: 'baselineFailedWeight', label: '基线失败' },
  { key: 'fimUnreviewedWeight', label: '文件变更待复核' },
  { key: 'externalEventWeight', label: '外部证据' },
  { key: 'overdueTicketWeight', label: '超时工单' },
  { key: 'openPlaybookTaskWeight', label: '未完成剧本任务' },
  { key: 'employeePendingTaskWeight', label: '员工待办' },
  { key: 'closedTicketReduceWeight', label: '已关闭工单降分' },
  { key: 'completedPlaybookReduceWeight', label: '已完成剧本降分' },
  { key: 'maxScore', label: '评分上限' },
] as const
const riskForm = reactive(defaultRiskForm())

onMounted(() => {
  void loadPolicies()
})

watch(activeTab, (tab) => {
  if (tab === 'audit') void loadAudits()
  if (tab === 'adapter') void loadAdapters()
  if (tab === 'playbook') void loadPlaybooks()
  if (tab === 'correlation') void loadCorrelationRules()
  if (tab === 'risk-scoring') void loadRiskPolicies()
})

const formattedPreview = computed(() => {
  if (!adapterPreviewResult.value) return '-'
  return JSON.stringify(adapterPreviewResult.value.normalizedEvent, null, 2)
})

async function loadPolicies() {
  loading.value = true
  try {
    const res = await listLocalCheckPolicies(query)
    rows.value = res.data.data.records
    total.value = res.data.data.total
    localPolicyFallback.value = rows.value.some(isFallbackPolicy)
  } catch {
    const fallback = fallbackLocalPolicies(query.osType, query.status, query.keyword)
    rows.value = fallback.slice((query.pageNum - 1) * query.pageSize, query.pageNum * query.pageSize)
    total.value = fallback.length
    localPolicyFallback.value = true
  } finally {
    loading.value = false
  }
}

async function loadAudits() {
  auditLoading.value = true
  try {
    const res = await localCheckPolicyAudits()
    auditRows.value = res.data.data
  } catch {
    auditRows.value = []
  } finally {
    auditLoading.value = false
  }
}

async function loadAdapters() {
  adapterLoading.value = true
  try {
    const res = await listEventAdapters(adapterQuery)
    adapterRows.value = res.data.data.records
    adapterTotal.value = res.data.data.total
  } catch {
    ElMessage.error('事件适配映射加载失败')
  } finally {
    adapterLoading.value = false
  }
}

function resetAdapterFilters() {
  adapterQuery.keyword = ''
  adapterQuery.sourceType = ''
  adapterQuery.status = ''
  adapterQuery.pageNum = 1
  void loadAdapters()
}

function openAdapterCreate() {
  editingAdapterId.value = undefined
  Object.assign(adapterForm, {
    sourceType: 'waf',
    displayName: '',
    description: '',
    status: 'draft',
    enabled: true,
    sortOrder: 10,
    sampleFile: '',
  })
  adapterDialogVisible.value = true
}

function openAdapterEdit(row: EventAdapterProfileItem) {
  editingAdapterId.value = row.id
  Object.assign(adapterForm, {
    sourceType: row.sourceType,
    displayName: row.displayName,
    description: row.description || '',
    status: row.status || 'draft',
    enabled: Boolean(row.enabled),
    sortOrder: row.sortOrder || 10,
    sampleFile: row.sampleFile || '',
  })
  adapterDialogVisible.value = true
}

async function saveAdapterProfile() {
  adapterSaving.value = true
  try {
    const payload: EventAdapterProfilePayload = {
      sourceType: adapterForm.sourceType,
      displayName: adapterForm.displayName,
      description: adapterForm.description,
      status: adapterForm.status,
      enabled: adapterForm.enabled,
      sortOrder: adapterForm.sortOrder,
      sampleFile: adapterForm.sampleFile,
    }
    if (editingAdapterId.value) {
      await updateEventAdapter(editingAdapterId.value, payload)
    } else {
      await createEventAdapter(payload)
    }
    ElMessage.success('事件适配器已保存')
    adapterDialogVisible.value = false
    await loadAdapters()
  } finally {
    adapterSaving.value = false
  }
}

async function openAdapterDetail(row: EventAdapterProfileItem) {
  selectedAdapter.value = row
  adapterDrawerVisible.value = true
  adapterDetailTab.value = 'fields'
  adapterPreviewResult.value = undefined
  await loadAdapterMappings(row.id)
}

async function loadAdapterMappings(id?: number) {
  if (!id) return
  const res = await eventAdapterMappings(id)
  adapterMappings.fieldMappings = res.data.data.fieldMappings || []
  adapterMappings.severityMappings = res.data.data.severityMappings || []
  adapterMappings.alertLinkRules = res.data.data.alertLinkRules || []
}

async function validateAdapterRow(row: EventAdapterProfileItem) {
  if (!row.id) return
  const res = await validateEventAdapter(row.id)
  if (res.data.data.passed) {
    ElMessage.success(res.data.data.message)
  } else {
    ElMessage.warning(`${res.data.data.message}：${res.data.data.errors.join('；')}`)
  }
}

async function publishAdapterRow(row: EventAdapterProfileItem) {
  if (!row.id) return
  await ElMessageBox.confirm('发布后多源导入会优先使用该 active 适配映射。确认发布？', '发布事件适配器')
  await publishEventAdapter(row.id)
  ElMessage.success('事件适配器已发布')
  await loadAdapters()
}

async function disableAdapterRow(row: EventAdapterProfileItem) {
  if (!row.id) return
  await ElMessageBox.confirm('停用后导入会回退到内置安全适配器。确认停用？', '停用事件适配器')
  await disableEventAdapter(row.id)
  ElMessage.success('事件适配器已停用')
  await loadAdapters()
}

function openMappingsEditor() {
  fieldMappingJson.value = JSON.stringify(adapterMappings.fieldMappings, null, 2)
  severityMappingJson.value = JSON.stringify(adapterMappings.severityMappings, null, 2)
  alertRuleJson.value = JSON.stringify(adapterMappings.alertLinkRules, null, 2)
  mappingEditorVisible.value = true
}

async function saveMappingsJson() {
  if (!selectedAdapter.value?.id) return
  mappingSaving.value = true
  try {
    const payload: EventAdapterMappingsPayload = {
      fieldMappings: JSON.parse(fieldMappingJson.value),
      severityMappings: JSON.parse(severityMappingJson.value),
      alertLinkRules: JSON.parse(alertRuleJson.value),
    }
    const res = await updateEventAdapterMappings(selectedAdapter.value.id, payload)
    adapterMappings.fieldMappings = res.data.data.fieldMappings || []
    adapterMappings.severityMappings = res.data.data.severityMappings || []
    adapterMappings.alertLinkRules = res.data.data.alertLinkRules || []
    mappingEditorVisible.value = false
    ElMessage.success('映射已保存，请校验后发布')
  } catch (error) {
    ElMessage.error(error instanceof SyntaxError ? 'JSON 格式不正确' : '映射保存失败')
  } finally {
    mappingSaving.value = false
  }
}

async function previewAdapter() {
  if (!selectedAdapter.value?.id) return
  try {
    const res = await previewEventAdapter(selectedAdapter.value.id, adapterPreviewPayload.value)
    adapterPreviewResult.value = res.data.data
    adapterDetailTab.value = 'preview'
    if (res.data.data.validationErrors.length) {
      ElMessage.warning('预览完成，但存在校验提示')
    } else {
      ElMessage.success('预览完成，未写入数据库')
    }
  } catch {
    ElMessage.error('预览失败，请检查样例 JSON')
  }
}

async function loadPlaybooks() {
  playbookLoading.value = true
  try {
    const res = await listResponsePlaybooks(playbookQuery)
    playbookRows.value = res.data.data.records
    playbookTotal.value = res.data.data.total
  } catch {
    ElMessage.error('处置剧本加载失败')
  } finally {
    playbookLoading.value = false
  }
}

async function loadCorrelationRules() {
  correlationLoading.value = true
  try {
    const res = await listCorrelationRules(correlationQuery)
    correlationRows.value = res.data.data.records
    correlationTotal.value = res.data.data.total
  } catch {
    correlationRows.value = []
    correlationTotal.value = 0
    ElMessage.error('事件关联规则加载失败')
  } finally {
    correlationLoading.value = false
  }
}

async function loadRiskPolicies() {
  riskLoading.value = true
  try {
    const res = await listRiskScoringPolicies(riskQuery)
    riskRows.value = res.data.data.records
    riskTotal.value = res.data.data.total
  } catch {
    riskRows.value = []
    riskTotal.value = 0
    ElMessage.error('风险评分策略加载失败')
  } finally {
    riskLoading.value = false
  }
}

function resetRiskFilters() {
  riskQuery.keyword = ''
  riskQuery.status = ''
  riskQuery.pageNum = 1
  void loadRiskPolicies()
}

function defaultRiskForm() {
  return {
    policyCode: 'DEFAULT_ASSET_RISK_V1',
    policyName: '默认资产风险评分策略',
    description: '基于告警、漏洞、基线、证据、工单和剧本任务的数字权重策略。',
    status: 'draft',
    enabled: true,
    criticalAssetWeight: 10,
    internetExposedWeight: 10,
    criticalAlertWeight: 25,
    highAlertWeight: 15,
    mediumAlertWeight: 8,
    criticalVulnerabilityWeight: 25,
    highVulnerabilityWeight: 15,
    baselineFailedWeight: 8,
    fimUnreviewedWeight: 6,
    externalEventWeight: 6,
    overdueTicketWeight: 10,
    openPlaybookTaskWeight: 6,
    employeePendingTaskWeight: 8,
    closedTicketReduceWeight: 8,
    completedPlaybookReduceWeight: 5,
    maxScore: 100,
  }
}

function openRiskCreate() {
  editingRiskId.value = undefined
  Object.assign(riskForm, defaultRiskForm())
  riskDialogVisible.value = true
}

function openRiskEdit(row: RiskScoringPolicyItem) {
  editingRiskId.value = row.id
  Object.assign(riskForm, {
    ...defaultRiskForm(),
    ...row,
    enabled: Boolean(row.enabled),
  })
  riskDialogVisible.value = true
}

function riskPayload(): RiskScoringPolicyPayload {
  return {
    policyCode: riskForm.policyCode,
    policyName: riskForm.policyName,
    description: riskForm.description,
    status: riskForm.status as 'draft' | 'active' | 'disabled',
    enabled: riskForm.enabled,
    criticalAssetWeight: riskForm.criticalAssetWeight,
    internetExposedWeight: riskForm.internetExposedWeight,
    criticalAlertWeight: riskForm.criticalAlertWeight,
    highAlertWeight: riskForm.highAlertWeight,
    mediumAlertWeight: riskForm.mediumAlertWeight,
    criticalVulnerabilityWeight: riskForm.criticalVulnerabilityWeight,
    highVulnerabilityWeight: riskForm.highVulnerabilityWeight,
    baselineFailedWeight: riskForm.baselineFailedWeight,
    fimUnreviewedWeight: riskForm.fimUnreviewedWeight,
    externalEventWeight: riskForm.externalEventWeight,
    overdueTicketWeight: riskForm.overdueTicketWeight,
    openPlaybookTaskWeight: riskForm.openPlaybookTaskWeight,
    employeePendingTaskWeight: riskForm.employeePendingTaskWeight,
    closedTicketReduceWeight: riskForm.closedTicketReduceWeight,
    completedPlaybookReduceWeight: riskForm.completedPlaybookReduceWeight,
    maxScore: riskForm.maxScore,
  }
}

async function saveRiskPolicy() {
  riskSaving.value = true
  try {
    if (editingRiskId.value) {
      await updateRiskScoringPolicy(editingRiskId.value, riskPayload())
    } else {
      await createRiskScoringPolicy(riskPayload())
    }
    ElMessage.success('风险评分策略已保存')
    riskDialogVisible.value = false
    await loadRiskPolicies()
  } finally {
    riskSaving.value = false
  }
}

async function validateRisk(row: RiskScoringPolicyItem) {
  if (!row.id) return
  await validateRiskById(row.id)
}

async function validateRiskById(id: number) {
  const res = await validateRiskScoringPolicy(id)
  ElMessage.success(res.data.data.message)
}

async function publishRisk(row: RiskScoringPolicyItem) {
  if (!row.id) return
  await ElMessageBox.confirm('发布后资产风险画像会使用该 active 策略。确认发布？', '发布风险评分策略')
  await publishRiskScoringPolicy(row.id)
  ElMessage.success('风险评分策略已发布')
  await loadRiskPolicies()
}

async function disableRisk(row: RiskScoringPolicyItem) {
  if (!row.id) return
  await ElMessageBox.confirm('停用后系统会选择其他 active 策略或回退内置默认策略。确认停用？', '停用风险评分策略')
  await disableRiskScoringPolicy(row.id)
  ElMessage.success('风险评分策略已停用')
  await loadRiskPolicies()
}

async function recalculateAllRisks() {
  await ElMessageBox.confirm('只根据已有 SOC 数据重新计算资产风险，不执行扫描或修复。确认继续？', '重新计算资产风险')
  const res = await recalculateAllAssetRisks()
  ElMessage.success(res.data.data.message)
}

function resetPlaybookFilters() {
  playbookQuery.keyword = ''
  playbookQuery.sourceType = ''
  playbookQuery.status = ''
  playbookQuery.pageNum = 1
  void loadPlaybooks()
}

function defaultPlaybookSteps() {
  return [
    {
      stepKey: 'triage',
      stepName: '复核告警证据',
      stepType: 'triage',
      ownerRole: 'analyst',
      instruction: '核对告警详情、影响资产和证据摘要，确认是否需要进入处置闭环。',
      expectedEvidence: '告警详情和人工判断说明。',
      requiresEmployee: false,
      sortOrder: 10,
      enabled: true,
    },
    {
      stepKey: 'record',
      stepName: '记录处置结论',
      stepType: 'report',
      ownerRole: 'analyst',
      instruction: '在工单中记录处置建议、负责人和报告结论。',
      expectedEvidence: '工单时间线和报告编号。',
      requiresEmployee: false,
      sortOrder: 20,
      enabled: true,
    },
  ] as ResponsePlaybookStep[]
}

function openPlaybookCreate() {
  editingPlaybookId.value = undefined
  Object.assign(playbookForm, {
    playbookKey: '',
    playbookName: '',
    sourceType: 'waf',
    eventType: '',
    ruleIdPattern: '*',
    minSeverity: 'medium',
    description: '',
    status: 'draft',
    enabled: true,
    sortOrder: 10,
    safetyNote: '只生成处置建议和任务清单，不执行命令、不自动修复、不调用外部系统。',
  })
  playbookStepsJson.value = JSON.stringify(defaultPlaybookSteps(), null, 2)
  playbookDialogVisible.value = true
}

async function openPlaybookEdit(row: ResponsePlaybookItem) {
  if (!row.id) return
  const res = await responsePlaybookDetail(row.id)
  const detail = res.data.data
  editingPlaybookId.value = row.id
  Object.assign(playbookForm, {
    playbookKey: detail.playbook.playbookKey,
    playbookName: detail.playbook.playbookName,
    sourceType: detail.playbook.sourceType,
    eventType: detail.playbook.eventType || '',
    ruleIdPattern: detail.playbook.ruleIdPattern || '*',
    minSeverity: detail.playbook.minSeverity || 'medium',
    description: detail.playbook.description || '',
    status: detail.playbook.status || 'draft',
    enabled: Boolean(detail.playbook.enabled),
    sortOrder: detail.playbook.sortOrder || 10,
    safetyNote: detail.playbook.safetyNote || '',
  })
  playbookStepsJson.value = JSON.stringify(detail.steps || [], null, 2)
  playbookDialogVisible.value = true
}

async function openPlaybookDetail(row: ResponsePlaybookItem) {
  await openPlaybookEdit(row)
}

async function savePlaybook() {
  playbookSaving.value = true
  try {
    const payload = playbookPayload()
    if (editingPlaybookId.value) {
      await updateResponsePlaybook(editingPlaybookId.value, payload)
    } else {
      await createResponsePlaybook(payload)
    }
    ElMessage.success('处置剧本已保存')
    playbookDialogVisible.value = false
    await loadPlaybooks()
  } catch (error) {
    ElMessage.error(error instanceof SyntaxError ? '步骤 JSON 格式不正确' : '处置剧本保存失败')
  } finally {
    playbookSaving.value = false
  }
}

function playbookPayload(): ResponsePlaybookPayload {
  return {
    playbookKey: playbookForm.playbookKey,
    playbookName: playbookForm.playbookName,
    sourceType: playbookForm.sourceType,
    eventType: playbookForm.eventType,
    ruleIdPattern: playbookForm.ruleIdPattern,
    minSeverity: playbookForm.minSeverity,
    description: playbookForm.description,
    status: playbookForm.status,
    enabled: playbookForm.enabled,
    sortOrder: playbookForm.sortOrder,
    safetyNote: playbookForm.safetyNote,
    steps: JSON.parse(playbookStepsJson.value) as ResponsePlaybookStep[],
  }
}

async function validatePlaybookRow(row: ResponsePlaybookItem) {
  if (!row.id) return
  const res = await validateResponsePlaybook(row.id)
  ElMessage.success(res.data.data.message)
}

async function publishPlaybookRow(row: ResponsePlaybookItem) {
  if (!row.id) return
  await ElMessageBox.confirm('发布后告警详情会推荐该处置剧本。确认发布？', '发布处置剧本')
  await publishResponsePlaybook(row.id)
  ElMessage.success('处置剧本已发布')
  await loadPlaybooks()
}

async function disablePlaybookRow(row: ResponsePlaybookItem) {
  if (!row.id) return
  await ElMessageBox.confirm('停用后告警详情不再推荐该处置剧本。确认停用？', '停用处置剧本')
  await disableResponsePlaybook(row.id)
  ElMessage.success('处置剧本已停用')
  await loadPlaybooks()
}

async function validateCorrelationRow(row: CorrelationRuleItem) {
  if (!row.id) return
  await validateCorrelationById(row.id)
}

async function validateCorrelationById(id: number) {
  const res = await validateCorrelationRule(id)
  ElMessage.success(res.data.data.message)
}

function resetCorrelationFilters() {
  correlationQuery.keyword = ''
  correlationQuery.type = ''
  correlationQuery.status = ''
  correlationQuery.pageNum = 1
  void loadCorrelationRules()
}

function defaultCorrelationForm() {
  return {
    ruleKey: '',
    ruleName: '',
    ruleType: 'cross_source_chain',
    sourceTypesJson: '["waf","zap","wazuh","suricata","zeek","trivy"]',
    eventTypesJson: '',
    groupByJson: '["assetIp","batchId","demoCaseId"]',
    threshold: 2,
    timeframeSeconds: 1800,
    sequenceJson: '["waf","zap","wazuh"]',
    severityFloor: 'medium',
    enabled: true,
    status: 'draft',
    version: 1,
    description: '',
    safetyNote: '只读取已导入的 SOC 证据，不执行脚本、扫描、外部查询或自动修复。',
  }
}

function openCorrelationCreate() {
  editingCorrelationId.value = undefined
  Object.assign(correlationForm, defaultCorrelationForm())
  correlationDialogVisible.value = true
}

function openCorrelationEdit(row: CorrelationRuleItem) {
  editingCorrelationId.value = row.id
  Object.assign(correlationForm, {
    ...defaultCorrelationForm(),
    ...row,
    enabled: Boolean(row.enabled),
    sourceTypesJson: row.sourceTypesJson || '',
    eventTypesJson: row.eventTypesJson || '',
    groupByJson: row.groupByJson || '',
    sequenceJson: row.sequenceJson || '',
    severityFloor: row.severityFloor || '',
    description: row.description || '',
    safetyNote: row.safetyNote || '',
  })
  correlationDialogVisible.value = true
}

function correlationPayload(): CorrelationRulePayload {
  return {
    ruleKey: correlationForm.ruleKey,
    ruleName: correlationForm.ruleName,
    ruleType: correlationForm.ruleType,
    sourceTypesJson: correlationForm.sourceTypesJson,
    eventTypesJson: correlationForm.eventTypesJson,
    groupByJson: correlationForm.groupByJson,
    threshold: correlationForm.threshold,
    timeframeSeconds: correlationForm.timeframeSeconds,
    sequenceJson: correlationForm.sequenceJson,
    severityFloor: correlationForm.severityFloor,
    enabled: correlationForm.enabled,
    status: correlationForm.status,
    version: correlationForm.version,
    description: correlationForm.description,
    safetyNote: correlationForm.safetyNote,
  }
}

async function saveCorrelationRule() {
  correlationSaving.value = true
  try {
    const payload = correlationPayload()
    if (editingCorrelationId.value) {
      await updateCorrelationRule(editingCorrelationId.value, payload)
    } else {
      await createCorrelationRule(payload)
    }
    ElMessage.success('事件关联规则已保存')
    correlationDialogVisible.value = false
    await loadCorrelationRules()
  } catch (error) {
    ElMessage.error(error instanceof SyntaxError ? 'JSON 格式不正确' : '事件关联规则保存失败')
  } finally {
    correlationSaving.value = false
  }
}

async function publishCorrelationRow(row: CorrelationRuleItem) {
  if (!row.id) return
  await ElMessageBox.confirm('发布后关联引擎会在下一次手动执行关联时使用该规则。确认发布？', '发布事件关联规则')
  await publishCorrelationRule(row.id)
  ElMessage.success('事件关联规则已发布')
  await loadCorrelationRules()
}

async function disableCorrelationRow(row: CorrelationRuleItem) {
  if (!row.id) return
  await ElMessageBox.confirm('停用后关联引擎不会再使用该规则。确认停用？', '停用事件关联规则')
  await disableCorrelationRule(row.id)
  ElMessage.success('事件关联规则已停用')
  await loadCorrelationRules()
}

function resetFilters() {
  query.keyword = ''
  query.osType = ''
  query.status = ''
  query.pageNum = 1
  void loadPolicies()
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    commandKey: '',
    displayName: '',
    osType: 'Linux',
    category: 'identity',
    description: '',
    commandArgvJson: '["id"]',
    timeoutSeconds: 2,
    outputLimitKb: 8,
    enabled: true,
    status: 'draft',
    sortOrder: 10,
    safetyNote: '仅执行只读观察命令，不写文件、不扫描公网、不发送外部请求。',
  })
  dialogVisible.value = true
}

function openEdit(row: LocalCheckPolicyItem) {
  if (isFallbackPolicy(row)) return
  editingId.value = row.id
  Object.assign(form, {
    commandKey: row.commandKey,
    displayName: row.displayName,
    osType: row.osType,
    category: row.category,
    description: row.description || '',
    commandArgvJson: row.commandArgvJson,
    timeoutSeconds: row.timeoutSeconds || 2,
    outputLimitKb: row.outputLimitKb || 8,
    enabled: Boolean(row.enabled),
    status: row.status || 'draft',
    sortOrder: row.sortOrder || 10,
    safetyNote: row.safetyNote || '',
  })
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = formPayload()
    if (editingId.value) {
      await updateLocalCheckPolicy(editingId.value, payload)
    } else {
      await createLocalCheckPolicy(payload)
    }
    ElMessage.success('策略已保存')
    dialogVisible.value = false
    await loadPolicies()
  } finally {
    saving.value = false
  }
}

async function precheck(row: LocalCheckPolicyItem) {
  if (isFallbackPolicy(row)) return
  if (!row.id) return
  const res = await validateLocalCheckPolicy(row.id)
  ElMessage.success(res.data.data.message)
}

async function precheckForm() {
  const res = await precheckLocalCheckPolicy(formPayload())
  ElMessage.success(res.data.data.message)
}

async function publish(row: LocalCheckPolicyItem) {
  if (isFallbackPolicy(row)) return
  if (!row.id) return
  await ElMessageBox.confirm('发布后员工端可以选择该 active 策略。确认发布？', '发布本机检查策略')
  await publishLocalCheckPolicy(row.id)
  ElMessage.success('策略已发布')
  await loadPolicies()
}

async function toggleEnabled(row: LocalCheckPolicyItem) {
  if (isFallbackPolicy(row)) return
  if (!row.id) return
  if (row.enabled) {
    await disableLocalCheckPolicy(row.id)
  } else {
    await changeLocalCheckPolicyEnabled(row.id, true)
  }
  ElMessage.success(row.enabled ? '策略已停用' : '策略已启用')
  await loadPolicies()
}

function isFallbackPolicy(row: LocalCheckPolicyItem) {
  return Boolean(row.id && row.id < 0)
}

function fallbackLocalPolicies(osType?: string, status?: string, keyword?: string): LocalCheckPolicyItem[] {
  if (status && status !== 'active') return []
  const osList = osType ? [osType] : ['Linux', 'macOS', 'Windows']
  const lowerKeyword = (keyword || '').trim().toLowerCase()
  return osList.flatMap((os) => fallbackCatalog(normalizePolicyOs(os)))
    .filter((item) => !lowerKeyword
      || item.commandKey.toLowerCase().includes(lowerKeyword)
      || item.displayName.toLowerCase().includes(lowerKeyword)
      || (item.description || '').toLowerCase().includes(lowerKeyword))
}

function fallbackCatalog(osType: string): LocalCheckPolicyItem[] {
  const common = (key: string, name: string, category: string, description: string, argv: string[], sortOrder: number): LocalCheckPolicyItem => ({
    id: -Math.abs(`${osType}:${key}`.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)),
    commandKey: key,
    displayName: name,
    osType,
    category,
    description,
    commandArgvJson: JSON.stringify(argv),
    timeoutSeconds: 2,
    outputLimitKb: 8,
    enabled: 1,
    status: 'active',
    version: 1,
    sortOrder,
    safetyNote: '使用内置默认策略：数据库策略表不可用或尚未初始化。',
  })
  if (osType === 'Windows') {
    return [
      common('identity', '检查当前登录身份', 'identity', '确认当前登录用户。', ['whoami'], 10),
      common('network', '检查网络连接', 'network', '查看当前电脑网络连接状态。', ['netstat', '-ano'], 20),
      common('process', '检查正在运行的程序', 'process', '查看正在运行的程序列表。', ['tasklist'], 30),
      common('startup', '检查开机启动项', 'startup', '查看只读启动项注册表。', ['reg', 'query', 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'], 40),
      common('hostname', '核对电脑名称', 'host', '核对电脑名称是否和安全团队记录一致。', ['hostname'], 50),
    ]
  }
  if (osType === 'macOS') {
    return [
      common('identity', '检查当前登录身份', 'identity', '确认当前登录用户和权限组。', ['id'], 10),
      common('network', '检查网络连接', 'network', '查看当前电脑网络连接状态。', ['netstat', '-an'], 20),
      common('process', '检查正在运行的程序', 'process', '查看正在运行的程序列表。', ['ps', '-axo', 'pid,comm'], 30),
      common('startup', '检查开机启动项', 'startup', '查看用户 LaunchAgents 目录。', ['ls', '-la', '~/Library/LaunchAgents'], 40),
      common('hostname', '核对电脑名称', 'host', '核对电脑名称是否和安全团队记录一致。', ['hostname'], 50),
    ]
  }
  return [
    common('identity', '检查当前登录身份', 'identity', '确认当前登录用户和权限组。', ['id'], 10),
    common('network', '检查网络连接', 'network', '查看当前电脑网络连接状态。', ['ss', '-tunap'], 20),
    common('process', '检查正在运行的程序', 'process', '查看正在运行的程序列表。', ['ps', '-axo', 'pid,comm'], 30),
    common('startup', '检查开机启动项', 'startup', '查看开机启动或用户服务项。', ['systemctl', '--user', 'list-units', '--type=service', '--no-pager', '--no-legend'], 40),
    common('hostname', '核对电脑名称', 'host', '核对电脑名称是否和安全团队记录一致。', ['hostname'], 50),
  ]
}

function normalizePolicyOs(value?: string) {
  const text = (value || '').toLowerCase()
  if (text.includes('win')) return 'Windows'
  if (text.includes('mac')) return 'macOS'
  return 'Linux'
}

function formPayload(): LocalCheckPolicyPayload {
  return {
    commandKey: form.commandKey,
    displayName: form.displayName,
    osType: form.osType,
    category: form.category,
    description: form.description,
    commandArgvJson: form.commandArgvJson,
    timeoutSeconds: form.timeoutSeconds,
    outputLimitKb: form.outputLimitKb,
    enabled: form.enabled,
    status: form.status,
    sortOrder: form.sortOrder,
    safetyNote: form.safetyNote,
  }
}

function statusLabel(status: string) {
  if (status === 'active') return '已发布'
  if (status === 'disabled') return '已停用'
  return '草稿'
}

function statusTag(status: string) {
  if (status === 'active') return 'success'
  if (status === 'disabled') return 'info'
  return 'warning'
}
</script>

<style scoped>
.policy-center-page {
  min-width: 0;
}

.policy-alert,
.policy-tabs {
  margin-top: 14px;
}

.panel-pad {
  padding: 14px;
}

.policy-form {
  padding-right: 12px;
}

.inline-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.risk-weight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  gap: 0 12px;
}

.placeholder-panel {
  padding: 28px;
  border: 1px solid rgba(179, 173, 163, 0.46);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.76);
}

.placeholder-panel h2 {
  margin: 0 0 10px;
  color: var(--soc-text);
  font-size: 20px;
}

.placeholder-panel p {
  max-width: 760px;
  color: var(--soc-text-muted);
  line-height: 1.7;
}

.adapter-summary {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid rgba(179, 173, 163, 0.46);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(250, 247, 242, 0.78));
}

.adapter-summary h2 {
  margin: 4px 0 8px;
  color: var(--soc-text);
  font-size: 20px;
}

.adapter-summary p {
  margin: 0;
  color: var(--soc-text-muted);
}

.adapter-actions {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.preview-grid {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(300px, 1fr);
  gap: 16px;
}

.preview-result {
  min-width: 0;
}

.preview-result h3 {
  margin: 0 0 10px;
  color: var(--soc-text);
  font-size: 16px;
}

.preview-result pre {
  max-height: 300px;
  overflow: auto;
  margin: 12px 0;
  padding: 12px;
  border: 1px solid rgba(179, 173, 163, 0.42);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--soc-text);
  font-size: 12px;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .adapter-summary,
  .preview-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .adapter-actions {
    justify-content: flex-start;
  }

  .risk-weight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
