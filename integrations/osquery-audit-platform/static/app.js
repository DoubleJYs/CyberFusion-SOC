const state = {
  templates: [],
  hosts: [],
  selectedHostId: null,
};

const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const data = await response.json();
  if (!response.ok || data.error) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

function riskClass(score) {
  if (score >= 70) return "high";
  if (score >= 40) return "medium";
  return "low";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function loadTemplates() {
  const data = await api("/api/templates");
  state.templates = data.templates;
  $("templateCount").textContent = String(data.templates.length);
  $("templateSelect").innerHTML = data.templates
    .map((template) => `<option value="${escapeHtml(template.id)}">${escapeHtml(template.name)}</option>`)
    .join("");
  renderTemplateDetail();
}

function renderTemplateDetail() {
  const selected = state.templates.find((template) => template.id === $("templateSelect").value) || state.templates[0];
  if (!selected) return;
  $("templateDetail").innerHTML = `
    <strong>${escapeHtml(selected.category)}</strong><br />
    ${escapeHtml(selected.description)}<br />
    <span>章节：${selected.sections.map(escapeHtml).join(" / ")}</span><br />
    <span>${escapeHtml(selected.safe_scope)}</span>
  `;
}

async function refreshAll() {
  const [health, summary, assets, failures, departments, changes, runs, tasks, schedules, auditLogs] = await Promise.all([
    api("/api/health"),
    api("/api/summary"),
    api("/api/assets"),
    api("/api/failures"),
    api("/api/departments"),
    api("/api/changes"),
    api("/api/runs"),
    api("/api/tasks"),
    api("/api/schedules"),
    api("/api/audit-logs"),
  ]);
  $("runtimePath").textContent = health.db_path;
  $("hostCount").textContent = String(summary.host_count || 0);
  $("failedCount").textContent = String(summary.failed_count || 0);
  $("riskScore").textContent = String(summary.avg_risk_score || 0);
  $("highRiskCount").textContent = String(summary.high_risk_count || 0);
  $("failureCount").textContent = String(failures.findings.length);
  $("lastRun").textContent = summary.run ? `${summary.run.mode} / ${summary.run.started_at}` : "暂无快照";
  $("runStatus").textContent = summary.run ? "已完成" : "未执行";
  state.hosts = assets.hosts;
  renderAssets();
  renderFailures(failures.findings);
  renderDepartments(departments.departments);
  renderChanges(changes);
  renderTasks(tasks.tasks);
  renderSchedules(schedules.schedules);
  renderAuditLogs(auditLogs.logs);
  const latestWithReport = runs.runs.find((run) => run.report_path);
  $("reportPath").textContent = latestWithReport ? latestWithReport.report_path : "报告尚未生成";
}

function renderAssets() {
  const keyword = $("assetFilter").value.trim().toLowerCase();
  const rows = state.hosts.filter((host) => {
    const text = `${host.hostname} ${host.ip} ${host.department} ${host.os_name}`.toLowerCase();
    return !keyword || text.includes(keyword);
  });
  $("assetRows").innerHTML = rows
    .map((host) => {
      const selected = host.id === state.selectedHostId ? " selected" : "";
      return `
        <tr class="${selected}" data-host-id="${escapeHtml(host.id)}">
          <td>${escapeHtml(host.hostname)}</td>
          <td>${escapeHtml(host.ip)}</td>
          <td>${escapeHtml(`${host.os_name} ${host.os_version}`)}</td>
          <td>${escapeHtml(host.department)}</td>
          <td>${escapeHtml(host.asset_type)}</td>
          <td>${escapeHtml(host.users_count || 0)}</td>
          <td>${escapeHtml(host.software_count || 0)}</td>
          <td>${escapeHtml(host.ports_count || 0)}</td>
          <td>${escapeHtml(host.last_query_at || "")}</td>
          <td>${escapeHtml(host.status)}</td>
          <td><span class="risk ${riskClass(host.risk_score)}">${escapeHtml(host.risk_score)}</span></td>
        </tr>
      `;
    })
    .join("");
  [...document.querySelectorAll("#assetRows tr")].forEach((row) => {
    row.addEventListener("click", () => selectHost(row.dataset.hostId));
  });
}

async function selectHost(hostId) {
  state.selectedHostId = hostId;
  renderAssets();
  const detail = await api(`/api/assets/${encodeURIComponent(hostId)}`);
  $("selectedHost").textContent = detail.host.hostname;
  const sections = detail.sections;
  $("detailContent").innerHTML = `
    <div class="detail-grid">
      ${detailBox("系统", sections.system)}
      ${detailBox("用户", sections.users)}
      ${detailBox("进程", sections.processes)}
      ${detailBox("端口", sections.ports)}
      ${detailBox("软件", sections.software)}
      ${detailBox("计划任务", sections.scheduled_tasks)}
      ${detailBox("启动项", sections.startup_items)}
      ${detailBox("内核模块", sections.kernel_modules)}
      ${detailBox("文件哈希", sections.file_hashes)}
      ${detailBox("基线", detail.findings.filter((item) => item.status === "failed"))}
    </div>
  `;
}

function detailBox(title, rows) {
  const list = Array.isArray(rows) ? rows.slice(0, 5) : rows ? [rows] : [];
  return `
    <div class="detail-box">
      <h3>${escapeHtml(title)}</h3>
      <ul>
        ${
          list.length
            ? list.map((row) => `<li>${escapeHtml(summarizeRow(row))}</li>`).join("")
            : "<li>暂无数据</li>"
        }
      </ul>
    </div>
  `;
}

function summarizeRow(row) {
  if (row.title) return `${row.title}：${row.evidence}`;
  return Object.entries(row)
    .slice(0, 4)
    .map(([key, value]) => `${key}=${value}`)
    .join("，");
}

function renderFailures(findings) {
  $("failureList").innerHTML = findings.length
    ? findings
        .map(
          (finding) => `
            <div class="failure-item">
              <strong>${escapeHtml(finding.hostname)} / ${escapeHtml(finding.title)}</strong>
              <span class="severity">${escapeHtml(finding.severity)}</span>
              <span>${escapeHtml(finding.evidence)}</span>
              <p>${escapeHtml(finding.recommendation)}</p>
            </div>
          `,
        )
        .join("")
    : "<div class='failure-item'>暂无失败项。</div>";
}

function renderDepartments(departments) {
  $("departmentGrid").innerHTML = departments.length
    ? departments
        .map(
          (department) => `
            <div class="department-card">
              <h3>${escapeHtml(department.department)}</h3>
              <dl>
                <dt>资产数</dt><dd>${escapeHtml(department.asset_count)}</dd>
                <dt>在线数</dt><dd>${escapeHtml(department.online_count)}</dd>
                <dt>平均风险</dt><dd>${escapeHtml(department.avg_risk_score)}</dd>
                <dt>合规率</dt><dd>${escapeHtml(department.baseline_pass_rate)}%</dd>
              </dl>
            </div>
          `,
        )
        .join("")
    : "<div class='department-card'>暂无部门数据。</div>";
}

function renderTasks(tasks) {
  $("taskCount").textContent = String(tasks.length);
  $("taskList").innerHTML = tasks.length
    ? tasks
        .slice(0, 8)
        .map(
          (task) => `
            <div class="task-item">
              <strong>${escapeHtml(task.status)} / ${escapeHtml(task.schedule)} / ${escapeHtml(task.target)}</strong>
              <code>${escapeHtml(task.query)}</code>
              <span>started=${escapeHtml(task.started_at)} finished=${escapeHtml(task.finished_at || "-")} result=${escapeHtml(task.result_count)}</span>
              ${task.error ? `<p>${escapeHtml(task.error)}</p>` : ""}
            </div>
          `,
        )
        .join("")
    : "<div class='task-item'>暂无任务。</div>";
}

function renderSchedules(schedules) {
  $("scheduleList").innerHTML = schedules.length
    ? schedules
        .slice(0, 8)
        .map(
          (schedule) => `
            <div class="task-item">
              <strong>${escapeHtml(schedule.status)} / ${escapeHtml(schedule.schedule)} / ${escapeHtml(schedule.target)}</strong>
              <code>${escapeHtml(schedule.template_id)} next=${escapeHtml(schedule.next_run_at)}</code>
              <span>retry=${escapeHtml(schedule.retry_count)}/${escapeHtml(schedule.retry_limit)} last=${escapeHtml(schedule.last_run_id || "-")}</span>
            </div>
          `,
        )
        .join("")
    : "<div class='task-item'>暂无周期巡检任务。</div>";
}

function renderAuditLogs(logs) {
  $("auditList").innerHTML = logs.length
    ? logs
        .slice(0, 8)
        .map(
          (log) => `
            <div class="audit-item">
              <strong>${escapeHtml(log.actor)} / ${escapeHtml(log.action)} / ${escapeHtml(log.ts)}</strong>
              <code>${escapeHtml(log.detail)}</code>
            </div>
          `,
        )
        .join("")
    : "<div class='audit-item'>暂无审计日志。</div>";
}

function renderChanges(changes) {
  if (!changes.previous_run_id) {
    $("changesList").innerHTML = "<div class='change-item'>暂无上一快照，完成第二次执行后展示变更。</div>";
    return;
  }
  $("changesList").innerHTML = changes.changes.length
    ? changes.changes
        .slice(0, 6)
        .map((change) => `<div class="change-item">${escapeHtml(change.hostname)}：${escapeHtml(change.type)} / ${escapeHtml(JSON.stringify(change.detail))}</div>`)
        .join("")
    : "<div class='change-item'>与上一快照相比未发现变化。</div>";
}

async function runDemo() {
  $("runStatus").textContent = "执行中";
  await api("/api/runs/demo", { method: "POST", body: JSON.stringify({ actor: "browser" }) });
  await refreshAll();
}

async function importResult() {
  const raw = $("importText").value.trim();
  if (!raw) {
    alert("请先粘贴 JSON 结果。");
    return;
  }
  const payload = JSON.parse(raw);
  payload.actor = "browser";
  payload.source = "browser-json";
  await api("/api/runs/import", { method: "POST", body: JSON.stringify(payload) });
  await refreshAll();
}

async function generateReport() {
  const report = await api("/api/reports", { method: "POST", body: JSON.stringify({ actor: "browser" }) });
  $("reportPath").textContent = report.report_path;
  await refreshAll();
}

async function addDailySchedule() {
  await api("/api/schedules", {
    method: "POST",
    body: JSON.stringify({ actor: "browser", template_id: $("templateSelect").value || "host_asset_core", target: "demo", schedule: "daily" }),
  });
  await api("/api/schedules/run", { method: "POST", body: JSON.stringify({ actor: "browser", force: true }) });
  await refreshAll();
}

function bindEvents() {
  $("templateSelect").addEventListener("change", renderTemplateDetail);
  $("refreshButton").addEventListener("click", refreshAll);
  $("demoRunButton").addEventListener("click", runDemo);
  $("importButton").addEventListener("click", importResult);
  $("reportButton").addEventListener("click", generateReport);
  $("scheduleButton").addEventListener("click", addDailySchedule);
  $("assetFilter").addEventListener("input", renderAssets);
}

async function boot() {
  bindEvents();
  await loadTemplates();
  await refreshAll();
}

boot().catch((error) => {
  console.error(error);
  $("runStatus").textContent = error.message;
});
