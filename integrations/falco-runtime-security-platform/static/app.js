const state = {
  events: [],
  selectedEvent: null,
  filters: {},
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  return response.json();
}

function priorityClass(priority) {
  return String(priority || "").toLowerCase();
}

function toast(message) {
  const el = $("#toast");
  el.textContent = message;
  el.classList.add("show");
  window.setTimeout(() => el.classList.remove("show"), 2600);
}

function renderRank(target, rows, emptyText = "暂无数据") {
  const el = $(target);
  if (!rows || rows.length === 0) {
    el.innerHTML = `<p class="muted">${emptyText}</p>`;
    return;
  }
  el.innerHTML = rows
    .map((row) => `<div class="rank-row"><span title="${escapeHtml(row.name)}">${escapeHtml(row.name)}</span><strong>${row.count}</strong></div>`)
    .join("");
}

function renderTrend(rows) {
  const el = $("#trend-chart");
  if (!rows || rows.length === 0) {
    el.innerHTML = `<p class="muted">导入事件后展示时间趋势。</p>`;
    return;
  }
  const max = Math.max(...rows.map((row) => row.count), 1);
  el.innerHTML = rows
    .map((row) => {
      const height = Math.max(18, Math.round((row.count / max) * 210));
      const label = row.bucket.slice(5, 13).replace("T", " ");
      return `<div class="bar" data-high="${row.high > 0}" style="--height:${height}px" title="${escapeHtml(row.bucket)}: ${row.count}"><span>${row.count}</span></div>`;
    })
    .join("");
}

async function loadDashboard() {
  const data = await api("/api/dashboard");
  $("#metric-total").textContent = data.total_events;
  $("#metric-high").textContent = data.high_events;
  $("#metric-risk").textContent = data.avg_risk_score;
  $("#metric-disposition").textContent = `${data.suppressed_events} / ${data.open_tickets}`;
  renderTrend(data.time_trend);
  renderRank("#rule-hits", data.rule_hits);
  renderRank("#priority-breakdown", data.priority_breakdown);
  renderRank("#container-distribution", data.container_distribution);
  renderRank("#host-distribution", data.host_distribution);
}

function queryString(filters = state.filters) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  return params.toString();
}

async function loadEvents() {
  const query = queryString();
  const data = await api(`/api/events${query ? `?${query}` : ""}`);
  state.events = data.events;
  $("#event-count").textContent = `${data.total} 条事件`;
  const tbody = $("#events-body");
  if (data.events.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7" class="muted">暂无事件。可先导入 demo 事件，或用 API 导入授权 Falco JSON。</td></tr>`;
    return;
  }
  tbody.innerHTML = data.events
    .map(
      (event) => `
        <tr data-id="${event.id}">
          <td>${formatTime(event.event_time)}</td>
          <td><span class="tag ${priorityClass(event.priority)}">${event.priority}</span></td>
          <td><strong>${event.risk_score}</strong></td>
          <td>${escapeHtml(event.rule)}<div class="muted">${escapeHtml(event.output).slice(0, 90)}</div></td>
          <td>${escapeHtml(event.namespace || "host")}<div class="muted">${escapeHtml(event.pod || event.node || "unknown")}</div></td>
          <td>${escapeHtml(event.container_name || event.container_id || "host")}<div class="muted">${escapeHtml(event.process_name || "unknown")}</div></td>
          <td>${escapeHtml(event.status)}${event.false_positive ? '<div class="muted">误报</div>' : ""}</td>
        </tr>`
    )
    .join("");
  tbody.querySelectorAll("tr[data-id]").forEach((row) => {
    row.addEventListener("click", () => openEvent(Number(row.dataset.id)));
  });
}

async function openEvent(id) {
  const event = await api(`/api/events/${id}`);
  state.selectedEvent = event;
  $("#drawer-priority").textContent = event.priority;
  $("#drawer-priority").className = `tag ${priorityClass(event.priority)}`;
  $("#drawer-rule").textContent = event.rule;
  $("#drawer-content").innerHTML = [
    ["时间", formatTime(event.event_time)],
    ["风险评分", event.risk_score],
    ["状态", event.status],
    ["namespace", event.namespace || "-"],
    ["pod", event.pod || "-"],
    ["container", event.container_name || event.container_id || "-"],
    ["node", event.node || event.hostname || "-"],
    ["image", event.image || "-"],
    ["process", `${event.process_name || "-"} ${event.process_pid || ""}`],
    ["command", event.command || "-"],
    ["user", event.user_name || "-"],
    ["tags", (event.tags || []).join(", ") || "-"],
    ["labels", Object.entries(event.labels || {}).map(([key, value]) => `${key}=${value}`).join(", ") || "-"],
    ["suppression", event.suppressed ? event.suppression_reason || "suppressed" : "-"],
    ["dedupe", event.dedupe_count || 1],
    ["输出", event.output || "-"],
    ["处置说明", event.disposition_note || "-"],
  ]
    .map(([key, value]) => `<div class="kv"><span>${key}</span><span>${escapeHtml(String(value))}</span></div>`)
    .join("");
  $("#event-drawer").classList.add("open");
  $("#event-drawer").setAttribute("aria-hidden", "false");
}

