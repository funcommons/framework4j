package fun.commons.framework4j.datetime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 本地时间格式注解：出参 {@code OffsetDateTime} 输出为 {@code yyyy-MM-dd HH:mm:ss}（GMT+8，无时区）。
 * <p>
 * 默认（不标注解）出参为 ISO-8601（{@code 2026-08-14T10:00:00+08:00}，带时区，微服务间通信安全）。
 * 本注解是"纯前端展示接口"的例外通道，使用后应在接口文档中注明时间格式；
 * <b>微服务内部 API 禁用</b>（简化格式丢失时区信息）。
 *
 * <h3>各标注位置的语义（v1.2.4 起允许 PARAMETER）</h3>
 * <ul>
 *   <li><b>方法 / 类</b>（{@code METHOD} / {@code TYPE}）：真正生效的位置。
 *       {@link TimeFormatInterceptor} 检测后设置线程状态，出参序列化器
 *       {@link DynamicTimeFilter} 据此切换本地格式。</li>
 *   <li><b>参数</b>（{@code PARAMETER}）：<b>仅语义标记，无运行时效果</b>。
 *       入参时间解析由全局注册的 {@link StringToOffsetDateTimeConverter} 负责，
 *       对<b>所有</b> {@code OffsetDateTime} 参数生效（时间戳秒/毫秒、ISO-8601、
 *       {@code yyyy-MM-dd HH:mm:ss} 三格式通吃），与是否标注本注解无关。
 *       参数上标注仅表示"该入参接受多格式时间"，便于阅读。</li>
 * </ul>
 *
 * <p>v1.2.4（GitHub Issue #8）：{@code @Target} 补充 {@code PARAMETER}。此前文档示例
 * 将本注解标在 {@code @RequestParam} 参数上，但 {@code @Target} 不允许导致编译失败；
 * 现放行参数位置并明确其为语义标记。控制出参格式请标注在<b>方法或类</b>上。
 *
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalTimeFormat {
}
