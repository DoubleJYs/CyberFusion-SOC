package com.zhangjiyan.template.system.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.SimpleXlsxUtils;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.file.FileStorageProperties;
import com.zhangjiyan.template.common.file.FileStorageService;
import com.zhangjiyan.template.common.file.StoredFileInfo;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.file.dto.AttachmentCreateRequest;
import com.zhangjiyan.template.system.file.vo.SysAttachmentVO;
import com.zhangjiyan.template.system.file.vo.SysFileTablePreview;
import com.zhangjiyan.template.system.file.vo.SysFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private static final int TABLE_PREVIEW_LIMIT = 200;

    private final FileStorageService storageService;
    private final FileStorageProperties storageProperties;
    private final SysAttachmentMapper attachmentMapper;

    @Override
    @Transactional
    public SysFileVO upload(MultipartFile file, String bizType) {
        StoredFileInfo storedFileInfo = storageService.store(file);
        SysFile sysFile = new SysFile();
        sysFile.setOriginalName(storedFileInfo.originalName());
        sysFile.setStoredName(storedFileInfo.storedName());
        sysFile.setFileExt(storedFileInfo.fileExt());
        sysFile.setContentType(storedFileInfo.contentType());
        sysFile.setFileSize(storedFileInfo.fileSize());
        sysFile.setStorageType(storedFileInfo.storageType());
        sysFile.setStoragePath(storedFileInfo.storagePath());
        sysFile.setMd5(storedFileInfo.md5());
        sysFile.setBizType(bizType);
        SecurityUtils.currentUser().ifPresent(loginUser -> {
            sysFile.setUploaderId(loginUser.userId());
            sysFile.setUploaderName(loginUser.nickname());
        });
        save(sysFile);
        sysFile.setAccessUrl("/api/system/files/" + sysFile.getId() + "/download");
        updateById(sysFile);
        return toVO(sysFile);
    }

    @Override
    public PageResult<SysFileVO> pageFiles(long pageNum, long pageSize, String keyword, String bizType) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getDeleted, 0)
                .eq(bizType != null && !bizType.isBlank(), SysFile::getBizType, bizType)
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysFile::getOriginalName, keyword)
                        .or()
                        .like(SysFile::getMd5, keyword))
                .orderByDesc(SysFile::getCreatedAt);
        Page<SysFile> page = baseMapper.selectPage(Page.of(pageNum <= 0 ? 1 : pageNum, pageSize <= 0 ? 10 : pageSize), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SysFileVO detail(Long id) {
        return toVO(fileEntity(id));
    }

    @Override
    public Resource fileResource(Long id) {
        SysFile file = fileEntity(id);
        return storageService.loadAsResource(file.getStoragePath());
    }

    @Override
    public SysFileTablePreview tablePreview(Long id) {
        SysFile file = fileEntity(id);
        String ext = file.getFileExt() == null ? "" : file.getFileExt().toLowerCase();
        if (!"xlsx".equals(ext)) {
            throw new BusinessException("仅支持 xlsx 文件预览，请下载该文件查看完整内容");
        }
        try (var inputStream = fileResource(id).getInputStream()) {
            List<List<String>> workbookRows = SimpleXlsxUtils.readWorkbook(inputStream);
            List<String> headers = workbookRows.isEmpty() ? List.of() : workbookRows.get(0);
            List<List<String>> bodyRows = workbookRows.stream()
                    .skip(1)
                    .limit(TABLE_PREVIEW_LIMIT)
                    .toList();
            int totalRows = Math.max(workbookRows.size() - 1, 0);
            return new SysFileTablePreview(
                    file.getId(),
                    file.getOriginalName(),
                    ext,
                    headers,
                    bodyRows,
                    totalRows,
                    totalRows > TABLE_PREVIEW_LIMIT
            );
        } catch (IOException ex) {
            throw new BusinessException("Excel 文件预览失败");
        }
    }

    @Override
    public SysFile fileEntity(Long id) {
        SysFile file = getById(id);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    @Override
    @Transactional
    public void deleteFile(Long id) {
        SysFile file = fileEntity(id);
        file.setDeleted(1);
        updateById(file);
        if (Boolean.TRUE.equals(storageProperties.getDeletePhysicalOnDelete())) {
            storageService.delete(file.getStoragePath());
        }
    }

    @Override
    @Transactional
    public SysAttachmentVO createAttachment(AttachmentCreateRequest request) {
        SysFile file = fileEntity(request.fileId());
        SysAttachment attachment = new SysAttachment();
        attachment.setBizType(request.bizType());
        attachment.setBizId(request.bizId());
        attachment.setFileId(file.getId());
        attachment.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        attachment.setRemark(request.remark());
        attachmentMapper.insert(attachment);
        return toAttachmentVO(attachment, file);
    }

    @Override
    public List<SysAttachmentVO> listAttachments(String bizType, String bizId) {
        LambdaQueryWrapper<SysAttachment> wrapper = new LambdaQueryWrapper<SysAttachment>()
                .eq(bizType != null && !bizType.isBlank(), SysAttachment::getBizType, bizType)
                .eq(bizId != null && !bizId.isBlank(), SysAttachment::getBizId, bizId)
                .orderByAsc(SysAttachment::getSortOrder)
                .orderByDesc(SysAttachment::getCreatedAt);
        return attachmentMapper.selectList(wrapper).stream()
                .map(attachment -> toAttachmentVO(attachment, getById(attachment.getFileId())))
                .toList();
    }

    @Override
    public void deleteAttachment(Long id) {
        attachmentMapper.deleteById(id);
    }

    private SysFileVO toVO(SysFile file) {
        return new SysFileVO(file.getId(), file.getOriginalName(), file.getFileExt(), file.getContentType(), file.getFileSize(),
                file.getStorageType(), file.getAccessUrl(), file.getMd5(), file.getBizType(), file.getUploaderId(),
                file.getUploaderName(), "/api/system/files/" + file.getId() + "/download",
                "/api/system/files/" + file.getId() + "/preview", file.getCreatedAt());
    }

    private SysAttachmentVO toAttachmentVO(SysAttachment attachment, SysFile file) {
        return new SysAttachmentVO(attachment.getId(), attachment.getBizType(), attachment.getBizId(), attachment.getFileId(),
                file == null ? null : file.getOriginalName(), file == null ? null : file.getContentType(), file == null ? null : file.getFileSize(),
                file == null ? null : file.getUploaderName(), file == null ? null : "/api/system/files/" + file.getId() + "/download",
                file == null ? null : "/api/system/files/" + file.getId() + "/preview", attachment.getSortOrder(), attachment.getRemark(),
                attachment.getCreatedAt());
    }
}
