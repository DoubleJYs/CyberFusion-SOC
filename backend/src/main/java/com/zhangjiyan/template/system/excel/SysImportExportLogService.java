package com.zhangjiyan.template.system.excel;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.excel.ExcelImportResult;
import com.zhangjiyan.template.system.excel.vo.SysImportExportLogVO;
import org.springframework.web.multipart.MultipartFile;

public interface SysImportExportLogService extends IService<SysImportExportLog> {
    byte[] template(String templateCode);

    ExcelImportResult importExcel(String templateCode, MultipartFile file);

    PageResult<SysImportExportLogVO> pageLogs(long pageNum, long pageSize, String keyword, String templateCode, String status);

    SysImportExportLogVO detail(Long id);

    byte[] exportLogs(String keyword, String templateCode, String status);
}
