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
 * API 密钥表 api_key_info。仅保存 SHA-256 哈希与脱敏前缀，不保存明文。
 */
@Data
@TableName("api_key_info")
public class ApiKeyInfo {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String keyName;

    /** 脱敏展示前缀，如 sk-AbCdEfgh...8f9a */
    private String keyPrefix;

    /** API Key 明文的 SHA-256 十六进制摘要 */
    private String keyHash;

    /** 每秒最大请求数 */
    private Integer rateLimitQps;

    /** 该 Key 总额度，-1 表示共享账户额度 */
    private Long totalQuota;

    private Long usedQuota;

    private LocalDateTime expiredTime;

    private LocalDateTime lastUsedTime;

    /** 1-启用，0-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
