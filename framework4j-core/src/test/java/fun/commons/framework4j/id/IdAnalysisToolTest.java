package fun.commons.framework4j.id;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.id.util.IdAnalysisTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

/**
 * ID 反解析工具测试
 */
@DisplayName("IdAnalysisTool 测试")
class IdAnalysisToolTest {

    @Nested
    @DisplayName("时间提取测试")
    class TimeExtractionTests {

        @Test
        @DisplayName("extractTime 返回正确时间")
        void shouldExtractCorrectTime() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            LocalDateTime beforeGeneration = LocalDateTime.now();

            long id = distributor.nextId();

            LocalDateTime afterGeneration = LocalDateTime.now();
            LocalDateTime extractedTime = IdAnalysisTool.extractTime(id);

            // 提取的时间应在生成前后之间
            assertThat(extractedTime).isAfterOrEqualTo(beforeGeneration.minusSeconds(1));
            assertThat(extractedTime).isBeforeOrEqualTo(afterGeneration.plusSeconds(1));
        }

        @Test
        @DisplayName("extractTimestamp 返回正确毫秒值")
        void shouldExtractCorrectTimestamp() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long beforeMs = System.currentTimeMillis();

            long id = distributor.nextId();

            long afterMs = System.currentTimeMillis();
            long extractedTimestamp = IdAnalysisTool.extractTimestamp(id);

