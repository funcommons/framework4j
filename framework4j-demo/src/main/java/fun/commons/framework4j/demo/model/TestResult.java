package fun.commons.framework4j.demo.model;

/**
 * 集成测试结果
 */
public record TestResult(
        String module,      // 模块名
        String scenario,    // 场景描述
        String status,      // PASS / FAIL / SKIP
        String detail,      // 详细信息
        long durationMs     // 耗时
) {}
