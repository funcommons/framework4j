package fun.commons.framework4j.openid;

import com.fasterxml.jackson.annotation.JsonProperty;
import fun.commons.framework4j.id.config.IdSdkAutoConfiguration;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.openid.annotation.OpenId;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenId 请求体容器级端到端(R1/R2 + 序列化侧 List 双向闭环)。
 * <p>
 * 目的:锁定 {@code OpenIdAutoConfiguration} 的 Jackson customizer 真的装到了<b>容器 ObjectMapper</b> 上,
 * 使得真实 MVC 管线下 {@code @RequestBody @OpenId Long} / {@code @OpenId List<Long>} 能反混淆,
 * 且出参 {@code @OpenId} 字段能再混淆(round-trip),非法串经 {@code GlobalExceptionHandler} 返错误码。
 * <p>
 * 这是 BeanPostProcessor 注册路径(绕开 modulesToInstall 互冲)的核心回归点。
 * <p>
 * 排除 {@link IdSdkAutoConfiguration} 以避免 Redis/Snowflake 依赖;OpenId + Web 自动装配足够。
 * 开启 support-integer/support-string:本测试以 @SpringBootApplication 装载,会组件扫描到同包其它测试的
 * @OpenId Integer/String 控制器,全开后这些字段合法、fail-fast 不误报,同时覆盖三开关全开的容器行为。
 */
@SpringBootTest(classes = OpenIdRequestBodyIntegrationTest.TestApp.class, properties = {
        "framework4j.openid.support-integer=true",
        "framework4j.openid.support-string=true"
})
@AutoConfigureMockMvc
@DisplayName("OpenId 请求体容器级端到端")
class OpenIdRequestBodyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpringBootApplication(exclude = IdSdkAutoConfiguration.class)
    @Import(BodyController.class)
    static class TestApp {
    }

    @RestController
    @RequestMapping("/openid")
    static class BodyController {

        record BodyReq(
                @JsonProperty("id") @OpenId Long id,
                @JsonProperty("tagIds") @OpenId List<Long> tagIds
        ) {
        }

        @Data
        static class BodyResp {
            @JsonProperty("echoId")
            @OpenId
            private Long echoId;          // @OpenId → 出参再混淆（证明 decode→encode 闭环）
            @JsonProperty("echoIdRaw")
            private String echoIdRaw;     // 解码后的明文（String，不受 Long→String 影响）
            @JsonProperty("echoTagIds")
            @OpenId
            private List<Long> echoTagIds; // @OpenId List → 出参混淆串数组（证明 List decode→encode 闭环）
        }

        @PostMapping(path = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        public BodyResp body(@RequestBody BodyReq req) {
            BodyResp resp = new BodyResp();
            resp.echoId = req.id();
            resp.echoIdRaw = req.id() == null ? null : String.valueOf(req.id());
            resp.echoTagIds = req.tagIds();
            return resp;
        }

        // 序列化侧回归（无 @RequestBody）：锁定 @OpenId Long 在容器级正确混淆。
        // 这是 v1.3 BeanPostProcessor 注册修复的核心证据——旧 modulesToInstall 写法下此断言会失败。
        @GetMapping(path = "/echo")
        public EchoResp echo(@RequestParam Long id) {
            EchoResp resp = new EchoResp();
            resp.id = id;        // @OpenId → 出参混淆
            resp.rawId = String.valueOf(id);
            return resp;
        }

        @Data
        static class EchoResp {
            @JsonProperty("id")
            @OpenId
            private Long id;
            @JsonProperty("rawId")
            private String rawId;
        }
    }

    @Test
    @DisplayName("序列化侧回归：@OpenId Long 在容器级正确混淆（旧 modulesToInstall 写法下会失败）")
    void serializerWorksAtContainerLevel() throws Exception {
        String oid = IdObfuscator.toOpenId(12345L);
        mockMvc.perform(get("/openid/echo").param("id", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(oid))
                .andExpect(jsonPath("$.rawId").value("12345"));
    }

    @Test
    @DisplayName("容器级 round-trip：@RequestBody @OpenId Long + List 反混淆 → 出参再混淆")
    void requestBodyRoundTrip() throws Exception {
        String oid = IdObfuscator.toOpenId(12345L);
        String tagOid = IdObfuscator.toOpenId(1L);
        String json = "{\"id\":\"" + oid + "\",\"tagIds\":[\"" + tagOid + "\"]}";

        mockMvc.perform(post("/openid/body").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                // 标量：decode(12345) → 再混淆
                .andExpect(jsonPath("$.echoId").value(oid))
                // 解码后的明文 = "12345"
                .andExpect(jsonPath("$.echoIdRaw").value("12345"))
                // List：decode(1) → 再混淆
                .andExpect(jsonPath("$.echoTagIds[0]").value(tagOid));
    }

    @Test
    @DisplayName("兼容期：请求体传纯数字 → 直接透传解码")
    void requestBodyNumericPassthrough() throws Exception {
        mockMvc.perform(post("/openid/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"99999\",\"tagIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echoIdRaw").value("99999"));
    }

    @Test
    @DisplayName("非法 OpenId 串 → MismatchedInputException → GlobalExceptionHandler 返 BODY_FORMAT_ERROR(10103) + HTTP 200")
    void invalidRequestBodyRejected() throws Exception {
        mockMvc.perform(post("/openid/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"not-a-valid-openid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10103));
    }
}