async function loadRules() {
  const data = await api("/api/rules");
  const list = $("#rules-list");
  if (data.rules.length === 0) {
    list.innerHTML = `<p class="muted">暂无规则命中。</p>`;
    return;
  }
  list.innerHTML = data.rules
    .map((rule) => `<button class="rule-row" data-rule="${encodeURIComponent(rule.name)}"><span>${escapeHtml(rule.name)}</span><strong>${rule.count}</strong></button>`)
    .join("");
  list.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => loadRuleDetail(decodeURIComponent(button.dataset.rule)));
  });
}

async function loadRuleDetail(ruleName) {
  const data = await api(`/api/rules/${encodeURIComponent(ruleName)}`);
  $("#rule-detail-title").textContent = data.rule;
  $("#rule-detail-meta").textContent = `${data.hits} 次命中，最高风险 ${data.highest_risk}`;
  $("#rule-detail").innerHTML = `
    <p>${escapeHtml(data.description || "")}</p>
    <p>严重等级：${data.priorities.map((row) => `${escapeHtml(row.name)} ${row.count}`).join(" / ")}</p>
    <p>标签：${data.tags.map(escapeHtml).join(", ") || "无"}</p>
    <h3>近期事件</h3>
    ${data.latest_events
      .slice(0, 6)
      .map((event) => `<button class="rule-event" data-id="${event.id}">[${event.priority}] ${escapeHtml(event.output).slice(0, 110)}</button>`)
      .join("")}
  `;
  $("#rule-detail").querySelectorAll(".rule-event").forEach((button) => {
    button.addEventListener("click", () => openEvent(Number(button.dataset.id)));
  });
}

async function loadAssets() {
  const [containers, processes, commands] = await Promise.all([api("/api/containers"), api("/api/processes"), api("/api/commands")]);
  renderRank("#containers-list", containers.containers);
  renderRank("#processes-list", processes.processes);
  const list = $("#commands-list");
  list.innerHTML = commands.commands.length
    ? commands.commands.map((row) => `<code>${escapeHtml(row.name)} · ${row.count}</code>`).join("")
    : `<p class="muted">暂无命令信息。</p>`;
}

async function loadSettings() {
  const [whitelist, notifications, tickets, deliveries, batches, reports] = await Promise.all([
    api("/api/whitelist"),
    api("/api/notifications"),
    api("/api/tickets"),
    api("/api/notification-deliveries"),
    api("/api/replay-batches"),
    api("/api/reports"),
  ]);
  $("#whitelist-list").innerHTML = whitelist.whitelist.length
    ? whitelist.whitelist.map((item) => `<div class="setting-row"><span>${escapeHtml(item.name)}</span><strong>${item.enabled ? "启用" : "停用"}</strong></div>`).join("")
    : `<p class="muted">暂无白名单。</p>`;
  $("#notifications-list").innerHTML = notifications.notifications.length
    ? notifications.notifications.map((item) => `<div class="setting-row"><span>${escapeHtml(item.name)} · ${escapeHtml(item.channel)}</span><strong>${escapeHtml(item.min_priority)}</strong></div>`).join("")
    : `<p class="muted">暂无通知配置。</p>`;
  $("#tickets-list").innerHTML = tickets.tickets.length
    ? tickets.tickets
        .map(
          (item) => `<div class="setting-row"><span>${escapeHtml(item.title)}</span><button class="link-button ticket-close" data-id="${item.id}">${escapeHtml(item.status)}</button></div>`
        )
        .join("")
    : `<p class="muted">暂无工单。</p>`;
  $("#deliveries-list").innerHTML = deliveries.deliveries.length
    ? deliveries.deliveries
        .map(
          (item) => `<div class="setting-row"><span>${escapeHtml(item.channel)} · ${escapeHtml(item.target)}</span><button class="link-button delivery-send" data-id="${item.id}">${escapeHtml(item.status)}</button></div>`
        )
        .join("")
    : `<p class="muted">暂无通知投递。</p>`;
  $("#batches-list").innerHTML = batches.batches.length
    ? batches.batches.map((item) => `<div class="setting-row"><span>${escapeHtml(item.source_name)} · ${item.event_count} events</span><strong>${item.replayed_at ? "已重放" : "可重放"}</strong></div>`).join("")
    : `<p class="muted">暂无接入批次。</p>`;
  $("#reports-list").innerHTML = reports.reports.length
    ? reports.reports
        .map((item) => `<div class="setting-row"><span>${escapeHtml(item.name)}</span><a class="link-button" href="/api/reports/${encodeURIComponent(item.name)}">下载</a></div>`)
        .join("")
    : `<p class="muted">暂无报告。</p>`;
  $("#tickets-list").querySelectorAll(".ticket-close").forEach((button) => {
    button.addEventListener("click", async () => {
      await api(`/api/tickets/${button.dataset.id}`, { method: "PATCH", body: JSON.stringify({ status: "closed" }) });
      toast("工单已关闭");
      await refreshAll();
    });
  });
  $("#deliveries-list").querySelectorAll(".delivery-send").forEach((button) => {
    button.addEventListener("click", async () => {
      const result = await api(`/api/notification-deliveries/${button.dataset.id}/deliver`, { method: "POST", body: "{}" });
      toast(`通知投递状态：${result.status}`);
      await refreshAll();
    });
  });
}

