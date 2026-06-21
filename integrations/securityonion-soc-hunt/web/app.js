const state = {
  selectedAlert: null,
};

const api = async (path, options = {}) => {
  const token = localStorage.getItem("soc_hunt_token") || "";
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(path, {
    headers,
    ...options,
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({ error: response.statusText }));
    throw new Error(err.error || response.statusText);
  }
  return response.json();
};

const severityBadge = (severity) => `<span class="badge ${severity}">${severity}</span>`;

async function refreshAll() {
  await Promise.all([loadSummary(), loadAlerts(), loadCases(), loadHunts(), loadHealth(), loadAudit()]);
}

async function loadSummary() {
  const summary = await api("/api/summary");
  const metrics = [
    ["告警总数", summary.alerts_total],
    ["打开案件", summary.open_cases],
    ["案件总数", summary.cases_total],
    ["狩猎任务", summary.hunt_tasks_total],
    ["高危以上", (summary.by_severity.critical || 0) + (summary.by_severity.high || 0)],
  ];
  document.querySelector("#overview").innerHTML = metrics
    .map(([label, value]) => `<article class="metric"><strong>${value}</strong><span>${label}</span></article>`)
    .join("");
  document.querySelector("#recentHighs").innerHTML = `
    <div class="section-head"><div><h2>近期高危事件</h2><p>critical / high 告警优先进入研判。</p></div></div>
    ${(summary.recent_high_alerts || []).slice(0, 6).map((alert) => `
      <div class="recent-row">
        <strong>${severityBadge(alert.severity)}</strong>
        <span>${escapeHtml(alert.asset || "-")}</span>
        <span>${escapeHtml(alert.rule || "-")}</span>
      </div>
    `).join("") || '<p class="muted">暂无高危事件</p>'}
  `;
}

async function loadAlerts() {
  const query = encodeURIComponent(document.querySelector("#search").value.trim());
  const severity = encodeURIComponent(document.querySelector("#severity").value);
  const source = encodeURIComponent(document.querySelector("#source").value);
  const asset = encodeURIComponent(document.querySelector("#asset").value.trim());
  const rule = encodeURIComponent(document.querySelector("#rule").value.trim());
  const tags = encodeURIComponent(document.querySelector("#tags").value.trim());
  const status = encodeURIComponent(document.querySelector("#status").value);
  const alerts = await api(`/api/alerts?query=${query}&severity=${severity}&source=${source}&asset=${asset}&rule=${rule}&tags=${tags}&status=${status}`);
  document.querySelector("#alertRows").innerHTML = alerts.map(alertRow).join("");
}

function alertRow(alert) {
  return `
    <tr data-alert-id="${escapeHtml(alert.id)}" onclick="showAlert(this.dataset.alertId)">
      <td>${severityBadge(alert.severity)}</td>
      <td>${escapeHtml(alert.source)}<br><span class="muted">${escapeHtml(alert.event_type)}</span></td>
      <td>${escapeHtml(alert.asset)}</td>
      <td>${escapeHtml(alert.src_ip)} -> ${escapeHtml(alert.dst_ip)}</td>
      <td>${escapeHtml(alert.rule)}<br><span class="muted">${escapeHtml(alert.tags || "")}</span></td>
      <td>${escapeHtml(alert.status)}<br><button type="button" onclick="showAlert('${escapeJs(alert.id)}')">详情</button></td>
    </tr>
  `;
}

async function showAlert(alertId) {
  const alert = await api(`/api/alerts/${alertId}`);
  state.selectedAlert = alert;
  document.querySelector("#alertDetail").innerHTML = `
    <h3>${escapeHtml(alert.rule)}</h3>
    <p>${severityBadge(alert.severity)} ${alert.source} / ${alert.event_type}</p>
    <p><strong>资产：</strong>${escapeHtml(alert.asset)}</p>
    <p><strong>通信：</strong>${escapeHtml(alert.src_ip)} -> ${escapeHtml(alert.dst_ip)}</p>
    <p><strong>标签：</strong>${escapeHtml(alert.tags || "-")}</p>
    <button class="primary" id="createCase">创建案件</button>
    <pre>${escapeHtml(alert.raw_event)}</pre>
  `;
  document.querySelector("#createCase").addEventListener("click", createCaseFromAlert);
}

