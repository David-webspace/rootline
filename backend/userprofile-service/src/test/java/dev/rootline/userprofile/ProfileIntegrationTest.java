package dev.rootline.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.kernel.security.RootlineHeaders;
import dev.rootline.userprofile.application.service.ProfileService;
import dev.rootline.userprofile.application.service.ProfileService.IncomingEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    ProfileService profiles;

    String tenant;

    @BeforeEach
    void tenant() {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void identityAndKycStatusAreProjectedFromEventsInAnyOrder() throws Exception {
        // kyc 的事件比註冊事件先到：也要能先建檔，之後再補上 email
        assertThat(profiles.applyKycEvent(event("KycStatusChanged", Map.of("userId", "mia", "newStatus", "PENDING_REVIEW"))))
                .isTrue();
        UUID registered = UUID.randomUUID();
        profiles.applyUserEvent(new IncomingEvent(registered, "UserRegistered", tenant,
                Map.of("username", "mia", "email", "mia@northwind.example")));
        // 重送同一事件：被冪等擋下
        assertThat(profiles.applyUserEvent(new IncomingEvent(registered, "UserRegistered", tenant,
                Map.of("username", "mia", "email", "changed@x.example")))).isFalse();
        profiles.applyKycEvent(event("KycStatusChanged", Map.of("userId", "mia", "newStatus", "VERIFIED")));
        profiles.applyUserEvent(event("UserLocked", Map.of("username", "mia")));

        mvc.perform(as("mia", get("/api/profiles/me")))
                .andExpect(jsonPath("$.data.email").value("mia@northwind.example"))
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.accountStatus").value("LOCKED"));
    }

    @Test
    void ownerUpdatesOwnProfileWithValidation() throws Exception {
        send(as("mia", put("/api/profiles/me")), Map.of("displayName", "Mia Chen", "jobTitle", "Platform Engineer",
                "phone", "+886 2 1234-5678", "links", List.of(Map.of("label", "Blog", "url", "https://mia.example")),
                "preferences", Map.of("emailNotifications", false, "smsNotifications", true, "language", "en")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.details.displayName").value("Mia Chen"))
                .andExpect(jsonPath("$.data.preferences.language").value("en"));

        send(as("mia", put("/api/profiles/me")), Map.of("links",
                List.of(Map.of("label", "x", "url", "javascript:alert(1)")),
                "preferences", Map.of("emailNotifications", true, "smsNotifications", false, "language", "en")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
    }

    @Test
    void othersNeedUserReadPermission() throws Exception {
        mvc.perform(as("leo", get("/api/profiles/mia"))).andExpect(status().isForbidden());
        mvc.perform(as("leo", get("/api/profiles/mia")).header(RootlineHeaders.USER_PERMISSIONS, "IAM:USER_READ"))
                .andExpect(status().isOk());
        mvc.perform(as("mia", get("/api/profiles/mia"))).andExpect(status().isOk());
    }

    private IncomingEvent event(String type, Map<String, Object> payload) {
        return new IncomingEvent(UUID.randomUUID(), type, tenant, payload);
    }

    private MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) {
        return request.header(RootlineHeaders.TENANT_ID, tenant).header(RootlineHeaders.USER_ID, user);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
