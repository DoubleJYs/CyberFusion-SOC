package com.zhangjiyan.template.soc.keeper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("soc_client_checkup_item")
public class SocClientCheckupItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long checkupId;
    private String itemType;
    private String itemName;
    private String severity;
    private Integer itemCount;
    private String summary;
    private String recommendation;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
