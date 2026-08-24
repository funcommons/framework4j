package fun.commons.framework4j.transport;

import java.util.Map;

/**
 * HTTP 传输抽象（S2S 服务间调用用）。
 * <p>
 * 默认实现 {@link RestTemplateHttpTransport}（RestTemplate 同步 + 重试）。
 * 业务方可替换为 {@link WebClientHttpTransport}（响应式）/ gRPC / 带 S2S JWT
 * 拦截器的装饰器实现。
 * <p>
 * 开闭原则：新增传输协议无需改上层 Client，只替换此 Bean。
 *
 * @since 1.2.9
 */
public interface HttpTransport {

    Object post(String url, Object body, Map<String, String> headers);

    Object get(String url, Map<String, String> headers);

    Object put(String url, Object body, Map<String, String> headers);

    Object delete(String url, Map<String, String> headers);
}
