package com.zhangjiyan.template.system.file;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.system.file.dto.AttachmentCreateRequest;
import com.zhangjiyan.template.system.file.vo.SysAttachmentVO;
import com.zhangjiyan.template.system.file.vo.SysFileVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysFileService extends IService<SysFile> {
    SysFileVO upload(MultipartFile file, String bizType);

    PageResult<SysFileVO> pageFiles(long pageNum, long pageSize, String keyword, String bizType);

    SysFileVO detail(Long id);

    Resource fileResource(Long id);

    SysFile fileEntity(Long id);

    void deleteFile(Long id);

    SysAttachmentVO createAttachment(AttachmentCreateRequest request);

    List<SysAttachmentVO> listAttachments(String bizType, String bizId);

    void deleteAttachment(Long id);
}
