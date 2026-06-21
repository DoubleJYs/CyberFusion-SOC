#!/usr/bin/env node
"use strict";

const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { URL } = require("url");

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || "127.0.0.1";
const DEFAULT_MODE = normalizeMode(process.env.DEMO_PORTAL_MODE || "protected");
const DEFAULT_BATCH_ID = cleanId(process.env.DEMO_BATCH_ID || "DEMO-RANGE-ENTERPRISE-LOCAL");
const DEFAULT_LOG_FILE = process.env.DEMO_PORTAL_LOG_FILE || "/runtime/logs/demo-target/demo-events.jsonl";
const ASSET_IP = process.env.DEMO_PORTAL_ASSET_IP || "10.20.1.15";
const SOURCE_IP = "203.0.113.120";

const cases = [
  {
    id: "DEMO-ACCESS-001",
    title: "访问控制风险模拟",
    path: "/case/DEMO-ACCESS-001",
    category: "access-control",
    severity: "high",
    ruleName: "Demo portal access policy review",
    safe: "角色校验通过，仅返回授权业务摘要。",
    vulnerable: "模拟发现越权访问风险，但不返回任何真实敏感数据。",
    protected: "访问控制策略阻断了越权业务视图请求。",
  },
  {
    id: "DEMO-UPLOAD-001",
    title: "上传策略风险模拟",
    path: "/case/DEMO-UPLOAD-001",
    category: "upload-policy",
    severity: "high",
    ruleName: "Demo portal upload policy review",
    safe: "上传策略仅接受业务白名单类型，文件不会落盘。",
    vulnerable: "模拟发现上传策略过宽风险，仍不会保存文件内容。",
    protected: "上传策略阻断了不符合白名单的演示请求。",
  },
  {
    id: "DEMO-INPUT-001",
    title: "输入校验风险模拟",
    path: "/case/DEMO-INPUT-001",
    category: "input-validation",
    severity: "medium",
    ruleName: "Demo portal input validation review",
    safe: "输入经过规范化校验后进入业务审计队列。",
    vulnerable: "模拟发现输入校验缺失风险，不执行也不回显危险内容。",
    protected: "输入校验策略阻断了不符合业务规则的演示请求。",
  },
  {
    id: "DEMO-HEADER-001",
    title: "安全响应头风险模拟",
    path: "/case/DEMO-HEADER-001",
    category: "security-headers",
    severity: "medium",
    ruleName: "Demo portal response header review",
    safe: "响应包含演示环境要求的安全响应头。",
    vulnerable: "模拟发现响应头基线缺口，用于生成审计证据。",
    protected: "响应头基线已被保护模式补齐并记录证据。",
  },
  {
    id: "DEMO-DEPENDENCY-001",
    title: "依赖组件风险模拟",
    path: "/case/DEMO-DEPENDENCY-001",
    category: "dependency-risk",
    severity: "high",
    ruleName: "Demo portal dependency inventory review",
    safe: "组件清单符合演示基线，无需生成高危证据。",
    vulnerable: "模拟发现依赖组件版本风险，仅生成清单证据。",
    protected: "依赖组件策略阻断了风险版本进入发布基线。",
  },
];

const caseById = new Map(cases.map((item) => [item.id, item]));

function normalizeMode(value) {
  return ["safe", "vulnerable", "protected"].includes(value) ? value : "protected";
}

function cleanId(value) {
  const cleaned = String(value || "").replace(/[^A-Za-z0-9_.:-]/g, "").slice(0, 80);
  return cleaned || "DEMO-RANGE-ENTERPRISE-LOCAL";
}

function nowIso() {
  return new Date().toISOString();
}

