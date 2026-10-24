package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api_service.model.entity.ApiKeyInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApiKeyInfoMapper extends BaseMapper<ApiKeyInfo> {

    /**
     * 累加 Key 使用次数并刷新最后调用时间。
     *
     * @return 受影响行数，0 表示 Key 不存在或已删除
     */
    @Update("""
            UPDATE api_key_info
            SET used_quota = used_quota + 1,
                last_used_time = NOW(),
                update_time = NOW()
            WHERE id = #{apiKeyId}
              AND is_deleted = 0
            """)
    int markUsed(@Param("apiKeyId") Long apiKeyId);
}
