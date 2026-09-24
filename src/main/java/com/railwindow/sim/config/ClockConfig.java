package com.railwindow.sim.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 时间来源配置。生产/演练环境使用系统 UTC 时钟；测试中用同类型的可变时钟 Bean 覆盖，
 * 以便模拟跨午夜窗口、迟到消息等时间相关场景。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
