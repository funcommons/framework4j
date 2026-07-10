package fun.commons.framework4j.id.util;

public class OpenIdValueTest {
    public static void main(String[] args) {
        // 测试 Long.MAX_VALUE
        long maxValue = Long.MAX_VALUE;
        String maxOpenId = IdObfuscator.toOpenId(maxValue);
        System.out.println("Long.MAX_VALUE = " + maxValue);
        System.out.println("OpenID = " + maxOpenId);
        System.out.println("Length = " + maxOpenId.length());
        System.out.println();

        // 测试 0
        long zeroValue = 0L;
        String zeroOpenId = IdObfuscator.toOpenId(zeroValue);
        System.out.println("0L = " + zeroValue);
        System.out.println("OpenID = " + zeroOpenId);
        System.out.println("Length = " + zeroOpenId.length());
        System.out.println();

        // 验证反向转换
        System.out.println("--- 验证反向转换 ---");
        long restoredMax = IdObfuscator.fromOpenId(maxOpenId);
        System.out.println("Max restored: " + restoredMax + " (expected: " + maxValue + ") - " + (restoredMax == maxValue ? "✓" : "✗"));

        long restoredZero = IdObfuscator.fromOpenId(zeroOpenId);
        System.out.println("Zero restored: " + restoredZero + " (expected: " + zeroValue + ") - " + (restoredZero == zeroValue ? "✓" : "✗"));
    }
}
