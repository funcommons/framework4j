package fun.commons.framework4j.id.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * ID 混淆工具 (v2.1)
 * <p>
 * 将 Long ID 转换为固定 12 位的字符串 (OpenID)，用于:
 * <ul>
 * <li>防止 JS 精度丢失</li>
 * <li>隐藏真实业务量（支持连续ID生成离散的OpenID）</li>
 * <li>支持业务前缀</li>
 * <li><b>支持自校验，防止非法篡改</b></li>
 * </ul>
 * <p>
 * 结构: [11位数据载荷] + [1位校验码]
 * <p>
 * 算法: 乘法散列(Knuth Hash) + Base62 + XOR 异或 + 乱序字符集 + ISO 7064校验思想
 *
 * @author LDX2T
 * @since 2.1.0
 */
public class IdObfuscator {

    /**
     * 原始字符集 (62进制)
     */
    private static final String ORIGINAL_ALPHABET = "qWeRtYuIoPaSdFgHjKlZxCvBnM1234567890QwErTyUiOpAsDfGhJkLzXcVbNm";

    /**
     * 加盐种子 (硬编码，保证一致性)
     */
    private static final long SALT_SEED = 827394182374921L;

    /**
     * 乘法散列的大素数 (用于打散连续ID)
     * 选取一个接近 2^62 的素数，确保乘法后位分布剧烈变化
     */
    private static final long PRIME_MULTIPLIER = 2368876913961369317L;

    /**
     * 乘法逆元 (自动计算)
     * 用于还原: (obfuscated * INVERSE) mod 2^63 = original
     */
    private static final long PRIME_INVERSE;

    /**
     * 乱序后的字符集
     */
    private static final String SHUFFLED_ALPHABET;
    private static final char PAD_CHAR;

    /**
     * 进制基数
     */
    private static final int BASE;

    /**
     * XOR 掩码
     */
    private static final long XOR_MASK;

    /**
     * 固定输出长度
     */
    private static final int FIXED_LENGTH = 12;

    /**
     * 有效载荷长度
     */
    private static final int PAYLOAD_LENGTH = FIXED_LENGTH - 1;

    static {
        // 1. 初始化字符集
        List<Character> chars = new ArrayList<>();
        for (char c : ORIGINAL_ALPHABET.toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, new Random(SALT_SEED));

        StringBuilder sb = new StringBuilder();
        for (Character c : chars) {
            sb.append(c);
        }
        SHUFFLED_ALPHABET = sb.toString();
        BASE = SHUFFLED_ALPHABET.length();
        PAD_CHAR = SHUFFLED_ALPHABET.charAt(0);

        // 2. 初始化 XOR 掩码 (确保最高位为0，保证正数空间)
        XOR_MASK = (SALT_SEED ^ 4827194812345678L) & Long.MAX_VALUE;

        // 3. 计算 2^63 空间下的模逆元
        // 模数 = 2^63 (因为我们只使用正数 long，即 63 位)
        BigInteger modulus = BigInteger.valueOf(2).pow(63);
        BigInteger prime = BigInteger.valueOf(PRIME_MULTIPLIER);
        PRIME_INVERSE = prime.modInverse(modulus).longValue();
    }

    private IdObfuscator() {
        // 工具类禁止实例化
    }

    public static String toOpenId(int id) {
        return toOpenId((long) id);
    }

    public static String toOpenId(Integer id) {
        if (id == null) return null;
        return toOpenId(id.longValue());
    }

    public static String toOpenId(Long id) {
        if (id == null) return null;
        return toOpenId(id.longValue());
    }

    /**
     * 核心转换逻辑
     */
    public static String toOpenId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        // 1. 乘法散列 (Knuth Multiplicative Hash)
        // 这一步将连续的 ID 映射到伪随机的离散数值
        // 使用 & Long.MAX_VALUE 模拟 mod 2^63，确保结果为正数
        long stepped = (id * PRIME_MULTIPLIER) & Long.MAX_VALUE;

