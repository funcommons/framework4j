package fun.commons.framework4j.id.generator;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus ID 生成器
 * <p>
 * 将 SDK 的雪花算法集成到 MyBatis-Plus 框架
 *
 * @since 1.0.0
 */
@Slf4j
public class MpIdGenerator implements IdentifierGenerator {

    private final SnowflakeDistributor snowflake;

    public MpIdGenerator(SnowflakeDistributor snowflake) {
        this.snowflake = snowflake;
        log.info("[ID-SDK] MpIdGenerator initialized with WorkerID: {}", snowflake.getSdkWorkerId());
    }

    @Override
    public Number nextId(Object entity) {
        return snowflake.nextId();
    }

    /**
     * 获取下一个 Long 类型 ID
     *
     * @return 雪花算法生成的 ID
     */
    public long nextLongId() {
        return snowflake.nextId();
    }

    /**
     * 获取下一个 String 类型 ID
     *
     * @return 雪花算法生成的 ID 字符串
     */
    public String nextStringId() {
        return String.valueOf(snowflake.nextId());
    }
}
