package fun.commons.framework4j.datetime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 时间处理模块配置属性（v1.10，issue #30-②）
 *
 * @since 1.10.0
 */
@Data
@ConfigurationProperties(prefix = "framework4j.datetime")
public class DateTimeProperties {

    /**
     * 本地格式分支（yyyy-MM-dd HH:mm:ss，@LocalTimeFormat 注解态）换算的目标时区。
     * <p>
     * v1.10 (issue #30-②)：此前本地格式分支直接按值自带 offset 排墙钟——UTC offset 值
     * （JVM TZ=UTC 容器常见）会输出 UTC 墙钟，与 javadoc 宣称的 GMT+8 错 8 小时。
     * 现格式化前统一 {@code atZoneSameInstant(localZone)} 换算。
     * <p>
     * 非法时区值启动即失败（fail-fast，与仓库 AccessTokenConfigValidator 风格一致）。
     */
    private String localZone = "Asia/Shanghai";
}