        // 2. 异或混淆
        long num = stepped ^ XOR_MASK;

        // 3. Base62 转换
        StringBuilder sb = new StringBuilder();
        if (num == 0) {
            sb.append(PAD_CHAR);
        } else {
            while (num > 0) {
                int remainder = (int) (num % BASE);
                sb.append(SHUFFLED_ALPHABET.charAt(remainder));
                num /= BASE;
            }
        }

        // 4. 填充与反转
        sb.reverse();
        while (sb.length() < PAYLOAD_LENGTH) {
            sb.insert(0, PAD_CHAR);
        }

        if (sb.length() > PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("ID value is too large for fixed length encoding");
        }

        // 5. 计算校验位
        char checkBit = calculateChecksum(sb);
        sb.append(checkBit);

        return sb.toString();
    }

    public static String toOpenId(long id, String prefix) {
        return prefix + "_" + toOpenId(id);
    }

    /**
     * 将 OpenID 还原为 Long ID
     */
    public static long fromOpenId(String openId) {
        if (openId == null || openId.isEmpty()) {
            throw new IllegalArgumentException("OpenID cannot be null or empty");
        }

        // 1. 基础校验与解析
        String realOpenId = stripPrefix(openId);
        if (realOpenId.length() != FIXED_LENGTH) {
            throw new IllegalArgumentException("Invalid OpenID length. Expected " + FIXED_LENGTH);
        }

        String payload = realOpenId.substring(0, PAYLOAD_LENGTH);
        char providedChecksum = realOpenId.charAt(PAYLOAD_LENGTH);
        
        // 2. 校验位验证
        if (providedChecksum != calculateChecksum(new StringBuilder(payload))) {
            throw new IllegalArgumentException("Invalid OpenID checksum");
        }

        // 3. Base62 还原数值
        long num = 0;
        for (int i = 0; i < payload.length(); i++) {
            int val = SHUFFLED_ALPHABET.indexOf(payload.charAt(i));
            if (val == -1) {
                throw new IllegalArgumentException("Invalid character: " + payload.charAt(i));
            }
            num = num * BASE + val;
        }

        // 4. 解除异或
        long stepped = num ^ XOR_MASK;

        // 5. 解除乘法散列 (乘以逆元)
        // 同样在 2^63 空间下进行运算
        long id = (stepped * PRIME_INVERSE) & Long.MAX_VALUE;

        return id;
    }

    /**
     * 验证 OpenID 格式是否有效
     */
    public static boolean isValid(String openId) {
        if (openId == null || openId.isEmpty()) return false;
        try {
            String realOpenId = stripPrefix(openId);
            if (realOpenId.length() != FIXED_LENGTH) return false;

            for (char c : realOpenId.toCharArray()) {
                if (SHUFFLED_ALPHABET.indexOf(c) == -1) return false;
            }

            String payload = realOpenId.substring(0, PAYLOAD_LENGTH);
            char providedChecksum = realOpenId.charAt(PAYLOAD_LENGTH);
            return providedChecksum == calculateChecksum(new StringBuilder(payload));
        } catch (Exception e) {
            return false;
        }
    }

    // ================= 内部辅助方法 =================

    private static String stripPrefix(String openId) {
        int lastUnderscore = openId.lastIndexOf('_');
        if (lastUnderscore != -1) {
            return openId.substring(lastUnderscore + 1);
        }
        return openId;
    }

    private static char calculateChecksum(StringBuilder payload) {
        long sum = 0;
        for (int i = 0; i < payload.length(); i++) {
            int val = SHUFFLED_ALPHABET.indexOf(payload.charAt(i));
            int weight = (i + 1); 
            sum += (long) val * weight;
        }
        int checksumIndex = (int) (sum % BASE);
        return SHUFFLED_ALPHABET.charAt(checksumIndex);
    }
}