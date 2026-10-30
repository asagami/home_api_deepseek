package com.example.api_service.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 密码重置邮件的异步投递器。
 *
 * <p>找回密码接口必须在「账号存在」与「账号不存在」两条路径上保持相近的响应时间，
 * 否则攻击者可测量耗时差异枚举账号。SMTP 发送（数十到数百毫秒）因此移出请求线程，
 * 统一提交到独立的有界线程池；队列饱和时退回调用线程执行，避免静默丢信。</p>
 */
@Slf4j
@Component
public class ResetMailDispatcher {

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 200;
    private static final String THREAD_NAME_PREFIX = "reset-mail-";

    private final EmailService emailService;
    private final ThreadPoolTaskExecutor executor;

    public ResetMailDispatcher(EmailService emailService) {
        this.emailService = emailService;
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(CORE_POOL_SIZE);
        pool.setMaxPoolSize(MAX_POOL_SIZE);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix(THREAD_NAME_PREFIX);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        pool.initialize();
        this.executor = pool;
    }

    /**
     * 异步发送重置邮件。提交或发送失败只记日志，调用方统一返回成功响应，避免响应差异泄露账号是否存在。
     */
    public void dispatch(Long userId, String email, String resetUrl, long validMinutes) {
        try {
            executor.execute(() -> {
                try {
                    emailService.sendPasswordResetEmail(email, resetUrl, validMinutes);
                } catch (RuntimeException ex) {
                    log.error("重置密码邮件发送失败: userId={}", userId, ex);
                }
            });
        } catch (RuntimeException ex) {
            log.error("提交重置密码邮件任务失败: userId={}", userId, ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
