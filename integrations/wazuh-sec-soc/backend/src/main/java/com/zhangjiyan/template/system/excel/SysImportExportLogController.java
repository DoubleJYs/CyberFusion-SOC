package com.zhangjiyan.template.system.excel;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.ExcelImportResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.system.excel.vo.SysImportExportLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "Excel", description = "Excel 导入导出")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/excel")
public class SysImportExportLogController {

    private final SysImportExportLogService excelService;

    @Operation(summary = "下载 Excel 模板")
    @GetMapping("/templates/{templateCode}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:excel:template')")
    public ResponseEntity<byte[]> template(@PathVariable String templateCode) {
        return excelResponse(templateCode + ".xlsx", excelService.template(templateCode));
    }

    @Operation(summary = "导入 Excel")
    @PostMapping("/imports/{templateCode}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:excel:import')")
    public ApiResult<ExcelImportResult> importExcel(@PathVariable String templateCode, @RequestPart("file") MultipartFile file) {
        return ApiResult.ok(excelService.importExcel(templateCode, file));
    }

    @Operation(summary = "导入导出日志分页")
    @GetMapping("/logs")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:excel:log')")
    public ApiResult<PageResult<SysImportExportLogVO>> logs(@RequestParam(defaultValue = "1") long pageNum,
                                                            @RequestParam(defaultValue = "10") long pageSize,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String templateCode,
                                                            @RequestParam(required = false) String status) {
        return ApiResult.ok(excelService.pageLogs(pageNum, pageSize, keyword, templateCode, status));
    }

    @Operation(summary = "导入导出日志详情")
    @GetMapping("/logs/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:excel:log')")
    public ApiResult<SysImportExportLogVO> detail(@PathVariable Long id) {
        return ApiResult.ok(excelService.detail(id));
    }

    @Operation(summary = "导出导入导出日志")
    @GetMapping("/logs/export")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:excel:export')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String templateCode,
                                         @RequestParam(required = false) String status) {
        return excelResponse("import-export-logs.xlsx", excelService.exportLogs(keyword, templateCode, status));
    }

    private ResponseEntity<byte[]> excelResponse(String filename, byte[] bytes) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }
}