function evaluateCase(item, mode, reqMeta) {
  const isProtectedBlock = mode === "protected" && item.id !== "DEMO-HEADER-001";
  const httpStatus = isProtectedBlock ? 403 : 200;
  const action = isProtectedBlock ? "block" : mode === "vulnerable" ? "detect" : "allow";
  const eventType = isProtectedBlock
    ? item.id === "DEMO-UPLOAD-001" ? "upload_block" : "waf_block"
    : "waf_detect";
  const evidenceSummary = mode === "safe"
    ? item.safe
    : mode === "vulnerable"
      ? item.vulnerable
      : item.protected;
  const requestId = `${reqMeta.batchId}-${item.id}-${Date.now().toString(36)}`;

  return {
    response: {
      caseId: item.id,
      title: item.title,
      mode,
      status: action,
      message: evidenceSummary,
      dataSource: "demo-enterprise-portal structured log",
      expectedEvidence: {
        sourceType: "waf",
        eventType,
        action,
        ruleId: item.id,
      },
    },
    log: {
      timestamp: nowIso(),
      service: "demo-enterprise-portal",
      sourceType: "demo-enterprise-portal",
      eventType,
      severity: mode === "safe" ? "low" : item.severity,
      assetIp: ASSET_IP,
      targetUrl: reqMeta.targetUrl,
      httpMethod: reqMeta.method,
      httpStatus,
      action,
      ruleId: item.id,
      ruleName: item.ruleName,
      engine: "demo-enterprise-portal",
      requestId,
      demoCaseId: item.id,
      batchId: reqMeta.batchId,
      sourceIp: SOURCE_IP,
      mode,
      evidenceSummary,
    },
    httpStatus,
  };
}

function writeLog(record) {
  const logFile = process.env.DEMO_PORTAL_LOG_FILE || DEFAULT_LOG_FILE;
  const line = `${JSON.stringify(record)}\n`;
  try {
    fs.mkdirSync(path.dirname(logFile), { recursive: true });
    fs.appendFileSync(logFile, line, "utf8");
  } catch (error) {
    process.stdout.write(line);
    process.stderr.write(`demo-enterprise-portal log fallback: ${error.message}\n`);
  }
}

function jsonResponse(res, status, body, headers = {}) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    ...headers,
  });
  res.end(JSON.stringify(body, null, 2));
}

function htmlResponse(res, body) {
  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
  });
  res.end(body);
}

