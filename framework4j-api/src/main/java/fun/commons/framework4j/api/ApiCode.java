package fun.commons.framework4j.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * API 错误码枚举
 * <p>
 * 段位划分（对齐 mc-api-spec v1.6 §7 / v1.7 §7.9/§7.10/§7.11）：
 * <ul>
 *   <li>0        成功</li>
 *   <li>10xxx    系统与基础设施（v1.6 §7.4：10001-10005）</li>
 *   <li>101xx    请求与参数校验（v1.6 §7.5）</li>
 *   <li>102xx    认证（v1.6 §7.6）</li>
 *   <li>103xx    权限（v1.6 §7.7）</li>
 *   <li>104xx    资源（v1.6 §7.8）</li>
 *   <li>105xx    流量控制 + 文件上传（v1.7 §7.9：10500-10506，文件上传从原 106xx 整合）</li>
 *   <li>106xx    业务自定义（v1.7 §7.10：业务线按需登记认领，本枚举不预定义）</li>
 *   <li>10700    部分成功（v1.6 §7.11）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ApiCode {

    // --- 0: 成功 ---
    SUCCESS(0, "操作成功"),

    // --- 10xxx: 系统与基础设施类 (v1.6 §7.4) ---
    SYSTEM_BUSY(10001, "系统繁忙，请稍后再试"),
    SERVICE_MAINTENANCE(10002, "服务暂停维护"),
    SERVICE_TIMEOUT(10003, "服务调用超时"),
    THIRD_PARTY_ERROR(10004, "第三方服务异常"),
    MIDDLEWARE_ERROR(10005, "中间件服务异常"),

    // --- 101xx: 请求与参数校验类 (v1.6 §7.5) ---
    PARAM_ERROR(10100, "请求参数错误"),
    PARAM_MISSING(10101, "必填参数缺失"),
    PARAM_FORMAT_ERROR(10102, "参数格式错误"),
    BODY_FORMAT_ERROR(10103, "请求体格式错误"),
    METHOD_NOT_SUPPORTED(10104, "请求方法不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(10105, "媒体类型不支持"),
    BUSINESS_RULE_ERROR(10106, "业务规则校验失败"),

    // --- 102xx: 认证与账号类 (v1.6 §7.6) ---
    UNAUTHORIZED(10200, "用户未登录"),
    TOKEN_EXPIRED(10201, "登录凭证已过期"),
    TOKEN_INVALID(10202, "登录凭证无效"),
    CREDENTIAL_ERROR(10203, "账号密码错误"),
    ACCOUNT_DISABLED(10204, "账号已被冻结"),
    KICKED_OUT(10205, "账号在异地登录被踢下线"),
    CAPTCHA_ERROR(10206, "验证码错误"),
    TOKEN_INVALID_FORMAT(10207, "令牌格式错误"),
    TOKEN_REVOKED(10208, "令牌已注销"),
    REFRESH_EXPIRED(10210, "刷新令牌已过期"),
    REFRESH_INVALID(10211, "刷新令牌无效或已使用"),
    REFRESH_ROTATION_EXCEEDED(10212, "刷新令牌轮转次数超限，请重新登录"),

    // --- 103xx: 权限与授权类 (v1.6 §7.7) ---
    FORBIDDEN(10300, "无权限访问"),
    DATA_PERMISSION_DENIED(10301, "数据权限不足"),
    SIGNATURE_ERROR(10302, "签名验证失败"),
    IP_RESTRICTED(10303, "IP 限制访问"),

    // --- 104xx: 资源与数据类 (v1.6 §7.8) ---
    NOT_FOUND(10400, "请求资源不存在"),
    UNIQUE_CONFLICT(10401, "数据已存在"),
    STATE_CONFLICT(10402, "数据状态冲突"),
    LOCKED(10403, "数据被锁定"),
    DATA_INTEGRITY_VIOLATION(10404, "数据完整性约束失败"),

    // --- 105xx: 流量控制 + 文件上传类 (v1.7 §7.9) ---
    TOO_MANY_REQUESTS(10500, "请求过于频繁"),
    DUPLICATE_SUBMIT(10501, "请勿重复提交"),
    SERVICE_DEGRADE(10502, "服务降级"),
    UPLOAD_FAILED(10503, "文件上传失败"),
    FILE_TYPE_ERROR(10504, "文件类型不支持"),
    FILE_SIZE_EXCEED(10505, "文件体积过大"),
    FILE_EMPTY(10506, "文件内容为空"),

    // --- 106xx: 业务自定义错误 (v1.7 §7.10) ---
    // 段位由各业务线按需登记认领，本枚举不预定义具体值。
    // 通用错误码（10xxx ~ 105xx）无法精确表达的业务规则错误应落到此段。
    // 登记规范参见 mc-api-spec v1.7 §7.10。

    // --- 10700: 业务混合结果类 (v1.6 §7.11) ---
    PARTIAL_SUCCESS(10700, "部分成功");

    private final int code;
    private final String message;

    private static final Map<Integer, ApiCode> CODE_MAP;

    static {
        Map<Integer, ApiCode> m = new HashMap<>(values().length * 2);
        for (ApiCode v : values()) {
            m.put(v.code, v);
        }
        CODE_MAP = Map.copyOf(m);
    }

    /**
     * 根据错误码查找枚举
     *
     * @param code 错误码
     * @return ApiCode 枚举，未找到返回 null
     */
    public static ApiCode fromCode(int code) {
        return CODE_MAP.get(code);
    }
}
