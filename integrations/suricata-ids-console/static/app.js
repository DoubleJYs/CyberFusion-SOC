const state = {
  page: 1,
  pageSize: 20,
  filters: {},
};

const $ = (selector) => document.querySelector(selector);

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  return response.json();
}

function query(params) {
  const data = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      data.set(key, value);
    }
  });
  return data.toString();
}

async function refreshAll() {
  await Promise.all([loadSummary(), loadAlerts(), loadAggregation(), loadTimeline(), loadIpAnalysis(), loadImportLogs(), loadWhitelist()]);
}

async function loadSummary() {
  const data = await api("/api/summary");
  const totals = data.totals || {};
  const metrics = [
    ["告警总数", totals.total || 0],
    ["高危告警", totals.high || 0],
    ["规则数量", totals.signatures || 0],
    ["源 IP", totals.source_ips || 0],
    ["目的 IP", totals.destination_ips || 0],
    ["误报标记", totals.false_positive || 0],
  ];
  $("#metrics").innerHTML = metrics
    .map(([label, value]) => `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`)
    .join("");
  renderTrend(data.trend || []);
  renderListBars("#categories", data.categories || [], "category");
  renderListBars("#sources", data.top_sources || [], "src_ip");
  renderListBars("#destinations", data.top_destinations || [], "dest_ip");
}

function renderTrend(items) {
  const max = Math.max(1, ...items.map((item) => item.count));
  $("#trendCount").textContent = `${items.length} buckets`;
  $("#trend").innerHTML = items.length
    ? items
        .map((item) => {
          const height = Math.max(4, Math.round((item.count / max) * 170));
          return `<div class="bar" title="${item.bucket} ${item.count}" style="height:${height}px"><span>${item.count}</span></div>`;
        })
        .join("")
    : `<p class="flag">暂无趋势数据</p>`;
}

function renderListBars(selector, items, key) {
  const max = Math.max(1, ...items.map((item) => item.count));
  $(selector).innerHTML = items.length
    ? items
        .map((item) => {
          const width = Math.max(3, Math.round((item.count / max) * 100));
          const label = item[key] ?? "unknown";
          return `<div class="list-row"><label title="${escapeHtml(label)}">${escapeHtml(label)}</label><strong>${item.count}</strong><div class="barline"><i style="width:${width}%"></i></div></div>`;
        })
        .join("")
    : `<p class="flag">暂无数据</p>`;
}

async function loadAlerts() {
  const params = {
    ...state.filters,
    page: state.page,
    page_size: state.pageSize,
  };
  const data = await api(`/api/alerts?${query(params)}`);
  $("#alertsBody").innerHTML = data.items.length
    ? data.items.map(renderAlertRow).join("")
    : `<tr><td colspan="8" class="flag">暂无告警</td></tr>`;
  $("#pageInfo").textContent = `第 ${data.page} 页 / ${data.total} 条`;
  $("#prevPage").disabled = data.page <= 1;
  $("#nextPage").disabled = data.page * data.page_size >= data.total;
  $("#exportLink").href = `/api/export.csv?${query(state.filters)}`;
}

function renderAlertRow(item) {
  const status = item.false_positive
    ? `<span class="flag fp">误报</span>`
    : item.ignored
      ? `<span class="flag fp">已忽略</span>`
    : item.ticket_id
      ? `<span class="flag">工单 #${item.ticket_id}</span>`
      : `<span class="flag">待分析</span>`;
  return `<tr data-alert-id="${item.id}">
    <td>${escapeHtml(item.timestamp)}</td>
    <td><span class="badge ${item.severity_label}">${item.severity_label}</span></td>
    <td class="sig" title="${escapeHtml(item.signature)}">${escapeHtml(item.signature)}</td>
    <td>${escapeHtml(item.category)}</td>
    <td>${escapeHtml(endpoint(item.src_ip, item.src_port))}</td>
    <td>${escapeHtml(endpoint(item.dest_ip, item.dest_port))}</td>
    <td>${escapeHtml(item.proto)}</td>
    <td>${status}</td>
  </tr>`;
}

async function loadAggregation() {
  const data = await api("/api/aggregation");
  const groups = data.duplicate_groups || [];
  $("#aggregation").innerHTML = groups.length
    ? groups.slice(0, 12).map((group) => `<div class="compact-row">
      <label title="${escapeHtml(group.signature)}">${escapeHtml(group.signature)}<br><span class="flag">${escapeHtml(group.src_ip)} -> ${escapeHtml(group.dest_ip)} · ${escapeHtml(group.proto)} · ${escapeHtml(group.time_window)}</span></label>
      <strong>${group.count}</strong>
    </div>`).join("")
    : `<p class="flag">暂无重复告警组</p>`;
}

