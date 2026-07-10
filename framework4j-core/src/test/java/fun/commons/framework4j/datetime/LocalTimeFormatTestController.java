package fun.commons.framework4j.datetime;

import fun.commons.framework4j.api.ApiResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * @LocalTimeFormat 注解测试 Controller
 */
@RestController
@RequestMapping("/api/time-test")
public class LocalTimeFormatTestController {

    /**
     * 方法级别注解测试 - 应该返回本地时间格式
     */
    @GetMapping("/method-level")
    @LocalTimeFormat  // 方法级别注解
    public ApiResponse<TestOrderVO> testMethodLevelAnnotation() {
        TestOrderVO order = new TestOrderVO();
        order.setId(1L);
        order.setName("测试订单");
        order.setCreateTime(OffsetDateTime.parse("2025-12-10T10:30:00+08:00"));
        order.setDeliveryTime(OffsetDateTime.parse("2025-12-15T14:20:00+08:00"));
        order.setEventTimes(Arrays.asList(
            OffsetDateTime.parse("2025-12-10T09:00:00+08:00"),
            OffsetDateTime.parse("2025-12-10T18:00:00+08:00")
        ));
        return ApiResponse.success(order);
    }

    /**
     * 类级别注解测试 - 所有方法都应该返回本地时间格式
     */
    @GetMapping("/class-level")
    public ApiResponse<TestOrderVO> testClassLevelAnnotation() {
        TestOrderVO order = new TestOrderVO();
        order.setId(2L);
        order.setName("类级别测试订单");
        order.setCreateTime(OffsetDateTime.parse("2025-11-20T15:45:00+08:00"));
        order.setDeliveryTime(OffsetDateTime.parse("2025-11-25T10:15:00+08:00"));
        order.setEventTimes(Arrays.asList(
            OffsetDateTime.parse("2025-11-20T08:00:00+08:00"),
            OffsetDateTime.parse("2025-11-20T17:30:00+08:00")
        ));
        return ApiResponse.success(order);
    }

    /**
     * 无注解测试 - 应该返回 ISO-8601 格式
     */
    @GetMapping("/no-annotation")
    public ApiResponse<TestOrderVO> testNoAnnotation() {
        TestOrderVO order = new TestOrderVO();
        order.setId(3L);
        order.setName("无注解测试订单");
        order.setCreateTime(OffsetDateTime.parse("2025-10-15T12:00:00+08:00"));
        order.setDeliveryTime(OffsetDateTime.parse("2025-10-20T16:30:00+08:00"));
        order.setEventTimes(Arrays.asList(
            OffsetDateTime.parse("2025-10-15T08:00:00+08:00"),
            OffsetDateTime.parse("2025-10-15T19:00:00+08:00")
        ));
        return ApiResponse.success(order);
    }

    /**
     * 批量数据测试 - 验证 List 中的所有对象都会被格式化
     */
    @GetMapping("/batch")
    @LocalTimeFormat  // 方法级别注解
    public ApiResponse<List<TestOrderVO>> testBatchData() {
        List<TestOrderVO> orders = Arrays.asList(
            createOrder(4L, "批量订单1", "2025-09-10T10:00:00+08:00", "2025-09-15T14:00:00+08:00"),
            createOrder(5L, "批量订单2", "2025-09-11T11:30:00+08:00", "2025-09-16T15:30:00+08:00"),
            createOrder(6L, "批量订单3", "2025-09-12T09:45:00+08:00", "2025-09-17T12:45:00+08:00")
        );
        return ApiResponse.success(orders);
    }

    /**
     * 复杂嵌套对象测试
     */
    @GetMapping("/nested")
    @LocalTimeFormat  // 方法级别注解
    public ApiResponse<ComplexOrderVO> testNestedObject() {
        ComplexOrderVO complexOrder = new ComplexOrderVO();
        complexOrder.setId(7L);
        complexOrder.setName("复杂嵌套订单");

        // 主要订单时间
        complexOrder.setCreateTime(OffsetDateTime.parse("2025-08-05T13:00:00+08:00"));
        complexOrder.setDeliveryTime(OffsetDateTime.parse("2025-08-10T17:00:00+08:00"));

        // 子订单
        TestOrderVO subOrder1 = new TestOrderVO();
        subOrder1.setId(8L);
        subOrder1.setName("子订单1");
        subOrder1.setCreateTime(OffsetDateTime.parse("2025-08-06T09:00:00+08:00"));
        subOrder1.setDeliveryTime(OffsetDateTime.parse("2025-08-11T13:00:00+08:00"));

        TestOrderVO subOrder2 = new TestOrderVO();
        subOrder2.setId(9L);
        subOrder2.setName("子订单2");
        subOrder2.setCreateTime(OffsetDateTime.parse("2025-08-07T14:30:00+08:00"));
        subOrder2.setDeliveryTime(OffsetDateTime.parse("2025-08-12T18:30:00+08:00"));

        complexOrder.setSubOrders(Arrays.asList(subOrder1, subOrder2));

        return ApiResponse.success(complexOrder);
    }

    private TestOrderVO createOrder(Long id, String name, String createTime, String deliveryTime) {
        TestOrderVO order = new TestOrderVO();
        order.setId(id);
        order.setName(name);
        order.setCreateTime(OffsetDateTime.parse(createTime));
        order.setDeliveryTime(OffsetDateTime.parse(deliveryTime));
        order.setEventTimes(Arrays.asList(
            OffsetDateTime.parse(createTime),
            OffsetDateTime.parse(deliveryTime)
        ));
        return order;
    }

    /**
     * 测试用 VO 类 - 无需注解
     */
    @Data
    public static class TestOrderVO {
        private Long id;
        private String name;
        private OffsetDateTime createTime;
        private OffsetDateTime deliveryTime;
        private List<OffsetDateTime> eventTimes;
    }

    /**
     * 复杂嵌套 VO
     */
    @Data
    public static class ComplexOrderVO {
        private Long id;
        private String name;
        private OffsetDateTime createTime;
        private OffsetDateTime deliveryTime;
        private List<TestOrderVO> subOrders;
    }
}

/**
 * 类级别注解测试 Controller
 */
@RestController
@RequestMapping("/api/time-test-class")
@LocalTimeFormat  // 类级别注解，所有方法生效
class ClassLevelAnnotationTestController {

    @GetMapping("/method1")
    public ApiResponse<LocalTimeFormatTestController.TestOrderVO> testMethod1() {
        LocalTimeFormatTestController.TestOrderVO order = new LocalTimeFormatTestController.TestOrderVO();
        order.setId(10L);
        order.setName("类级别方法1");
        order.setCreateTime(OffsetDateTime.parse("2025-07-10T10:00:00+08:00"));
        return ApiResponse.success(order);
    }

    @GetMapping("/method2")
    public ApiResponse<LocalTimeFormatTestController.TestOrderVO> testMethod2() {
        LocalTimeFormatTestController.TestOrderVO order = new LocalTimeFormatTestController.TestOrderVO();
        order.setId(11L);
        order.setName("类级别方法2");
        order.setCreateTime(OffsetDateTime.parse("2025-07-11T15:30:00+08:00"));
        return ApiResponse.success(order);
    }
}