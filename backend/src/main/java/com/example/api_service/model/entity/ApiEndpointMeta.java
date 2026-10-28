package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外 API 服务目录表 api_endpoint_meta。
 */
@Data
@TableName("api_endpoint_meta")
public class ApiEndpointMeta {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    /** 接口路径，如 /api/v1/tools/ip-locate */
    private String path;

    /** GET / POST */
    private String method;

    /** 引擎类型: INTERNAL_SQL, EXTERNAL_SQL, ES, HTTP_PROXY */
    private String engineType;

    /** 单次调用扣减额度 */
    private Integer costPerCall;

    private String description;

    /** 请求参数示例或 JSON Schema */
    private String requestSchema;

    /** 返回结果示例 */
    private String responseSchema;

    /** 引擎专属配置(JSON) */
    private String engineConfig;

    /** 1-上线，0-维护中 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
