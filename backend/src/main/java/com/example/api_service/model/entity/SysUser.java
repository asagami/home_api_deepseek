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
 * 开发者用户表 sys_user。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    /** USER-普通开发者，ADMIN-管理员 */
    private String role;

    /** 剩余调用总额度（次） */
    private Long balanceQuota;

    /** 1-正常，0-封禁 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
