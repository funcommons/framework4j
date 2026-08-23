
# framework4j-cache 多级缓存

## 编程式

```java
@Autowired private CacheService cacheService;

// 读（L1 → L2 → loader → 回填）
User user = cacheService.get("user", id, 3600, () -> userMapper.selectById(id), User.class);
// 写
cacheService.put("user", id, 3600, user);
// 删（双删 L1+L2）
cacheService.evict("user", id);
```

## 注解式（AOP）

```java
@CacheableGet(prefix = "user", key = "#id", nullTtl = 5)
public User getUser(String id) { return userMapper.selectById(id); }

@CacheablePut(prefix = "user", key = "#id")
public User updateUser(String id, String name) { ... }

@CacheableEvict(prefix = "user", key = "#id")
public void deleteUser(String id) {}
```

## 三防

| 能力 | 实现 |
|---|---|
| 防穿透 | 空值标记 `__NULL__`（短 TTL，默认 30s，`nullTtl` 可注解级覆盖） |
| 防击穿 | Lua 分布式锁 + per-key `CompletableFuture` 单飞 |
| 防雪崩 | TTL ±10% `ThreadLocalRandom` 抖动 |

## 批量预热

```java
@PostConstruct
void warmup() {
    cacheService.warmup("user", hotIds, 3600,
        id -> userMapper.selectById(id), User.class);
}
```

## 配置

```yaml
framework4j:
  cache:
    enabled: true
    default-ttl-seconds: 3600
    null-ttl-seconds: 30
    l1:
      enabled: true
      max-size: 10000
      expire-after-write: 600
    single-flight:
      enabled: true
      lock-ttl-seconds: 3
      wait-millis: 200
      max-retry: 10
```

## 引入

```xml
<dependency>
    <groupId>com.github.funcommons.framework4j</groupId>
    <artifactId>framework4j-cache</artifactId>
    <version>v1.2.8</version>
</dependency>
```
