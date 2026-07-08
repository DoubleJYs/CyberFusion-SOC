package com.zhangjiyan.template.soc.report;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.soc.SocOperationService;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Tag(name = "SOC 报表中心", description = "日报、周报、月报生成与导出")
@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/reports")
public class SocReportController {

    private final SocOperationService service;

    @Operation(summary = "分页查询报表")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:report:view')")
    public ApiResult<PageResult<SocReport>> list(@Valid SocPageRequest request) {
        return ApiResult.ok(service.reports(request));
    }

    @Operation(summary = "生成报表")
    @PostMapping("/generate")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:report:generate')")
    public ApiResult<SocReport> generate(@Valid @RequestBody ReportGenerateRequest request) {
        return ApiResult.ok(service.generateReport(request));
    }

    @Operation(summary = "导出报表")
    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:report:export')")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(defaultValue = "attachment") String disposition) {
        ReportExportPreview preview = service.reportExportPreview(id, format);
        byte[] bytes = service.exportReport(id, format);
        boolean inline = "inline".equalsIgnoreCase(disposition);
        MediaType mediaType = "pdf".equalsIgnoreCase(preview.format())
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ContentDisposition contentDisposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(preview.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(bytes);
    }

    @Operation(summary = "预览报表导出内容")
    @GetMapping("/{id}/preview")
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:report:view')")
    public ApiResult<ReportExportPreview> preview(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "xlsx") String format) {
        return ApiResult.ok(service.reportExportPreview(id, format));
    }
}
