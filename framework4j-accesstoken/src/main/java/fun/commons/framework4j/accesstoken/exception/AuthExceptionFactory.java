package fun.commons.framework4j.accesstoken.exception;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;

/**
 * v2.1: 抽出 throwAuthException 通用逻辑（TokenInterceptor + AccessToken/RefreshValidationStrategy 三处重复）。
 * <p>反射构造自定义异常，优先 (int, String) 构造，回退 (String)，最后无参。
 */
public final class AuthExceptionFactory {

    private AuthExceptionFactory() {}

    /**
     * 抛出自定义异常或 AuthException。
     *
     * @param annotation @RequiresToken 注解（含 exception() 类型）
     * @param code       错误码
     * @param message    错误消息
     * @throws Exception 反射构造的异常或 AuthException
     */
    public static void throwCustom(RequiresToken annotation, int code, String message) throws Exception {
        Class<? extends Exception> exceptionClass = annotation.exception();
        if (exceptionClass != AuthException.class) {
            try {
                throw exceptionClass.getConstructor(int.class, String.class).newInstance(code, message);
            } catch (NoSuchMethodException ignore) {
                try {
                    throw exceptionClass.getConstructor(String.class).newInstance(message);
                } catch (NoSuchMethodException e2) {
                    throw exceptionClass.getConstructor().newInstance();
                }
            }
        }
        throw new AuthException(code, message);
    }
}
