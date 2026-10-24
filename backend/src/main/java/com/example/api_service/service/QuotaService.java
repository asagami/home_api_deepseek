package com.example.api_service.service;

public interface QuotaService {

    /**
     * 一次成功调用后的计量：按实际点数扣减用户额度、累加 Key 使用次数、写入调用日志。
     * 三步在同一事务中完成。
     *
     * @param userId       调用者用户 ID
     * @param apiKeyId     使用的 Key ID
     * @param endpointPath 接口路径
     * @param httpStatus   响应状态码
     * @param costPoints   本次调用实际扣减点数（按专属折扣计算）
     * @param latencyMs    处理耗时（毫秒）
     * @param clientIp     客户端 IP
     */
    void consume(Long userId, Long apiKeyId, String endpointPath,
                 int httpStatus, int costPoints, long latencyMs, String clientIp);
}
