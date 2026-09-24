package local.train.blockwindow;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 封锁窗口许可服务 —— 仅供本地模拟演练使用。
 *
 * <p>本服务不连接、不驱动任何真实铁路信号/行车/供电设备，所有“调度回执”均为模拟文本。
 */
@SpringBootApplication
public class BlockWindowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlockWindowApplication.class, args);
    }

    /** 系统时钟：测试中可注入固定/可调时钟，保证跨午夜等场景可重复演练。 */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI trainingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("封锁窗口许可服务（本地模拟演练）")
                        .version("v1")
                        .description("""
                                仅供本地培训演练的封锁窗口许可 API：计划编排、人员资质、区段保护、开工与销记。
                                **安全声明**：本服务不连接、不控制任何真实铁路设备，调度回执全部为模拟数据，
                                禁止用于真实行车组织。
                                """)
                        .license(new License().name("Simulation-only, for training"))
                        .contact(new Contact().name("信号检修演练环境")));
    }
}
