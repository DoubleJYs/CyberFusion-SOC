package com.zhangjiyan.template.system.excel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.ExcelExportUtils;
import com.zhangjiyan.template.common.excel.ExcelImportError;
import com.zhangjiyan.template.common.excel.ExcelImportResult;
import com.zhangjiyan.template.common.excel.ExcelTemplateUtils;
import com.zhangjiyan.template.common.excel.SimpleXlsxUtils;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.excel.vo.SysImportExportLogVO;
import com.zhangjiyan.template.system.file.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysImportExportLogServiceImpl extends ServiceImpl<SysImportExportLogMapper, SysImportExportLog> implements SysImportExportLogService {

    private final AppExcelProperties excelProperties;
    private final SysFileService fileService;

    @Override
    public byte[] template(String templateCode) {
        assertDemoTemplate(templateCode);
        return ExcelTemplateUtils.simpleTemplate("user-demo-import-template",
                List.of("姓名", "手机号", "邮箱"),
                List.of(List.of("演示姓名", "18800000000", "demo@example.local")));
    }

    @Override
    @Transactional
    public ExcelImportResult importExcel(String templateCode, MultipartFile file) {
        assertDemoTemplate(templateCode);
        Long fileId = fileService.upload(file, "excel_import").id();
        ExcelImportResult result = parseDemoImport(file);
        SysImportExportLog log = baseLog("IMPORT", templateCode);
        log.setFileId(fileId);
        log.setTotalCount(result.totalCount());
        log.setSuccessCount(result.successCount());
        log.setFailCount(result.failCount());
        log.setStatus(result.failCount() == 0 ? "SUCCESS" : result.successCount() == 0 ? "FAIL" : "PARTIAL_FAIL");
        log.setErrorSummary(result.errors().stream()
                .limit(10)
                .map(error -> "第" + error.rowNumber() + "行 " + error.fieldName() + ": " + error.reason())
                .reduce((left, right) -> left + "; " + right)
                .orElse(null));
        save(log);
        return result;
    }

    @Override
    public PageResult<SysImportExportLogVO> pageLogs(long pageNum, long pageSize, String keyword, String templateCode, String status) {
        LambdaQueryWrapper<SysImportExportLog> wrapper = queryWrapper(keyword, templateCode, status);
        Page<SysImportExportLog> page = baseMapper.selectPage(Page.of(pageNum <= 0 ? 1 : pageNum, pageSize <= 0 ? 10 : pageSize), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SysImportExportLogVO detail(Long id) {
        SysImportExportLog log = getById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "导入导出日志不存在");
        }
        return toVO(log);
    }

    @Override
    @Transactional
    public byte[] exportLogs(String keyword, String templateCode, String status) {
        List<SysImportExportLog> logs = baseMapper.selectList(queryWrapper(keyword, templateCode, status).last("limit 5000"));
        byte[] bytes = ExcelExportUtils.export("import-export-logs",
                List.of("任务编号", "任务类型", "模板编码", "总数", "成功数", "失败数", "状态", "操作人", "创建时间"),
                logs.stream().map(log -> List.of(
                        text(log.getTaskNo()), text(log.getTaskType()), text(log.getTemplateCode()),
                        text(log.getTotalCount()), text(log.getSuccessCount()), text(log.getFailCount()),
                        text(log.getStatus()), text(log.getOperatorName()), text(log.getCreatedAt())
                )).toList());
        SysImportExportLog exportLog = baseLog("EXPORT", "import-export-log");
        exportLog.setTotalCount(logs.size());
        exportLog.setSuccessCount(logs.size());
        exportLog.setFailCount(0);
        exportLog.setStatus("SUCCESS");
        save(exportLog);
        return bytes;
    }

    private ExcelImportResult parseDemoImport(MultipartFile file) {
        List<ExcelImportError> errors = new ArrayList<>();
        int total = 0;
        try {
            List<List<String>> rows = SimpleXlsxUtils.readWorkbook(file.getInputStream());
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                String name = cell(row, 0);
                String mobile = cell(row, 1);
                String email = cell(row, 2);
                if (name.isBlank() && mobile.isBlank() && email.isBlank()) {
                    continue;
                }
                total++;
                if (total > excelProperties.getMaxImportRows()) {
                    errors.add(new ExcelImportError(rowIndex + 1, "文件", "超过最大导入行数 " + excelProperties.getMaxImportRows()));
                    break;
                }
                if (name.isBlank()) {
                    errors.add(new ExcelImportError(rowIndex + 1, "姓名", "不能为空"));
                }
                if (!mobile.matches("^1\\d{10}$")) {
                    errors.add(new ExcelImportError(rowIndex + 1, "手机号", "必须为 11 位手机号"));
                }
                if (!email.isBlank() && !email.contains("@")) {
                    errors.add(new ExcelImportError(rowIndex + 1, "邮箱", "格式不正确"));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("Excel 文件解析失败");
        }
        return ExcelImportResult.of(total, errors);
    }

    private String cell(List<String> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).trim();
    }

    private LambdaQueryWrapper<SysImportExportLog> queryWrapper(String keyword, String templateCode, String status) {
        return new LambdaQueryWrapper<SysImportExportLog>()
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysImportExportLog::getTaskNo, keyword)
                        .or()
                        .like(SysImportExportLog::getOperatorName, keyword))
                .eq(templateCode != null && !templateCode.isBlank(), SysImportExportLog::getTemplateCode, templateCode)
                .eq(status != null && !status.isBlank(), SysImportExportLog::getStatus, status)
                .orderByDesc(SysImportExportLog::getCreatedAt);
    }

    private SysImportExportLog baseLog(String taskType, String templateCode) {
        SysImportExportLog log = new SysImportExportLog();
        log.setTaskNo("IE" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        log.setTaskType(taskType);
        log.setTemplateCode(templateCode);
        SecurityUtils.currentUser().ifPresent(loginUser -> {
            log.setOperatorId(loginUser.userId());
            log.setOperatorName(loginUser.nickname());
        });
        return log;
    }

    private void assertDemoTemplate(String templateCode) {
        if (!"user-demo-import-template".equals(templateCode)) {
            throw new BusinessException("当前母版仅内置 user-demo-import-template 演示模板");
        }
    }

    private SysImportExportLogVO toVO(SysImportExportLog log) {
        return new SysImportExportLogVO(log.getId(), log.getTaskNo(), log.getTaskType(), log.getTemplateCode(), log.getFileId(),
                log.getTotalCount(), log.getSuccessCount(), log.getFailCount(), log.getStatus(), log.getErrorSummary(),
                log.getOperatorId(), log.getOperatorName(), log.getCreatedAt());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
