package fun.commons.framework4j.tracelog.unit;

import fun.commons.framework4j.tracelog.switcher.SwitchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SwitchRule Pub/Sub 序列化")
class SwitchRuleTest {

    @Test
    @DisplayName("正常 payload 解析")
    void parsePayload() {
        SwitchRule rule = SwitchRule.fromPayload("{\"type\":\"user\",\"value\":\"10086\",\"level\":\"DEBUG\"}");
        assertThat(rule).isNotNull();
        assertThat(rule.getType()).isEqualTo("user");
        assertThat(rule.getValue()).isEqualTo("10086");
        assertThat(rule.getLevel()).isEqualTo("DEBUG");
    }

    @Test
    @DisplayName("空 / null payload → null")
    void emptyPayload() {
        assertThat(SwitchRule.fromPayload(null)).isNull();
        assertThat(SwitchRule.fromPayload("")).isNull();
        assertThat(SwitchRule.fromPayload("   ")).isNull();
    }

    @Test
    @DisplayName("非法 JSON → null")
    void invalidPayload() {
        assertThat(SwitchRule.fromPayload("not json")).isNull();
        assertThat(SwitchRule.fromPayload("{type:user}")).isNull(); // 缺引号
    }

    @Test
    @DisplayName("缺字段 → null")
    void missingFields() {
        assertThat(SwitchRule.fromPayload("{\"type\":\"user\"}")).isNull();
        assertThat(SwitchRule.fromPayload("{\"value\":\"10086\",\"level\":\"DEBUG\"}")).isNull();
    }

    @Test
    @DisplayName("redisKey 格式")
    void redisKeyFormat() {
        SwitchRule rule = new SwitchRule("user", "10086", "DEBUG");
        assertThat(rule.redisKey()).isEqualTo("log_switch:id:user:10086");
    }

    @Test
    @DisplayName("转义引号")
    void escapeQuotes() {
        SwitchRule rule = new SwitchRule("user", "name\"with\"quote", "DEBUG");
        String payload = rule.pubSubPayload();
        assertThat(payload).contains("\\\"");
        // 能正确反序列化
        SwitchRule parsed = SwitchRule.fromPayload(payload);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getValue()).isEqualTo("name\"with\"quote");
    }
}