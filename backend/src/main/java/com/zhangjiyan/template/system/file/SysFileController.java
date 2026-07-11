package com.zhangjiyan.template.system.file;

import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.file.FileTypeUtils;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.system.file.dto.AttachmentCreateRequest;
import com.zhangjiyan.template.system.file.vo.SysAttachmentVO;
import com.zhangjiyan.template.system.file.vo.SysFileTablePreview;
import com.zhangjiyan.template.system.file.vo.SysFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "File", description = "文件与附件管理")
@RestController
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping("/system/files/upload")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:upload')")
    public ApiResult<SysFileVO> upload(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String bizType) {
        return ApiResult.ok(fileService.upload(file, bizType));
    }

    @Operation(summary = "文件分页查询")
    @GetMapping("/system/files")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:list')")
    public ApiResult<PageResult<SysFileVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String bizType) {
        return ApiResult.ok(fileService.pageFiles(pageNum, pageSize, keyword, bizType));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/system/files/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:list')")
    public ApiResult<SysFileVO> detail(@PathVariable Long id) {
        return ApiResult.ok(fileService.detail(id));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/system/files/{id}/download")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:download')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysFile file = fileService.fileEntity(id);
        Resource resource = fileService.fileResource(id);
        return fileResponse(file, resource, false);
    }

    @Operation(summary = "图片和 PDF 预览")
    @GetMapping("/system/files/{id}/preview")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:download')")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        SysFile file = fileService.fileEntity(id);
        if (!FileTypeUtils.isImage(file.getFileExt(), file.getContentType()) && !isPdf(file)) {
            throw new BusinessException("该文件暂不支持流式预览，请下载查看");
        }
        return fileResponse(file, fileService.fileResource(id), true);
    }

    @Operation(summary = "Excel 表格预览")
    @GetMapping("/system/files/{id}/table-preview")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:download')")
    public ApiResult<SysFileTablePreview> tablePreview(@PathVariable Long id) {
        return ApiResult.ok(fileService.tablePreview(id));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/system/files/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ApiResult.ok();
    }

    @Operation(summary = "新增附件关联")
    @PostMapping("/system/attachments")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:upload')")
    public ApiResult<SysAttachmentVO> createAttachment(@Valid @RequestBody AttachmentCreateRequest request) {
        return ApiResult.ok(fileService.createAttachment(request));
    }

    @Operation(summary = "查询附件关联")
    @GetMapping("/system/attachments")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:list')")
    public ApiResult<List<SysAttachmentVO>> attachments(@RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String bizId) {
        return ApiResult.ok(fileService.listAttachments(bizType, bizId));
    }

    @Operation(summary = "删除附件关联")
    @DeleteMapping("/system/attachments/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:file:delete')")
    public ApiResult<Void> deleteAttachment(@PathVariable Long id) {
        fileService.deleteAttachment(id);
        return ApiResult.ok();
    }

    private ResponseEntity<Resource> fileResponse(SysFile file, Resource resource, boolean inline) {
        MediaType mediaType = mediaType(file);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private boolean isPdf(SysFile file) {
        return "pdf".equalsIgnoreCase(file.getFileExt())
                || "application/pdf".equalsIgnoreCase(file.getContentType());
    }

    private MediaType mediaType(SysFile file) {
        if (isPdf(file)) {
            return MediaType.APPLICATION_PDF;
        }
        if ("xlsx".equalsIgnoreCase(file.getFileExt())) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(file.getContentType());
    }
}
