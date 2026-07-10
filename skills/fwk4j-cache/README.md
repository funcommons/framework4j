# fwk4j-cache Skill
多级缓存 — Caffeine L1 + Redis L2 + 单飞防击穿 + 空值防穿透 + TTL 抖动防雪崩。
## 快查
- 编程式 → `cacheService.get/put/evict`
- 注解式 → `@CacheableGet/Put/Evict`
- 预热 → `cacheService.warmup`
- 空值 TTL → `@CacheableGet(nullTtl=5)`
