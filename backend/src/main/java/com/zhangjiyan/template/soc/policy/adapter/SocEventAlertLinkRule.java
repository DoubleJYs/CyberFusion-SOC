package com.zhangjiyan.template.soc.policy.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("soc_event_alert_link_rule")
public class SocEventAlertLinkRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adapterId;
    private String eventType;
    private String minSeverity;
    private Integer linkAlertsDefault;
    private String alertRuleIdField;
    private String alertNameTemplate;
    private String dedupKeyFieldsJson;
    private Integer enabled;
}
