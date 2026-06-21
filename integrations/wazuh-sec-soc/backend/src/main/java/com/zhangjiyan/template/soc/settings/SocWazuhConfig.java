package com.zhangjiyan.template.soc.settings;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_wazuh_config")
public class SocWazuhConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private String managerUrl;
    private String indexerUrl;
    private String dashboardUrl;
    private String authMode;
    private Integer enabled;
    private LocalDateTime lastCheckedAt;
    private String lastStatus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