function formToPayload(form) {
  const payload = {};
  new FormData(form).forEach((value, key) => {
    payload[key] = value;
  });
  return payload;
}

async function refreshAll() {
  await Promise.all([loadDashboard(), loadEvents(), loadRules(), loadAssets(), loadSettings()]);
}

async function importDemo() {
  const response = await fetch("/demo-falco-events.ndjson");
  const text = await response.text();
  const result = await api("/api/import", {
    method: "POST",
    body: JSON.stringify({ text }),
  });
  toast(`导入 ${result.imported} 条，去重 ${result.deduped} 条`);
  await refreshAll();
}

function download(path) {
  window.location.href = path;
}

function wireEvents() {
  $$(".nav-item").forEach((button) => {
    button.addEventListener("click", () => {
      closeDrawer();
      $$(".nav-item").forEach((item) => item.classList.remove("active"));
      $$(".view").forEach((view) => view.classList.remove("active"));
      button.classList.add("active");
      $(`#${button.dataset.view}`).classList.add("active");
    });
  });
  $("#apply-filters").addEventListener("click", async () => {
    state.filters = {
      q: $("#filter-q").value.trim(),
      priority: $("#filter-priority").value,
      namespace: $("#filter-namespace").value.trim(),
    };
    await loadEvents();
  });
  $("#import-demo").addEventListener("click", importDemo);
  $("#export-json").addEventListener("click", () => download(`/api/export?${queryString({ ...state.filters, format: "json" })}`));
  $("#export-csv").addEventListener("click", () => download(`/api/export?${queryString({ ...state.filters, format: "csv" })}`));
  $("#make-report").addEventListener("click", async () => {
    const result = await api("/api/report", { method: "POST", body: JSON.stringify({ type: $("#report-type").value }) });
    toast(`报告已生成：${result.path}`);
    await loadSettings();
  });
  $("#drawer-close").addEventListener("click", closeDrawer);
  $("#disposition-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!state.selectedEvent) return;
    const payload = formToPayload(event.currentTarget);
    payload.false_positive = Boolean(payload.false_positive);
    const updated = await api(`/api/events/${state.selectedEvent.id}/disposition`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    toast("处置状态已保存");
    await refreshAll();
    await openEvent(updated.id);
  });
  $("#whitelist-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await api("/api/whitelist", { method: "POST", body: JSON.stringify(formToPayload(event.currentTarget)) });
    event.currentTarget.reset();
    toast("白名单已保存");
    await refreshAll();
  });
  $("#notification-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await api("/api/notifications", { method: "POST", body: JSON.stringify(formToPayload(event.currentTarget)) });
    event.currentTarget.reset();
    toast("通知配置已保存");
    await loadSettings();
  });
}

function closeDrawer() {
  $("#event-drawer").classList.remove("open");
  $("#event-drawer").setAttribute("aria-hidden", "true");
}

function formatTime(value) {
  if (!value) return "-";
  return value.replace("T", " ").replace("Z", "");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

wireEvents();
refreshAll().catch((error) => toast(`加载失败：${error.message}`));