async function createCaseFromAlert() {
  if (!state.selectedAlert) return;
  await api("/api/cases", {
    method: "POST",
    body: JSON.stringify({
      title: `调查 ${state.selectedAlert.asset} ${state.selectedAlert.rule}`,
      alert_id: state.selectedAlert.id,
      summary: `由告警 ${state.selectedAlert.id} 创建，需确认影响范围、攻击链阶段和处置建议。`,
      assignee: "demo-analyst",
      actor: "demo-analyst",
    }),
  });
  await refreshAll();
}

async function loadCases() {
  const cases = await api("/api/cases");
  const template = document.querySelector("#caseTemplate");
  const list = document.querySelector("#caseList");
  list.innerHTML = "";
  cases.forEach((item) => {
    const node = template.content.cloneNode(true);
    node.querySelector(".case-card").dataset.caseId = item.id;
    node.querySelector(".case-title").textContent = `#${item.id} ${item.title}`;
    node.querySelector(".case-meta").innerHTML = `${severityBadge(item.severity)} 状态：${escapeHtml(item.status)} 负责人：${escapeHtml(item.assignee || "未分派")} 复核：${escapeHtml(item.reviewer || "未复核")} 关联告警：${escapeHtml(item.alert_id || "无")}`;
    node.querySelector(".case-records").innerHTML = item.records
      .map((record) => `<div>${record.created_at} ${record.analyst} ${record.action}: ${escapeHtml(record.note)}</div>`)
      .join("");
    node.querySelector(".record-form").addEventListener("submit", submitRecord);
    node.querySelectorAll("[data-status]").forEach((button) => button.addEventListener("click", changeStatus));
    node.querySelector("[data-review]").addEventListener("click", reviewCase);
    node.querySelector("[data-export]").addEventListener("click", exportReport);
    list.appendChild(node);
  });
}

async function submitRecord(event) {
  event.preventDefault();
  const card = event.target.closest(".case-card");
  const form = new FormData(event.target);
  await api(`/api/cases/${card.dataset.caseId}/records`, {
    method: "POST",
    body: JSON.stringify({
      action: "analysis",
      note: form.get("note"),
      analyst: "demo-analyst",
    }),
  });
  event.target.reset();
  await refreshAll();
}

