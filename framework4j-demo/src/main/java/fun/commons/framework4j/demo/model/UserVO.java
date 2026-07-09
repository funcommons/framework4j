package fun.commons.framework4j.demo.model;

import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demo 用户 VO（含脱敏字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private String id;

    @Sensitive(SensitiveRule.PHONE)
    private String phone;

    @Sensitive(SensitiveRule.EMAIL)
    private String email;

    @Sensitive(SensitiveRule.NAME)
    private String realName;
}
