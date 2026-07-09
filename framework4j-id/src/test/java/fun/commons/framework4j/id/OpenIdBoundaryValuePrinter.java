package fun.commons.framework4j.id;

import fun.commons.framework4j.id.util.IdObfuscator;

/**
 * 临时测试类：打印边界值的 OpenID
 */
public class OpenIdBoundaryValuePrinter {

    public static void main(String[] args) {
        // 测试 0
        long zero = 0L;
        String zeroOpenId = IdObfuscator.toOpenId(zero);
        System.out.println("ID: " + zero + " -> OpenID: " + zeroOpenId);

        // 测试 Long.MAX_VALUE
        long maxValue = Long.MAX_VALUE;
        String maxOpenId = IdObfuscator.toOpenId(maxValue);
        System.out.println("ID: " + maxValue + " -> OpenID: " + maxOpenId);

        // 验证双向转换
        long restoredZero = IdObfuscator.fromOpenId(zeroOpenId);
        long restoredMax = IdObfuscator.fromOpenId(maxOpenId);

        System.out.println("\n验证结果:");
        System.out.println("0 双向转换: " + (zero == restoredZero ? "✓ 成功" : "✗ 失败"));
        System.out.println("Long.MAX_VALUE 双向转换: " + (maxValue == restoredMax ? "✓ 成功" : "✗ 失败"));
    }
}