async function changeStatus(event) {
  const card = event.target.closest(".case-card");
  await api(`/api/cases/${card.dataset.caseId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status: event.target.dataset.status, actor: "demo-analyst" }),
  });
  await refreshAll();
}

async function reviewCase(event) {
  const card = event.target.closest(".case-card");
  await api(`/api/cases/${card.dataset.caseId}/review`, {
    method: "PATCH",
    body: JSON.stringify({ reviewer: "lead-analyst", note: "已完成复核，处置记录可追溯。", actor: "lead-analyst" }),
  });
  await refreshAll();
}

async function exportReport(event) {
  const card = event.target.closest(".case-card");
  const result = await api("/api/reports", {
    method: "POST",
    body: JSON.stringify({ case_id: Number(card.dataset.caseId), actor: "demo-analyst" }),
  });
  alert(`报告已导出：${result.report}`);
  await loadAudit();
}

async function loadHunts() {
  const hunts = await api("/api/hunts");
  document.querySelector("#huntList").innerHTML = hunts
    .map((hunt) => `<article class="task" data-hunt-id="${hunt.id}">
      <strong>${escapeHtml(hunt.title)}</strong><br>
      ${escapeHtml(hunt.query)}<br>
      假设：${escapeHtml(hunt.hypothesis || hunt.notes || "-")}<br>
      结果：${escapeHtml(hunt.result || "-")}<br>
      结论：${escapeHtml(hunt.conclusion || "-")}<br>
      ${escapeHtml(hunt.status)} / ${escapeHtml(hunt.owner)} / 案件：${escapeHtml(hunt.case_id || "无")}
      <div class="case-actions"><button data-hunt-export>导出狩猎报告</button></div>
    </article>`)
    .join("");
  document.querySelectorAll("[data-hunt-export]").forEach((button) => button.addEventListener("click", exportHuntReport));
}

async function exportHuntReport(event) {
  const item = event.target.closest("[data-hunt-id]");
  const result = await api("/api/hunt-reports", {
    method: "POST",
    body: JSON.stringify({ hunt_id: Number(item.dataset.huntId), actor: "demo-analyst" }),
  });
  alert(`狩猎报告已导出：${result.report}`);
  await loadAudit();
}

async function loadHealth() {
  const rows = await api("/api/health");
  document.querySelector("#healthList").innerHTML = rows.length
    ? rows.map((row) => `<div class="health-row">
        <strong>${escapeHtml(row.component)}</strong>
        <span class="status-${escapeHtml(row.status)}">${escapeHtml(row.status)}</span>
        <span>${escapeHtml(row.last_seen)} · ${escapeHtml(row.detail || "")}</span>
      </div>`).join("")
    : '<p class="muted">暂无数据源健康记录，请先导入 demo。</p>';
}

async function loadAudit() {
  const logs = await api("/api/audit");
  document.querySelector("#auditList").innerHTML = logs
    .map((log) => `<div class="audit-item">${log.created_at} ${log.actor} ${log.action} ${log.entity_type}:${log.entity_id} ${escapeHtml(log.detail || "")}</div>`)
    .join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeJs(value) {
  return String(value ?? "").replaceAll("\\", "\\\\").replaceAll("'", "\\'");
}

document.querySelector("#importDemo").addEventListener("click", async () => {
  await api("/api/import-demo", { method: "POST", body: JSON.stringify({ actor: "demo-analyst" }) });
  await refreshAll();
});

document.querySelector("#apiToken").value = localStorage.getItem("soc_hunt_token") || "";
document.querySelector("#saveToken").addEventListener("click", () => {
  localStorage.setItem("soc_hunt_token", document.querySelector("#apiToken").value.trim());
  refreshAll().catch((error) => {
    document.body.insertAdjacentHTML("afterbegin", `<div class="error">${escapeHtml(error.message)}</div>`);
  });
});

document.querySelector("#resetDemo").addEventListener("click", async () => {
  await api("/api/reset-demo", { method: "POST", body: JSON.stringify({ actor: "demo-analyst" }) });
  await refreshAll();
});

document.querySelector("#dailyReport").addEventListener("click", async () => {
  const result = await api("/api/summary-reports", {
    method: "POST",
    body: JSON.stringify({ period: "daily", actor: "demo-analyst" }),
  });
  alert(`日报已导出：${result.report}`);
  await loadAudit();
});

document.querySelector("#search").addEventListener("input", loadAlerts);
document.querySelector("#severity").addEventListener("change", loadAlerts);
document.querySelector("#source").addEventListener("change", loadAlerts);
document.querySelector("#asset").addEventListener("input", loadAlerts);
document.querySelector("#rule").addEventListener("input", loadAlerts);
document.querySelector("#tags").addEventListener("input", loadAlerts);
document.querySelector("#status").addEventListener("change", loadAlerts);
document.querySelector("#huntForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.target).entries());
  if (data.case_id) data.case_id = Number(data.case_id);
  await api("/api/hunts", { method: "POST", body: JSON.stringify({ ...data, owner: "demo-analyst" }) });
  event.target.reset();
  await refreshAll();
});

refreshAll().catch((error) => {
  document.body.insertAdjacentHTML("afterbegin", `<div class="error">${escapeHtml(error.message)}</div>`);
});
