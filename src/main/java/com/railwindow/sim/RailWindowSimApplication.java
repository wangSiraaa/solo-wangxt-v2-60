package com.railwindow.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 信号检修封锁窗口许可服务——<b>仅限本地模拟演练</b>。
 *
 * <p>本服务不连接、不驱动任何真实铁路信号或联锁设备，所有“调度回执”均由本地模拟器产生。
 */
@SpringBootApplication
public class RailWindowSimApplication {
    public static void main(String[] args) {
        SpringApplication.run(RailWindowSimApplication.class, args);
    }
}
