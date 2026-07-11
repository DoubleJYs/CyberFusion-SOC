<template>
  <div class="fim-watch-panel">
    <el-alert
      title="只监控已授权的主机目录"
      description="Agent 仅采集目录内的路径、大小、时间、权限和元数据哈希变化，不上传文件内容；根目录、通配符和路径穿越会被拒绝。发布后，匹配主机的 Agent 会在下一次采集周期读取授权。"
      type="warning"
      show-icon
      :closable="false"
    />

    <section class="soc-panel panel-pad">
      <div class="soc-filter-bar">
        <el-input v-model="query.keyword" clearable placeholder="搜索目录、主机或用途" @keyup.enter="load" />
        <el-select v-model="query.osType" clearable placeholder="系统"><el-option label="macOS" value="macos" /><el-option label="Windows" value="windows" /><el-option label="Linux" value="linux" /></el-select>
        <el-select v-model="query.status" clearable placeholder="状态"><el-option label="草稿" value="draft" /><el-option label="已发布" value="active" /><el-option label="已停用" value="disabled" /></el-select>
        <el-button @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate">新增授权目录</el-button>
      </div>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无文件监控授权目录">
        <el-table-column prop="displayName" label="授权名称" min-width="150" />
        <el-table-column prop="hostName" label="目标主机" min-width="170" />
        <el-table-column label="系统" width="94"><template #default="{ row }">{{ osLabel(row.osType) }}</template></el-table-column>
        <el-table-column prop="watchPath" label="监控目录" min-width="270" show-overflow-tooltip />
        <el-table-column label="用途" width="120"><template #default="{ row }">{{ purposeLabel(row.purpose) }}</template></el-table-column>
        <el-table-column label="范围" width="130"><template #default="{ row }">{{ row.recursive ? `递归 ${row.maxEntries} 项` : `仅当前层 ${row.maxEntries} 项` }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="172" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button text @click="openEdit(row)">编辑</el-button>
            <el-button text :disabled="row.status === 'active'" @click="publish(row)">发布</el-button>
            <el-button text type="warning" :disabled="row.status === 'disabled'" @click="disable(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row"><span>共 {{ total }} 个授权目录</span><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" layout="total, sizes, prev, pager, next" :total="total" @change="load" /></div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑文件监控授权' : '新增文件监控授权'" width="640px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="授权名称"><el-input v-model="form.displayName" placeholder="例如：系统日志目录" /></el-form-item>
        <el-form-item label="目标主机">
          <el-select v-model="form.hostName" filterable allow-create default-first-option placeholder="选择已登记 Agent 的主机" @change="applyHost">
            <el-option v-for="agent in agents" :key="agent.id" :label="`${agent.hostname} (${osLabel(agent.osType)})`" :value="agent.hostname" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机系统"><el-segmented v-model="form.osType" :options="osOptions" /></el-form-item>
        <el-form-item label="监控目录"><el-input v-model="form.watchPath" placeholder="macOS: /Users/name/Library/Logs；Windows: C:/ProgramData/App/logs" /></el-form-item>
        <el-form-item label="用途"><el-select v-model="form.purpose"><el-option v-for="option in purposeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="扫描范围"><el-switch v-model="form.recursive" active-text="递归目录" inactive-text="仅当前层" /><el-input-number v-model="form.maxEntries" :min="1" :max="2000" class="entry-limit" /><span class="form-hint">最多目录项</span></el-form-item>
        <el-form-item label="状态"><el-segmented v-model="form.status" :options="['draft', 'active', 'disabled']" /></el-form-item>
        <el-form-item label="平台接收"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFimWatchPath, disableFimWatchPath, listFimWatchPaths, listHostAgents, publishFimWatchPath, updateFimWatchPath, type FimWatchPathItem, type FimWatchPathPayload, type HostAgentItem } from '@/api/soc'

const loading = ref(false)
const saving = ref(false)
const rows = ref<FimWatchPathItem[]>([])
const agents = ref<HostAgentItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number>()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', osType: '', status: '' })
const osOptions = [{ label: 'macOS', value: 'macos' }, { label: 'Windows', value: 'windows' }, { label: 'Linux', value: 'linux' }]
const purposeOptions = [
  { label: '主机日志', value: 'host_log' }, { label: '应用日志', value: 'application_log' }, { label: '巡回审计', value: 'audit' },
  { label: '文件完整性', value: 'file_integrity' }, { label: '自定义目录', value: 'custom' },
] as const
const form = reactive({ displayName: '', hostName: '', osType: 'macos' as FimWatchPathPayload['osType'], watchPath: '', purpose: 'host_log' as FimWatchPathPayload['purpose'], recursive: true, maxEntries: 500, status: 'draft' as NonNullable<FimWatchPathPayload['status']>, enabled: true })

onMounted(() => { void load(); void loadAgents() })
async function load() { loading.value = true; try { const res = await listFimWatchPaths(query); rows.value = res.data.data.records; total.value = res.data.data.total } catch { ElMessage.error('文件监控授权目录加载失败') } finally { loading.value = false } }
async function loadAgents() { try { agents.value = (await listHostAgents()).data.data } catch { agents.value = [] } }
function openCreate() { editingId.value = undefined; Object.assign(form, { displayName: '', hostName: '', osType: 'macos', watchPath: '', purpose: 'host_log', recursive: true, maxEntries: 500, status: 'draft', enabled: true }); dialogVisible.value = true }
function openEdit(row: FimWatchPathItem) { editingId.value = row.id; Object.assign(form, { ...row, recursive: Boolean(row.recursive), enabled: Boolean(row.enabled) }); dialogVisible.value = true }
function applyHost(hostname: string) { const agent = agents.value.find(item => item.hostname === hostname); if (agent?.osType) form.osType = agent.osType as FimWatchPathPayload['osType'] }
function payload(): FimWatchPathPayload { return { displayName: form.displayName, hostName: form.hostName, osType: form.osType, watchPath: form.watchPath, purpose: form.purpose, recursive: form.recursive, maxEntries: form.maxEntries, status: form.status, enabled: form.enabled } }
async function save() { saving.value = true; try { if (editingId.value) await updateFimWatchPath(editingId.value, payload()); else await createFimWatchPath(payload()); ElMessage.success('文件监控授权已保存'); dialogVisible.value = false; await load() } finally { saving.value = false } }
async function publish(row: FimWatchPathItem) { await publishFimWatchPath(row.id); ElMessage.success('目录授权已发布，Agent 会在下一轮采集时同步'); await load() }
async function disable(row: FimWatchPathItem) { await disableFimWatchPath(row.id); ElMessage.success('目录授权已停用'); await load() }
function osLabel(value: string) { return ({ macos: 'macOS', windows: 'Windows', linux: 'Linux' } as Record<string, string>)[value] || value }
function purposeLabel(value: string) { return (purposeOptions.find(item => item.value === value)?.label) || value }
function statusLabel(value: string) { return ({ draft: '草稿', active: '已发布', disabled: '已停用' } as Record<string, string>)[value] || value }
function statusType(value: string) { return ({ draft: 'info', active: 'success', disabled: 'warning' } as Record<string, 'success' | 'warning' | 'info'>)[value] || 'info' }
</script>

<style scoped>
.fim-watch-panel { display: grid; gap: 14px; }
.panel-pad { padding: 14px; }
.entry-limit { margin-left: 12px; width: 120px; }
.form-hint { margin-left: 8px; color: var(--soc-text-muted); font-size: 13px; }
@media (max-width: 860px) { .entry-limit { margin-left: 0; } }
</style>
