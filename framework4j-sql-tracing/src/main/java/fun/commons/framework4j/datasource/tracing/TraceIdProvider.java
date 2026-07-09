package fun.commons.framework4j.datasource.tracing;

/**
 * TraceId 提供者接口
 * <p>
 * 用于从不同的追踪系统获取当前请求的 TraceId。
 * 支持 Micrometer Tracing, SkyWalking, Zipkin 等多种追踪系统。
 * <p>
 * 用户可以实现此接口来适配自定义的追踪系统。
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * 获取当前请求的 TraceId
     *
     * @return TraceId, 如果没有则返回 null 或空字符串
     */
    String getTraceId();
}