function dashboardHtml() {
  const rows = cases.map((item) => `
    <tr>
      <td>${item.id}</td>
      <td>${item.title}</td>
      <td>${item.category}</td>
      <td>
        <a href="${item.path}?mode=safe">safe</a>
        <a href="${item.path}?mode=vulnerable">vulnerable</a>
        <a href="${item.path}?mode=protected">protected</a>
      </td>
    </tr>`).join("");
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CyberFusion Demo Enterprise Portal</title>
  <style>
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f5f7fb; color: #1f2937; }
    main { max-width: 1040px; margin: 0 auto; padding: 32px 20px; }
    h1 { font-size: 28px; margin: 0 0 12px; }
    p { line-height: 1.65; color: #4b5563; }
    table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #d9e2ef; }
    th, td { padding: 12px; border-bottom: 1px solid #e5e7eb; text-align: left; }
    th { background: #edf2f7; font-weight: 650; }
    a { color: #b45309; margin-right: 12px; }
    code { background: #edf2f7; padding: 2px 5px; border-radius: 4px; }
  </style>
</head>
<body>
  <main>
    <h1>CyberFusion Demo Enterprise Portal</h1>
    <p>隔离演示靶站，仅输出受控业务风险模拟和结构化证据日志。默认模式为 <code>${DEFAULT_MODE}</code>，不会连接外部目标、不会保存上传文件、不会读取真实客户数据。</p>
    <table>
      <thead><tr><th>用例</th><th>场景</th><th>类别</th><th>模式入口</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
  </main>
</body>
</html>`;
}

function handleRequest(req, res) {
  const url = new URL(req.url || "/", `http://${req.headers.host || "127.0.0.1"}`);
  const mode = normalizeMode(url.searchParams.get("mode") || DEFAULT_MODE);
  const batchId = cleanId(url.searchParams.get("batchId") || DEFAULT_BATCH_ID);

  if (url.pathname === "/health") {
    jsonResponse(res, 200, { status: "ok", service: "demo-enterprise-portal", mode: DEFAULT_MODE });
    return;
  }

  if (url.pathname === "/cases") {
    jsonResponse(res, 200, { modes: ["safe", "vulnerable", "protected"], cases });
    return;
  }

  if (url.pathname === "/") {
    htmlResponse(res, dashboardHtml());
    return;
  }

  const match = url.pathname.match(/^\/case\/([A-Z0-9-]+)$/);
  if (!match || !caseById.has(match[1])) {
    jsonResponse(res, 404, { error: "not_found", message: "Unknown demo case." });
    return;
  }

  const item = caseById.get(match[1]);
  const result = evaluateCase(item, mode, {
    batchId,
    method: req.method || "GET",
    targetUrl: url.pathname,
  });
  writeLog(result.log);

  const headers = item.id === "DEMO-HEADER-001" && mode === "vulnerable"
    ? { "X-Demo-Header-Finding": "missing-baseline-simulation" }
    : {};
  jsonResponse(res, result.httpStatus, result.response, headers);
}

function runSelfTest() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "demo-enterprise-portal-"));
  const tempLogFile = path.join(tempDir, "demo-events.jsonl");
  process.env.DEMO_PORTAL_LOG_FILE = tempLogFile;
  const originalLogFile = DEFAULT_LOG_FILE;
  const records = [];
  for (const item of cases) {
    for (const mode of ["safe", "vulnerable", "protected"]) {
      const result = evaluateCase(item, mode, {
        batchId: "SELFTEST",
        method: "GET",
        targetUrl: item.path,
      });
      if (!["allow", "detect", "block"].includes(result.log.action)) {
        throw new Error(`unexpected action for ${item.id}/${mode}`);
      }
      writeLog(result.log);
      records.push(result.log);
    }
  }
  const protectedBlocks = records.filter((item) => item.mode === "protected" && item.action === "block").length;
  const logLines = fs.readFileSync(tempLogFile, "utf8").trim().split("\n").filter(Boolean);
  if (records.length !== 15 || protectedBlocks !== 4 || logLines.length !== 15) {
    throw new Error(`unexpected self-test counts records=${records.length} protectedBlocks=${protectedBlocks} logLines=${logLines.length}`);
  }
  fs.rmSync(tempDir, { recursive: true, force: true });
  console.log(JSON.stringify({
    status: "ok",
    cases: cases.length,
    modes: 3,
    generatedRecords: records.length,
    protectedBlocks,
    logFileDefault: originalLogFile,
  }, null, 2));
}

function runHttpSmoke() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "demo-enterprise-portal-http-"));
  const tempLogFile = path.join(tempDir, "demo-events.jsonl");
  process.env.DEMO_PORTAL_LOG_FILE = tempLogFile;
  const server = http.createServer(handleRequest);
  server.listen(0, "127.0.0.1", () => {
    const address = server.address();
    const request = http.get({
      host: "127.0.0.1",
      port: address.port,
      path: "/case/DEMO-ACCESS-001?mode=protected&batchId=SMOKE",
      timeout: 3000,
    }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        try {
          const logLines = fs.readFileSync(tempLogFile, "utf8").trim().split("\n").filter(Boolean);
          if (response.statusCode !== 403 || !body.includes("DEMO-ACCESS-001") || logLines.length !== 1) {
            throw new Error(`unexpected smoke result status=${response.statusCode} logLines=${logLines.length}`);
          }
          console.log(JSON.stringify({
            status: "ok",
            httpStatus: response.statusCode,
            logLines: logLines.length,
            caseId: "DEMO-ACCESS-001",
          }, null, 2));
          fs.rmSync(tempDir, { recursive: true, force: true });
          server.close(() => process.exit(0));
        } catch (error) {
          server.close(() => {
            fs.rmSync(tempDir, { recursive: true, force: true });
            console.error(error.message);
            process.exit(1);
          });
        }
      });
    });
    request.on("error", (error) => {
      server.close(() => {
        fs.rmSync(tempDir, { recursive: true, force: true });
        console.error(error.message);
        process.exit(1);
      });
    });
  });
}

if (process.argv.includes("--self-test")) {
  runSelfTest();
} else if (process.argv.includes("--http-smoke")) {
  runHttpSmoke();
} else {
  const server = http.createServer(handleRequest);
  server.listen(PORT, HOST, () => {
    console.log(JSON.stringify({
      service: "demo-enterprise-portal",
      host: HOST,
      port: PORT,
      mode: DEFAULT_MODE,
      batchId: DEFAULT_BATCH_ID,
      logFile: DEFAULT_LOG_FILE,
    }));
  });
}
