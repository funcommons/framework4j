package fun.commons.framework4j.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * framework4j 全模块 Demo
 * <p>
 * 启动后可用 curl 测试：
 * <ul>
 *   <li>无签名 → 401 + code 10101</li>
 *   <li>正确签名 → 200 + 业务返回（脱敏后）</li>
 *   <li>超限流 → 429 + code 10500 + Retry-After</li>
 * </ul>
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