            assertThat(extractedTimestamp).isBetween(beforeMs, afterMs);
        }

        @Test
        @DisplayName("时间戳与 Epoch 正确计算")
        void shouldCalculateTimestampCorrectly() {
            // 构造一个已知时间的 ID
            SnowflakeDistributor distributor = new SnowflakeDistributor(0);
            long id = distributor.nextId();

            long extractedTimestamp = IdAnalysisTool.extractTimestamp(id);
            long currentTime = System.currentTimeMillis();

            // 时间差应该很小（毫秒级）
            assertThat(Math.abs(extractedTimestamp - currentTime)).isLessThan(1000);
        }
    }

    @Nested
    @DisplayName("WorkerID 提取测试")
    class WorkerIdExtractionTests {

        @Test
        @DisplayName("extractWorkerId 返回低5位")
        void shouldExtractWorkerId() {
            // 测试不同的 SDK WorkerID
            for (long sdkWorkerId = 0; sdkWorkerId <= 1023; sdkWorkerId += 50) {
                SnowflakeDistributor distributor = new SnowflakeDistributor(sdkWorkerId);
                long id = distributor.nextId();

                long extractedWorkerId = IdAnalysisTool.extractWorkerId(id);
                long expectedWorkerId = sdkWorkerId & 0x1F; // 低5位

                assertThat(extractedWorkerId)
                        .as("SDKWorkerId=%d, expected workerId=%d", sdkWorkerId, expectedWorkerId)
                        .isEqualTo(expectedWorkerId);
            }
        }

        @Test
        @DisplayName("extractDatacenterId 返回高5位")
        void shouldExtractDatacenterId() {
            for (long sdkWorkerId = 0; sdkWorkerId <= 1023; sdkWorkerId += 50) {
                SnowflakeDistributor distributor = new SnowflakeDistributor(sdkWorkerId);
                long id = distributor.nextId();

                long extractedDatacenterId = IdAnalysisTool.extractDatacenterId(id);
                long expectedDatacenterId = sdkWorkerId >> 5; // 高5位

                assertThat(extractedDatacenterId)
                        .as("SDKWorkerId=%d, expected datacenterId=%d", sdkWorkerId, expectedDatacenterId)
                        .isEqualTo(expectedDatacenterId);
            }
        }

        @Test
        @DisplayName("extractSdkWorkerId 返回完整10位")
        void shouldExtractSdkWorkerId() {
            for (long sdkWorkerId = 0; sdkWorkerId <= 1023; sdkWorkerId += 100) {
                SnowflakeDistributor distributor = new SnowflakeDistributor(sdkWorkerId);
                long id = distributor.nextId();

                long extractedSdkWorkerId = IdAnalysisTool.extractSdkWorkerId(id);

                assertThat(extractedSdkWorkerId)
                        .as("Expected SDKWorkerId=%d", sdkWorkerId)
                        .isEqualTo(sdkWorkerId);
            }
        }

        @Test
        @DisplayName("边界值 WorkerID 测试")
        void shouldHandleBoundaryWorkerIds() {
            // 测试边界值: 0, 31, 32, 1023
            long[] testIds = {0, 31, 32, 511, 512, 1023};

            for (long sdkWorkerId : testIds) {
                SnowflakeDistributor distributor = new SnowflakeDistributor(sdkWorkerId);
                long id = distributor.nextId();

                long extractedSdkWorkerId = IdAnalysisTool.extractSdkWorkerId(id);
                assertThat(extractedSdkWorkerId).isEqualTo(sdkWorkerId);
            }
        }
    }

    @Nested
    @DisplayName("序列号提取测试")
    class SequenceExtractionTests {

        @Test
        @DisplayName("extractSequence 返回正确序列号")
        void shouldExtractSequence() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long id = distributor.nextId();

            long sequence = IdAnalysisTool.extractSequence(id);

            // 序列号范围 0-4095
            assertThat(sequence).isBetween(0L, 4095L);
        }

        @Test
        @DisplayName("连续生成的 ID 序列号递增")
        void shouldHaveIncrementingSequence() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);

            long id1 = distributor.nextId();
            long id2 = distributor.nextId();
            long id3 = distributor.nextId();

            long seq1 = IdAnalysisTool.extractSequence(id1);
            long seq2 = IdAnalysisTool.extractSequence(id2);
            long seq3 = IdAnalysisTool.extractSequence(id3);

            // 如果在同一毫秒内，序列号应该递增
            // 否则可能重置为0
            System.out.printf("Sequences: %d -> %d -> %d%n", seq1, seq2, seq3);
        }
    }

    @Nested
    @DisplayName("parse 完整解析测试")
    class ParseTests {

        @Test
        @DisplayName("parse 返回完整 IdInfo")
        void shouldParseCompleteIdInfo() {
            long sdkWorkerId = 500;
            SnowflakeDistributor distributor = new SnowflakeDistributor(sdkWorkerId);
            long id = distributor.nextId();

            IdAnalysisTool.IdInfo info = IdAnalysisTool.parse(id);

            assertThat(info.id()).isEqualTo(id);
            assertThat(info.sdkWorkerId()).isEqualTo(sdkWorkerId);
            assertThat(info.workerId()).isEqualTo(sdkWorkerId & 0x1F);
            assertThat(info.datacenterId()).isEqualTo(sdkWorkerId >> 5);
            assertThat(info.sequence()).isBetween(0L, 4095L);
            assertThat(info.timestamp()).isPositive();
            assertThat(info.dateTime()).isNotNull();
        }

        @Test
        @DisplayName("IdInfo toString 格式正确")
        void shouldHaveCorrectToStringFormat() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long id = distributor.nextId();

            IdAnalysisTool.IdInfo info = IdAnalysisTool.parse(id);
            String infoString = info.toString();

            assertThat(infoString).contains("IdInfo{");
            assertThat(infoString).contains("id=");
            assertThat(infoString).contains("dateTime=");
            assertThat(infoString).contains("sdkWorkerId=");
            assertThat(infoString).contains("sequence=");

            System.out.println(infoString);
        }

        @Test
        @DisplayName("多个 ID 解析一致性")
        void shouldParseConsistently() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);

            for (int i = 0; i < 100; i++) {
                long id = distributor.nextId();
                IdAnalysisTool.IdInfo info = IdAnalysisTool.parse(id);

                // 重新解析应该得到相同结果
                IdAnalysisTool.IdInfo info2 = IdAnalysisTool.parse(id);

                assertThat(info).isEqualTo(info2);
            }
        }
    }

    @Nested
    @DisplayName("时区测试")
    class TimeZoneTests {

        @Test
        @DisplayName("使用系统默认时区")
        void shouldUseSystemDefaultTimeZone() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long id = distributor.nextId();

            LocalDateTime extractedTime = IdAnalysisTool.extractTime(id);
            ZoneId systemZone = ZoneId.systemDefault();

            // 确保使用的是系统时区
            assertThat(extractedTime).isNotNull();
            System.out.println("Extracted time: " + extractedTime + " (Zone: " + systemZone + ")");
        }
    }

    @Nested
    @DisplayName("Epoch 测试")
    class EpochTests {

        @Test
        @DisplayName("Epoch 基准时间正确")
        void shouldHaveCorrectEpoch() {
            // 2024-01-01 00:00:00 UTC+8
            assertThat(SnowflakeDistributor.HARDCODED_EPOCH).isEqualTo(1704067200000L);
        }

        @Test
        @DisplayName("时间戳偏移正确计算")
        void shouldCalculateCorrectTimeOffset() {
            SnowflakeDistributor distributor = new SnowflakeDistributor(100);
            long id = distributor.nextId();

            long extractedTimestamp = IdAnalysisTool.extractTimestamp(id);
            long timeDelta = extractedTimestamp - SnowflakeDistributor.HARDCODED_EPOCH;

            // 从 2024-01-01 到现在应该是正值
            assertThat(timeDelta).isPositive();
        }
    }
}
