package fun.commons.framework4j.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API 错误码枚举
 * <p>
 * 错误码格式：ABCCC (5位数字)
 * <ul>
 *   <li>A: 错误来源 (1: 系统/通用, 2: 业务A...)</li>
 *   <li>B: 模块分类 (0: 系统, 1: 参数, 2: 认证, 3: 权限, 4: 资源, 5: 流量, 6: 上传)</li>
 *   <li>CCC: 具体错误编号</li>
 * </ul>
 *
 * @author LDX2T
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ApiCode {

    // --- 0: 成功 ---
    SUCCESS(0, "操作成功"),

    // --- 10xxx: 系统与基础设施类 ---
    SYSTEM_BUSY(10001, "系统繁忙，请稍后再试"),
    SERVICE_MAINTENANCE(10002, "服务暂停维护"),
    SERVICE_TIMEOUT(10003, "服务调用超时"),
    THIRD_PARTY_ERROR(10004, "第三方服务异常"),
    MIDDLEWARE_ERROR(10005, "中间件服务异常"),

    // --- 101xx: 请求与参数校验类 ---
    PARAM_ERROR(10100, "请求参数错误"),
    PARAM_MISSING(10101, "必填参数缺失"),
    PARAM_FORMAT_ERROR(10102, "参数格式错误"),
    BODY_FORMAT_ERROR(10103, "请求体格式错误"),
    METHOD_NOT_SUPPORTED(10104, "请求方法不支持"),
    MEDIA_TYPE_NOT_SUPPORTED(10105, "媒体类型不支持"),
    BUSINESS_RULE_ERROR(10106, "业务规则校验失败"),

    // --- 102xx: 认证与账号类 ---
    UNAUTHORIZED(10200, "用户未登录"),
    TOKEN_EXPIRED(10201, "登录凭证已过期"),
    TOKEN_INVALID(10202, "登录凭证无效"),
    CREDENTIAL_ERROR(10203, "账号密码错误"),
    ACCOUNT_DISABLED(10204, "账号已被冻结"),
    ACCOUNT_KICKOUT(10205, "账号在异地登录被踢下线"),
    CAPTCHA_ERROR(10206, "验证码错误"),

    // --- 103xx: 权限与授权类 ---
    FORBIDDEN(10300, "无权限访问"),
    DATA_PERMISSION_DENIED(10301, "数据权限不足"),
    SIGNATURE_ERROR(10302, "签名验证失败"),
    IP_RESTRICTED(10303, "IP 限制访问"),

    // --- 104xx: 资源与数据类 ---
    RESOURCE_NOT_FOUND(10400, "请求资源不存在"),
    DATA_EXISTS(10401, "数据已存在"),
    STATE_CONFLICT(10402, "数据状态冲突"),
    RESOURCE_LOCKED(10403, "数据被锁定"),
    INTEGRITY_VIOLATION(10404, "数据完整性约束失败"),

    // --- 105xx: 流量控制类 ---
    TOO_MANY_REQUESTS(10500, "请求过于频繁"),
    DUPLICATE_SUBMIT(10501, "请勿重复提交"),
    SERVICE_DEGRADE(10502, "服务降级"),

    // --- 106xx: 文件与上传类 ---
    UPLOAD_FAILED(10600, "文件上传失败"),
    FILE_TYPE_ERROR(10601, "文件类型不支持"),
    FILE_SIZE_EXCEED(10602, "文件体积过大"),
    FILE_EMPTY(10603, "文件内容为空");

    private final int code;
    private final String message;

    /**
     * 根据错误码查找枚举
     *
     * @param code 错误码
     * @return ApiCode 枚举，未找到返回 SYSTEM_BUSY
     */
    public static ApiCode fromCode(int code) {
        for (ApiCode value : ApiCode.values()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        return SYSTEM_BUSY;
    }
}
