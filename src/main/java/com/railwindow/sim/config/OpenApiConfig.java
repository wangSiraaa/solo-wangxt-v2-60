package com.railwindow.sim.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI railWindowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("信号检修封锁窗口许可服务（本地模拟演练）")
                        .version("v1")
                        .description("""
                                仅供本地模拟演练的封锁窗口许可服务，**不连接、不控制任何真实铁路设备**。

                                业务规则要点：
                                * 一个计划可占用多个相邻区段；
                                * 窗口重叠不必然冲突——必须按“互斥作业矩阵”逐对判断，禁止只比设备编号；
                                * 开工前须同时满足：窗口已开放、各区段保护到位、人员到岗且资质覆盖到预计结束时刻；
                                * 资质在预计结束前失效的计划必须重新安排；
                                * 销记前逐项确认复核与人员撤离；计划销记后迟到的开工消息一律拒绝。
                                """)
                        .license(new License().name("Simulation-only, no operational use"))
                        .contact(new Contact().name("Signal Maintenance Planner (simulation)")));
    }
}