async function loadImportLogs() {
  const data = await api("/api/import-logs");
  $("#importLogs").innerHTML = data.items.length
    ? data.items.slice(0, 10).map((item) => `<div class="compact-row">
      <label title="${escapeHtml(item.source_path)}">${escapeHtml(item.status)} · ${escapeHtml(item.message)}<br><span class="flag">${escapeHtml(item.source_path)} · ${item.imported_alerts} imported / ${item.skipped_events} skipped</span></label>
      <strong>${escapeHtml(item.created_at)}</strong>
    </div>`).join("")
    : `<p class="flag">暂无导入日志</p>`;
}

async function loadWhitelist() {
  const data = await api("/api/whitelist");
  $("#whitelist").innerHTML = data.items.length
    ? data.items.slice(0, 10).map((item) => `<div class="compact-row"><label>${escapeHtml(item.kind)} = ${escapeHtml(item.value)}<br><span class="flag">${escapeHtml(item.reason || "")}</span></label><strong>${item.enabled ? "启用" : "停用"}</strong></div>`).join("")
    : `<p class="flag">暂无白名单</p>`;
}

async function loadTimeline() {
  const data = await api(`/api/timeline?${query(state.filters)}`);
  $("#timeline").innerHTML = data.length
    ? data.slice(0, 40).map((item) => `<div class="event">
      <time>${escapeHtml(item.timestamp)}</time>
      <div><span class="badge ${item.severity_label}">${item.severity_label}</span> ${escapeHtml(item.signature)}</div>
      <div class="flag">${escapeHtml(item.src_ip)} -> ${escapeHtml(item.dest_ip)} · ${escapeHtml(item.proto)}</div>
    </div>`).join("")
    : `<p class="flag">暂无时间线数据</p>`;
}

async function loadIpAnalysis() {
  const ip = $("#focusIp").value.trim();
  const data = await api(`/api/ip-analysis?${query({ ip })}`);
  const rows = (data.focus_ip ? data.peers : data.top_ips) || [];
  $("#ipAnalysis").innerHTML = rows.length
    ? rows.slice(0, 16).map((row) => {
        if (data.focus_ip) {
          return `<div class="compact-row"><label>${escapeHtml(row.peer_ip)}<br><span class="flag">入站 ${row.inbound_count || 0} · 出站 ${row.outbound_count || 0}</span></label><strong>${row.total}</strong></div>`;
        }
        return `<div class="compact-row"><label>${escapeHtml(row.ip)}</label><strong>${row.count}</strong></div>`;
      }).join("")
    : `<p class="flag">暂无 IP 分析数据</p>`;
}

