package com.ai.config;

import com.ai.config.ConditionalOnModule;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.ai.log.LogStreamAppender;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 把实时日志 appender 挂到 Logback 根 logger 上；
 * OpsLogStreamService 负责提供 sink，本类只负责挂载/卸载。
 */
@ConditionalOnModule("ops")
@Configuration
public class OpsLogStreamConfig {

    private LogStreamAppender appender;

    public OpsLogStreamConfig() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            appender = new LogStreamAppender();
            appender.setContext(context);
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        } catch (Exception ignored) {
            // 挂载失败只影响实时日志面板，不影响业务
            appender = null;
        }
    }

    @PreDestroy
    public void detach() {
        try {
            if (appender != null) {
                LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
                context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
                appender.stop();
            }
        } catch (Exception ignored) {
            // 忽略卸载异常
        }
    }
}
