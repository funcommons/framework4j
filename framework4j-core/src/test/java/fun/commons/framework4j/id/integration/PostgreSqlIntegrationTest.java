package fun.commons.framework4j.id.integration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.id.generator.MpIdGenerator;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.util.IdAnalysisTool;
import fun.commons.framework4j.datasource.config.MultiDataSourceAutoConfiguration;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL 集成测试
 * <p>
 * 测试 MyBatis-Plus ID 生成器与 PostgreSQL 的集成
 */
@SpringBootTest(classes = PostgreSqlIntegrationTest.TestApplication.class)
@ActiveProfiles("pgsql-test")
@DisplayName("PostgreSQL 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgreSqlIntegrationTest {

    @Autowired
    private SnowflakeDistributor snowflakeDistributor;

    @Autowired
    private MpIdGenerator mpIdGenerator;

    @Autowired
    private TestEntityMapper testEntityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 先尝试创建表（H2兼容语法）
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS test_entity (" +
                            "id BIGINT PRIMARY KEY, " +
                            "name VARCHAR(255), " +
                            "description TEXT" +
                            ")"
            );
            // 表创建成功，清空数据
            jdbcTemplate.execute("DELETE FROM test_entity");
        } catch (Exception e) {
            // 如果创建失败，尝试清空（可能表已存在）
            try {
                jdbcTemplate.execute("DELETE FROM test_entity");
            } catch (Exception deleteEx) {
                // 如果还是失败，记录错误但不中断测试
                System.err.println("Warning: Could not initialize test_entity table: " + deleteEx.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("SnowflakeDistributor 注入测试")
    class DistributorTests {

        @Test
        @Order(1)
        @DisplayName("SnowflakeDistributor 应正确注入")
        void shouldInjectDistributor() {
            assertThat(snowflakeDistributor).isNotNull();
            assertThat(snowflakeDistributor.getSdkWorkerId()).isBetween(0L, 1023L);
        }

        @Test
        @Order(2)
        @DisplayName("生成的 ID 应唯一且递增")
        void shouldGenerateUniqueIds() {
            long id1 = snowflakeDistributor.nextId();
            long id2 = snowflakeDistributor.nextId();
            long id3 = snowflakeDistributor.nextId();

            assertThat(id1).isPositive();
            assertThat(id2).isGreaterThan(id1);
            assertThat(id3).isGreaterThan(id2);
        }
    }

    @Nested
    @DisplayName("MpIdGenerator 注入测试")
    class MpIdGeneratorTests {

        @Test
        @Order(10)
        @DisplayName("MpIdGenerator 应正确注入")
        void shouldInjectMpIdGenerator() {
            assertThat(mpIdGenerator).isNotNull();
        }

        @Test
        @Order(11)
        @DisplayName("nextId 应生成有效 ID")
        void shouldGenerateValidIds() {
            Number id = mpIdGenerator.nextId(null);
            assertThat(id.longValue()).isPositive();
        }

        @Test
        @Order(12)
        @DisplayName("nextLongId 应生成 Long 类型 ID")
        void shouldGenerateLongIds() {
            long id = mpIdGenerator.nextLongId();
            assertThat(id).isPositive();
        }

        @Test
        @Order(13)
        @DisplayName("nextStringId 应生成 String 类型 ID")
        void shouldGenerateStringIds() {
            String id = mpIdGenerator.nextStringId();
            assertThat(id).isNotEmpty();
            assertThat(Long.parseLong(id)).isPositive();
        }
    }

    @Nested
    @DisplayName("MyBatis-Plus 自动填充测试")
    class MyBatisPlusTests {

        @Test
        @Order(20)
        @DisplayName("插入实体时应自动生成 ID")
        void shouldAutoGenerateIdOnInsert() {
            TestEntity entity = new TestEntity();
            entity.setName("Test Entity 1");
            entity.setDescription("This is a test");

            int rows = testEntityMapper.insert(entity);

            assertThat(rows).isEqualTo(1);
            assertThat(entity.getId()).isPositive();

            System.out.println("Generated ID: " + entity.getId());
        }

        @Test
        @Order(21)
        @DisplayName("批量插入应生成唯一 ID")
        void shouldGenerateUniqueIdsForBatchInsert() {
            for (int i = 0; i < 10; i++) {
                TestEntity entity = new TestEntity();
                entity.setName("Entity " + i);
                entity.setDescription("Description " + i);

                testEntityMapper.insert(entity);
                assertThat(entity.getId()).isPositive();
            }

            List<TestEntity> allEntities = testEntityMapper.selectList(null);
            assertThat(allEntities).hasSize(10);

            // 检查所有 ID 唯一
            long distinctCount = allEntities.stream()
                    .map(TestEntity::getId)
                    .distinct()
                    .count();
            assertThat(distinctCount).isEqualTo(10);
        }

        @Test
        @Order(22)
        @DisplayName("生成的 ID 应可被 IdAnalysisTool 解析")
        void shouldBeAnalyzableByIdAnalysisTool() {
            TestEntity entity = new TestEntity();
            entity.setName("Analyzable Entity");

            testEntityMapper.insert(entity);
            long generatedId = entity.getId();

            IdAnalysisTool.IdInfo info = IdAnalysisTool.parse(generatedId);

            assertThat(info.id()).isEqualTo(generatedId);
            assertThat(info.sdkWorkerId()).isBetween(0L, 1023L);
            assertThat(info.dateTime()).isNotNull();
            assertThat(info.sequence()).isBetween(0L, 4095L);

            System.out.println("Parsed ID Info: " + info);
        }
    }

    @Nested
    @DisplayName("数据库操作测试")
    class DatabaseTests {

        @Test
        @Order(30)
        @DisplayName("插入并查询实体")
        void shouldInsertAndQueryEntity() {
            TestEntity entity = new TestEntity();
            entity.setName("Query Test");
            entity.setDescription("Testing query functionality");

            testEntityMapper.insert(entity);
            long insertedId = entity.getId();

            TestEntity queried = testEntityMapper.selectById(insertedId);

            assertThat(queried).isNotNull();
            assertThat(queried.getId()).isEqualTo(insertedId);
            assertThat(queried.getName()).isEqualTo("Query Test");
            assertThat(queried.getDescription()).isEqualTo("Testing query functionality");
        }

        @Test
        @Order(31)
        @DisplayName("更新实体")
        void shouldUpdateEntity() {
            TestEntity entity = new TestEntity();
            entity.setName("Original Name");

            testEntityMapper.insert(entity);
            long id = entity.getId();

            entity.setName("Updated Name");
            entity.setDescription("Updated Description");
            testEntityMapper.updateById(entity);

            TestEntity updated = testEntityMapper.selectById(id);
            assertThat(updated.getName()).isEqualTo("Updated Name");
            assertThat(updated.getDescription()).isEqualTo("Updated Description");
        }

        @Test
        @Order(32)
        @DisplayName("删除实体")
        void shouldDeleteEntity() {
            TestEntity entity = new TestEntity();
            entity.setName("To Be Deleted");

            testEntityMapper.insert(entity);
            long id = entity.getId();

            int rows = testEntityMapper.deleteById(id);
            assertThat(rows).isEqualTo(1);

            TestEntity deleted = testEntityMapper.selectById(id);
            assertThat(deleted).isNull();
        }
    }

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @Order(40)
        @DisplayName("批量插入性能测试")
        void shouldInsertManyEntitiesQuickly() {
            int count = 1000;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                TestEntity entity = new TestEntity();
                entity.setName("Perf Test " + i);
                testEntityMapper.insert(entity);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.printf("Inserted %d entities in %d ms (%.2f ops/sec)%n",
                    count, duration, count * 1000.0 / duration);

            assertThat(duration).isLessThan(30000); // 应在30秒内完成
        }
    }

    // ==================== 测试配置 ====================

    @SpringBootApplication
    @org.mybatis.spring.annotation.MapperScan("com.ldx2t.commons.id.integration")
    @org.springframework.context.annotation.Import({
            MultiDataSourceAutoConfiguration.class,
            IdSdkAutoConfiguration.class
    })
    static class TestApplication {
    }

    @Data
    @TableName("test_entity")
    static class TestEntity {
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;
        private String name;
        private String description;
    }

    @Mapper
    interface TestEntityMapper extends BaseMapper<TestEntity> {
    }
}