async function openDetail(alertId) {
  const item = await api(`/api/alerts/${alertId}`);
  $("#detailContent").innerHTML = `<div class="detail-grid">
    ${kv("时间", item.timestamp)}
    ${kv("等级", item.severity_label)}
    ${kv("规则", item.signature)}
    ${kv("分类", item.category)}
    ${kv("源", endpoint(item.src_ip, item.src_port))}
    ${kv("目的", endpoint(item.dest_ip, item.dest_port))}
    ${kv("协议", item.proto)}
    ${kv("规则 ID", [item.gid, item.signature_id, item.rev].filter(Boolean).join(":") || "-")}
    ${kv("Flow ID", item.flow_id || "-")}
    ${kv("工单", item.ticket ? `#${item.ticket.id} ${item.ticket.status}` : "-")}
  </div>
  <div class="actions">
    <button id="fpBtn" class="secondary">${item.false_positive ? "取消误报" : "标记误报"}</button>
    <button id="ignoreBtn" class="secondary">${item.ignored ? "取消忽略" : "忽略"}</button>
    <button id="whitelistSrcBtn" class="secondary">白名单源 IP</button>
    <button id="ticketBtn">转工单</button>
    ${item.ticket ? `<button id="closeTicketBtn" class="secondary">关闭工单</button>` : ""}
  </div>
  <h2 style="margin:16px 0 8px">原始事件</h2>
  <pre>${escapeHtml(JSON.stringify(item.raw_event, null, 2))}</pre>`;
  $("#detailDrawer").classList.add("open");
  $("#detailDrawer").setAttribute("aria-hidden", "false");
  $("#fpBtn").onclick = async () => {
    const reason = item.false_positive ? "" : window.prompt("误报原因", "授权环境确认误报") || "";
    await api(`/api/alerts/${item.id}/false-positive`, {
      method: "POST",
      body: JSON.stringify({ false_positive: !item.false_positive, reason }),
    });
    toast("误报状态已更新");
    await refreshAll();
    await openDetail(item.id);
  };
  $("#ignoreBtn").onclick = async () => {
    const reason = item.ignored ? "" : window.prompt("忽略原因", "重复或低价值告警") || "";
    await api(`/api/alerts/${item.id}/ignore`, {
      method: "POST",
      body: JSON.stringify({ ignored: !item.ignored, reason }),
    });
    toast("忽略状态已更新");
    await refreshAll();
    await openDetail(item.id);
  };
  $("#whitelistSrcBtn").onclick = async () => {
    await api("/api/whitelist", {
      method: "POST",
      body: JSON.stringify({ kind: "src_ip", value: item.src_ip, reason: "分析员从告警详情加入" }),
    });
    toast("源 IP 已加入白名单");
    await loadWhitelist();
  };
  $("#ticketBtn").onclick = async () => {
    const note = window.prompt("工单备注", "请跟进该 IDS 告警") || "";
    const ticket = await api(`/api/alerts/${item.id}/ticket`, {
      method: "POST",
      body: JSON.stringify({ note }),
    });
    toast(`已创建工单 #${ticket.id}`);
    await refreshAll();
    await openDetail(item.id);
  };
  const closeTicketBtn = $("#closeTicketBtn");
  if (closeTicketBtn && item.ticket) {
    closeTicketBtn.onclick = async () => {
      const review = window.prompt("复核结论", "已完成复核并关闭") || "";
      await api(`/api/tickets/${item.ticket.id}/status`, {
        method: "POST",
        body: JSON.stringify({ status: "closed", review_conclusion: review }),
      });
      toast("工单已关闭");
      await refreshAll();
      await openDetail(item.id);
    };
  }
}

function kv(label, value) {
  return `<div class="kv"><span>${label}</span>${escapeHtml(value || "-")}</div>`;
}

function endpoint(ip, port) {
  return port ? `${ip}:${port}` : ip;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function toast(message) {
  const el = $("#toast");
  el.textContent = message;
  el.hidden = false;
  setTimeout(() => {
    el.hidden = true;
  }, 2200);
}

$("#filters").addEventListener("submit", async (event) => {
  event.preventDefault();
  state.filters = Object.fromEntries(new FormData(event.currentTarget).entries());
  state.page = 1;
  await Promise.all([loadAlerts(), loadTimeline()]);
});

$("#alertsBody").addEventListener("click", (event) => {
  const row = event.target.closest("tr[data-alert-id]");
  if (row) openDetail(row.dataset.alertId).catch((error) => toast(error.message));
});

$("#prevPage").addEventListener("click", async () => {
  state.page = Math.max(1, state.page - 1);
  await loadAlerts();
});

$("#nextPage").addEventListener("click", async () => {
  state.page += 1;
  await loadAlerts();
});

$("#closeDrawer").addEventListener("click", () => {
  $("#detailDrawer").classList.remove("open");
  $("#detailDrawer").setAttribute("aria-hidden", "true");
});

$("#refreshBtn").addEventListener("click", () => refreshAll().catch((error) => toast(error.message)));
$("#ipBtn").addEventListener("click", () => loadIpAnalysis().catch((error) => toast(error.message)));
$("#retryBtn").addEventListener("click", async () => {
  const result = await api("/api/import/retry-failed", { method: "POST", body: "{}" });
  toast(`已重试 ${result.retried} 个失败导入`);
  await loadImportLogs();
});

$("#dailyReport").addEventListener("click", () => window.open("/api/reports/daily", "_blank"));
$("#highReport").addEventListener("click", () => window.open("/api/reports/high", "_blank"));
$("#ruleReport").addEventListener("click", () => window.open("/api/reports/rules", "_blank"));

$("#importBtn").addEventListener("click", async () => {
  const path = $("#evePath").value.trim();
  if (!path) {
    toast("请输入 eve.json 路径");
    return;
  }
  const result = await api("/api/import", {
    method: "POST",
    body: JSON.stringify({ path }),
  });
  toast(`导入 ${result.imported_alerts} 条，跳过 ${result.skipped_events} 条`);
  await refreshAll();
});

refreshAll().catch((error) => toast(error.message));
