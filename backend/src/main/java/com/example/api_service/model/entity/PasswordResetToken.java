package com.example.api_service.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码重置令牌表 password_reset_token。
 *
 * <p>只保存令牌的 SHA-256 哈希，明文令牌仅出现在邮件链接中；一次性使用，
 * 校验通过即写入 used_at，重置成功后该用户全部未使用令牌一并作废。</p>
 */
@Data
@TableName("password_reset_token")
public class PasswordResetToken {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** SHA-256(令牌) 的十六进制，唯一索引 */
    private String tokenHash;

    private LocalDateTime expiresAt;

    /** 校验换票时写入，非空即代表已使用 */
    private LocalDateTime usedAt;

    private String requestIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
