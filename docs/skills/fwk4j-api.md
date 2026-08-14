
# framework4j-api 错误码规范

## 错误码段位

| 段位 | 含义 | 常用枚举 |
|---|---|---|
| `0` | 成功 | `SUCCESS` |
| `10xxx` | 系统 | `SYSTEM_BUSY(10001)` |
| `101xx` | 参数 | `PARAM_ERROR(10100)` `PARAM_MISSING(10101)` `PARAM_FORMAT_ERROR(10102)` |
| `102xx` | 认证 | `UNAUTHORIZED(10200)` `TOKEN_EXPIRED(10201)` `TOKEN_INVALID(10202)` |
| `103xx` | 权限 | `FORBIDDEN(10300)` `SIGNATURE_ERROR(10302)` |
| `104xx` | 资源 | `NOT_FOUND(10400)` `UNIQUE_CONFLICT(10401)` `STATE_CONFLICT(10402)` |
| `105xx` | 流量 | `TOO_MANY_REQUESTS(10500)` `DUPLICATE_SUBMIT(10501)` |
| `106xx` | 业务自定义 | 业务线登记 |
| `10700` | 部分成功 | `PARTIAL_SUCCESS` |

## 使用

```java
import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiResponse;

return ApiResponse.fail(ApiCode.NOT_FOUND, "订单不存在");
throw new ApiException(ApiCode.PARAM_MISSING, "items 不能为空");
ApiCode code = ApiCode.fromCode(10500); // → TOO_MANY_REQUESTS
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-api</artifactId>
    <version>v1.2.4</version>
</dependency>
```

> ApiResponse / GlobalExceptionHandler 已迁移到 `framework4j-web`
