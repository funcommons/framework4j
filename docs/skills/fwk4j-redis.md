
# framework4j-redis 多 Redis 数据源

## 配置

```yaml
framework4j:
  redis:
    enabled: true
    datasources:
      default:                        # STRING 类型（StringRedisTemplate）
        host: localhost
        port: 6379
        template-type: string
      cache:                          # OBJECT 类型（RedisTemplate<String, Object>）
        host: cache.redis.com
        database: 1
        template-type: object
      session:
        host: session.redis.com
        database: 2
        redisson:
          enabled: true               # 启用 Redisson 分布式锁
```

## @RedisOn 注解注入

```java
@Service
public class OrderService {
    @RedisOn("default")
    private StringRedisTemplate defaultTemplate;

    @RedisOn("cache")
    private RedisTemplate<String, Object> cacheTemplate;

    @RedisOn(value = "missing", strict = false)  // strict=false 缺失则 fallback default
    private StringRedisTemplate optionalTemplate;
}
```

## 健康检查

```java
boolean ok = multiRedisManager.checkHealth("default");
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-redis</artifactId>
    <version>v1.2.0</version>
</dependency>
```
