package fun.commons.framework4j.cache;

import fun.commons.framework4j.cache.annotation.CacheableEvict;
import fun.commons.framework4j.cache.annotation.CacheableGet;
import fun.commons.framework4j.cache.annotation.CacheablePut;
import fun.commons.framework4j.cache.service.CacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration-test")
class CacheableAspectTest {

    @SpringBootApplication
    @Import(TestUserService.class)
    static class TestApp {}

    @Autowired
    private TestUserService testUserService;

    @Autowired
    private CacheService cacheService;

    @Test
    @DisplayName("@CacheableGet: first call loads, second from cache")
    void cacheableGet() {
        String key = "aop-get-" + System.nanoTime();
        testUserService.resetLoadCount();
        cacheService.evict("aspect-user", key);

        TestUserService.User u1 = testUserService.getUser(key);
        TestUserService.User u2 = testUserService.getUser(key);
        assertThat(u1.getName()).isEqualTo("name-" + key);
        assertThat(testUserService.getLoadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("@CacheablePut: updates cache")
    void cacheablePut() {
        String key = "aop-put-" + System.nanoTime();
        testUserService.resetLoadCount();
        cacheService.evict("aspect-user", key);

        testUserService.getUser(key);
        int countAfterFirst = testUserService.getLoadCount();
        testUserService.updateUser(key, "updated");
        TestUserService.User u = testUserService.getUser(key);
        assertThat(u.getName()).isEqualTo("updated");
        assertThat(testUserService.getLoadCount()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("@CacheableEvict: invalidates cache")
    void cacheableEvict() {
        String key = "aop-evict-" + System.nanoTime();
        testUserService.resetLoadCount();
        cacheService.evict("aspect-user", key);

        testUserService.getUser(key);
        assertThat(testUserService.getLoadCount()).isEqualTo(1);
        testUserService.deleteUser(key);
        testUserService.getUser(key);
        assertThat(testUserService.getLoadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("@CacheableGet SpEL: #user.id resolves")
    void cacheableGetSpelKey() {
        String key = "aop-spel-" + System.nanoTime();
        testUserService.resetLoadCount();
        cacheService.evict("aspect-user", key);

        TestUserService.User input = new TestUserService.User(key, "spel");
        testUserService.getUserByObject(input);
        testUserService.getUserByObject(input);
        assertThat(testUserService.getLoadCount()).isEqualTo(1);
    }

    @Component
    public static class TestUserService {
        private final AtomicInteger loadCount = new AtomicInteger(0);

        @CacheableGet(prefix = "aspect-user", key = "#id")
        public User getUser(String id) {
            loadCount.incrementAndGet();
            return new User(id, "name-" + id);
        }

        @CacheableGet(prefix = "aspect-user", key = "#user.id")
        public User getUserByObject(User user) {
            loadCount.incrementAndGet();
            return new User(user.getId(), "name-" + user.getId());
        }

        @CacheablePut(prefix = "aspect-user", key = "#id")
        public User updateUser(String id, String newName) {
            return new User(id, newName);
        }

        @CacheableEvict(prefix = "aspect-user", key = "#id")
        public void deleteUser(String id) {}

        public int getLoadCount() { return loadCount.get(); }
        public void resetLoadCount() { loadCount.set(0); }

        public static class User {
            private String id;
            private String name;
            public User() {}
            public User(String id, String name) { this.id = id; this.name = name; }
            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
    }
}
