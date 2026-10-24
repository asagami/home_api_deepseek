package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 调用日志流水表 api_access_log。
 */
@Data
@TableName("api_access_log")
public class ApiAccessLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long apiKeyId;

    private String endpointPath;

    private Integer httpStatus;

    /** 本次调用实际扣减点数 */
    private Integer costPoints;

    private Integer latencyMs;

    private String clientIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
