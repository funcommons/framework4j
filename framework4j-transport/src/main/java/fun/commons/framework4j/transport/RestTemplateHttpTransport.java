package fun.commons.framework4j.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 默认 HttpTransport 实现（RestTemplate 同步）+ 重试（默认 3 次, 500ms 间隔）。
 * <p>
 * 跨进程调用网络抖动自动重试，重试耗尽抛异常（业务方 catch）。
 * 熔断由业务方自配（如 Resilience4j circuitbreaker）。
 *
 * @since 1.2.9
 */
public class RestTemplateHttpTransport implements HttpTransport {

    private static final Logger log = LoggerFactory.getLogger(RestTemplateHttpTransport.class);

    private final RestTemplate restTemplate;
    private final int maxAttempts;
    private final long retryIntervalMs;

    public RestTemplateHttpTransport(RestTemplate restTemplate) {
        this(restTemplate, 3, 500);
    }

    public RestTemplateHttpTransport(RestTemplate restTemplate, int maxAttempts, long retryIntervalMs) {
        this.restTemplate = restTemplate;
        this.maxAttempts = maxAttempts;
        this.retryIntervalMs = retryIntervalMs;
    }

    @Override
    public Object post(String url, Object body, Map<String, String> headers) {
        return withRetry(() -> {
            HttpHeaders h = buildHeaders(headers);
            HttpEntity<Object> entity = new HttpEntity<>(body, h);
            return restTemplate.postForObject(url, entity, Object.class);
        }, url, "POST");
    }

    @Override
    public Object get(String url, Map<String, String> headers) {
        return withRetry(() -> {
            HttpHeaders h = buildHeaders(headers);
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Object.class).getBody();
        }, url, "GET");
    }

    @Override
    public Object put(String url, Object body, Map<String, String> headers) {
        return withRetry(() -> {
            HttpHeaders h = buildHeaders(headers);
            h.setContentType(MediaType.APPLICATION_JSON);
            return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, h), Object.class).getBody();
        }, url, "PUT");
    }

    @Override
    public Object delete(String url, Map<String, String> headers) {
        return withRetry(() -> {
            HttpHeaders h = buildHeaders(headers);
            return restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(h), Object.class).getBody();
        }, url, "DELETE");
    }

    private HttpHeaders buildHeaders(Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) headers.forEach(h::add);
        return h;
    }

    /** 重试包装: maxAttempts 次, 每次间隔 retryIntervalMs */
    private Object withRetry(java.util.function.Supplier<Object> call, String url, String method) {
        Exception last = null;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return call.get();
            } catch (Exception e) {
                last = e;
                if (i < maxAttempts) {
                    log.warn("[HttpTransport] {} {} 第{}次失败, 重试: {}", method, url, i, e.getMessage());
                    try { Thread.sleep(retryIntervalMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new RuntimeException("HTTP " + method + " " + url + " 重试 " + maxAttempts + " 次仍失败", last);
    }
}
