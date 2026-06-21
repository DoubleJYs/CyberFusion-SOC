const state = {
  targets: [],
  tasks: [],
  activeTaskId: null,
  activeTask: null,
};

const riskOrder = ["Critical", "High", "Medium", "Low", "Informational"];
const riskLabel = {
  Critical: "严重",
  High: "高危",
  Medium: "中危",
  Low: "低危",
  Informational: "信息",
};

const $ = (selector) => document.querySelector(selector);

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.error?.message || `HTTP ${response.status}`);
  }
  return payload;
}

function formData(form) {
  const data = Object.fromEntries(new FormData(form).entries());
  for (const checkbox of form.querySelectorAll('input[type="checkbox"]')) {
    data[checkbox.name] = checkbox.checked;
  }
  return data;
}

function toast(message) {
  const node = $("#toast");
  node.textContent = message;
  node.classList.add("show");
  clearTimeout(node._timer);
  node._timer = setTimeout(() => node.classList.remove("show"), 2600);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderTargets() {
  $("#targetCount").textContent = state.targets.length;
  const select = $('#taskForm select[name="target_id"]');
  select.innerHTML = state.targets
    .map((target) => `<option value="${target.id}">${escapeHtml(target.name)}</option>`)
    .join("");
  $("#targets").innerHTML = state.targets
    .map(
      (target) => `
        <article class="target-card">
          <strong>${escapeHtml(target.name)}</strong>
          <p>${escapeHtml(target.base_url)}</p>
          <p>范围：${escapeHtml(target.scope_prefix)}</p>
          <p>白名单：${escapeHtml((target.allowlist || []).join(", ") || "默认范围")}</p>
          <p>黑名单：${escapeHtml((target.blocklist || []).join(", ") || "无")}</p>
          <p>负责人：${escapeHtml(target.owner)} · 有效期：${escapeHtml(target.valid_until)} · 状态：${escapeHtml(target.status)}</p>
        </article>
      `,
    )
    .join("");
}

function renderTasks() {
  $("#tasks").innerHTML = state.tasks
    .map((task) => {
      const counts = riskOrder
        .map((risk) => `${riskLabel[risk]} ${task.risk_counts?.[risk] ?? 0}`)
        .join(" / ");
      return `
        <article class="task-card ${task.id === state.activeTaskId ? "active" : ""}">
          <div>
            <strong>${escapeHtml(task.name)}</strong>
            <p>${escapeHtml(task.target_name)} · ${escapeHtml(task.status)} · ${counts}</p>
            <p>${escapeHtml(task.created_at)}</p>
          </div>
          <button type="button" class="secondary" data-task="${task.id}">查看</button>
        </article>
      `;
    })
    .join("");
}

function renderTaskDetail() {
  const task = state.activeTask;
  $("#exportBtn").disabled = !task;
  $("#retestTaskBtn").disabled = !task;
  $("#ciGateBtn").disabled = !task;
  if (!task) {
    $("#activeTask").textContent = "未选择任务";
    $("#riskStrip").innerHTML = "";
    $("#taskMeta").innerHTML = "";
    $("#findings").innerHTML = "";
    return;
  }

  $("#activeTask").textContent = `${task.name} · ${task.target_name} · ${task.target_url}`;
  $("#riskStrip").innerHTML = riskOrder
    .map(
      (risk) => `
        <div class="risk-box">
          <span>${riskLabel[risk]}</span>
          <strong>${task.risk_counts?.[risk] ?? 0}</strong>
        </div>
      `,
    )
    .join("");
  const comparison = task.comparison
    ? `差异：新增 ${task.comparison.new_count} / 已修复 ${task.comparison.fixed_count} / 遗留 ${task.comparison.carried_count}`
    : "差异：未生成";
  const gate = task.ci_gate ? `CI：${task.ci_gate.status} · ${task.ci_gate.message}` : "CI：未运行";
  const logs = (task.logs || []).slice(-3).map((log) => `${log.level}: ${log.message}`).join(" | ");
  $("#taskMeta").innerHTML = `
    <div class="meta-line">类型：${escapeHtml(task.scan_type)} · 策略：${escapeHtml(task.policy)} · 状态：${escapeHtml(task.status)} · 超时：${escapeHtml(task.timeout_seconds)}s · 速率：${escapeHtml(task.rate_limit_per_min)}/min</div>
    <div class="meta-line">${escapeHtml(comparison)}</div>
    <div class="meta-line">${escapeHtml(gate)}</div>
    <div class="meta-line">日志：${escapeHtml(logs || "暂无")}</div>
  `;

  $("#findings").innerHTML = task.vulnerabilities
    .map(
      (vuln) => `
        <article class="finding-card">
          <div>
            <span class="risk ${vuln.risk}">${riskLabel[vuln.risk]}</span>
          </div>
          <div class="finding-main">
            <h3>${escapeHtml(vuln.title)}</h3>
            <div class="kv"><span>URL</span><span>${escapeHtml(vuln.url)}</span></div>
            <div class="kv"><span>参数</span><span>${escapeHtml(vuln.parameter)}</span></div>
            <div class="kv"><span>CWE</span><span>${escapeHtml(vuln.cwe || "")}</span></div>
            <div class="kv"><span>WASC</span><span>${escapeHtml(vuln.wasc || "")}</span></div>
            <div class="kv"><span>证据</span><span>${escapeHtml(vuln.evidence)}</span></div>
            <div class="kv"><span>建议</span><span>${escapeHtml(vuln.recommendation)}</span></div>
          </div>
          <div class="finding-actions">
            <span class="status ${vuln.status}">${escapeHtml(vuln.status)}</span>
            <button type="button" class="secondary" data-fixed="${vuln.id}" ${!["pending_fix", "retest_failed"].includes(vuln.status) ? "disabled" : ""}>已修复</button>
            <button type="button" data-close="${vuln.id}" ${vuln.status !== "retest_passed" ? "disabled" : ""}>关闭</button>
          </div>
        </article>
      `,
    )
    .join("");
}

async function loadHealth() {
  const health = await api("/api/health");
  $("#envStatus").textContent = `数据：${health.db_path} ｜ 报告：${health.report_dir} ｜ 网络扫描：关闭 ｜ 并发：${health.max_concurrent_scans}`;
}

async function loadAll() {
  const [targets, tasks] = await Promise.all([api("/api/targets"), api("/api/tasks")]);
  state.targets = targets.targets;
  state.tasks = tasks.tasks;
  renderTargets();
  renderTasks();
  if (state.activeTaskId) {
    await selectTask(state.activeTaskId, false);
  }
}

async function selectTask(taskId, rerenderTasks = true) {
  const payload = await api(`/api/tasks/${encodeURIComponent(taskId)}`);
  state.activeTaskId = taskId;
  state.activeTask = payload.task;
  if (rerenderTasks) renderTasks();
  renderTaskDetail();
}

async function addTarget(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = formData(form);
  if (!data.scope_prefix) data.scope_prefix = data.base_url;
  data.allowlist = splitPatterns(data.allowlist);
  data.blocklist = splitPatterns(data.blocklist);
  await api("/api/targets", { method: "POST", body: JSON.stringify(data) });
  form.reset();
  toast("授权目标已添加");
  await loadAll();
}

async function createTask(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = formData(form);
  const payload = await api("/api/tasks", { method: "POST", body: JSON.stringify(data) });
  toast("demo 扫描已完成");
  await loadAll();
  await selectTask(payload.task.id);
}

async function retestTask() {
  if (!state.activeTaskId) return;
  const payload = await api(`/api/tasks/${encodeURIComponent(state.activeTaskId)}/retest`, {
    method: "POST",
    body: "{}",
  });
  toast("复测任务已创建");
  await loadAll();
  await selectTask(payload.task.id);
}

async function exportReport() {
  if (!state.activeTaskId) return;
  const payload = await api(`/api/tasks/${encodeURIComponent(state.activeTaskId)}/export`, {
    method: "POST",
    body: JSON.stringify({ report_type: "remediation" }),
  });
  toast(`报告已导出：${payload.report.path}`);
  window.open(payload.report.download_url, "_blank", "noopener");
}

async function runCiGate() {
  if (!state.activeTaskId) return;
  const payload = await api(`/api/tasks/${encodeURIComponent(state.activeTaskId)}/ci-gate`, {
    method: "POST",
    body: JSON.stringify({ threshold: "High" }),
  });
  toast(payload.ci_gate.message);
  await selectTask(state.activeTaskId);
}

async function closeFinding(event) {
  const button = event.target.closest("[data-close]");
  if (!button) return;
  const dialog = $("#closeDialog");
  $('#closeForm input[name="vuln_id"]').value = button.dataset.close;
  $('#closeForm textarea[name="closure_reason"]').value = "";
  dialog.showModal();
}

async function markFixed(event) {
  const button = event.target.closest("[data-fixed]");
  if (!button) return;
  const dialog = $("#fixedDialog");
  $('#fixedForm input[name="vuln_id"]').value = button.dataset.fixed;
  $('#fixedForm textarea[name="note"]').value = "";
  dialog.showModal();
}

async function submitClose(event) {
  event.preventDefault();
  const submitter = event.submitter;
  if (submitter?.value === "cancel") {
    $("#closeDialog").close();
    return;
  }
  const data = formData(event.currentTarget);
  await api(`/api/vulnerabilities/${encodeURIComponent(data.vuln_id)}/close`, {
    method: "POST",
    body: JSON.stringify({ closure_reason: data.closure_reason }),
  });
  $("#closeDialog").close();
  toast("漏洞已关闭");
  await selectTask(state.activeTaskId);
  await loadAll();
}

async function submitFixed(event) {
  event.preventDefault();
  const submitter = event.submitter;
  if (submitter?.value === "cancel") {
    $("#fixedDialog").close();
    return;
  }
  const data = formData(event.currentTarget);
  await api(`/api/vulnerabilities/${encodeURIComponent(data.vuln_id)}/mark-fixed`, {
    method: "POST",
    body: JSON.stringify({ note: data.note }),
  });
  $("#fixedDialog").close();
  toast("漏洞已标记为已修复待复测");
  await selectTask(state.activeTaskId);
  await loadAll();
}

function splitPatterns(value) {
  return String(value || "")
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function bindEvents() {
  $("#targetForm").addEventListener("submit", (event) => addTarget(event).catch(showError));
  $("#taskForm").addEventListener("submit", (event) => createTask(event).catch(showError));
  $("#refreshBtn").addEventListener("click", () => loadAll().catch(showError));
  $("#tasks").addEventListener("click", (event) => {
    const button = event.target.closest("[data-task]");
    if (button) selectTask(button.dataset.task).catch(showError);
  });
  $("#findings").addEventListener("click", (event) => {
    closeFinding(event).catch(showError);
    markFixed(event).catch(showError);
  });
  $("#closeForm").addEventListener("submit", (event) => submitClose(event).catch(showError));
  $("#fixedForm").addEventListener("submit", (event) => submitFixed(event).catch(showError));
  $("#retestTaskBtn").addEventListener("click", () => retestTask().catch(showError));
  $("#ciGateBtn").addEventListener("click", () => runCiGate().catch(showError));
  $("#exportBtn").addEventListener("click", () => exportReport().catch(showError));
}

function showError(error) {
  console.error(error);
  toast(error.message || "操作失败");
}

bindEvents();
loadHealth().catch(showError);
loadAll().catch(showError);
