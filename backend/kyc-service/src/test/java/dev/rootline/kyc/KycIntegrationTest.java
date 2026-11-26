package dev.rootline.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.kernel.security.RootlineHeaders;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KycIntegrationTest {

    private static final String ID = "A123456789";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;

    String tenant;

    @BeforeEach
    void tenant() {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void submitAndApproveHappyPath() throws Exception {
        mvc.perform(as("mia", "", get("/api/kyc/me"))).andExpect(jsonPath("$.data.status").value("UNVERIFIED"));

        submit("mia", ID, LocalDate.of(1995, 4, 12)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.maskedIdNumber").value("A******789"))
                .andExpect(jsonPath("$.data.flags").isEmpty());

        mvc.perform(reviewer(get("/api/kyc/review-queue"))).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(reviewer(post("/api/kyc/mia/approve"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.reviewedBy").value("reviewer"));

        submit("mia", ID, LocalDate.of(1995, 4, 12))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("KYC_ALREADY_VERIFIED"));
    }

    @Test
    void fullIdNumberIsNeverStoredOrPublished() throws Exception {
        submit("mia", ID, LocalDate.of(1995, 4, 12));
        mvc.perform(reviewer(post("/api/kyc/mia/approve")));

        String row = jdbc.queryForObject("SELECT row_to_json(k)::text FROM kyc_applications k WHERE tenant_id = ?",
                String.class, tenant);
        assertThat(row).doesNotContain(ID).contains("A******789");

        List<String> events = jdbc.queryForList("""
                SELECT payload::text FROM outbox_events WHERE payload->>'tenantId' = ? ORDER BY seq
                """, String.class, tenant);
        assertThat(events).hasSize(2).allSatisfy(e -> assertThat(e).doesNotContain(ID).doesNotContain("Chen")
                .doesNotContain("1995"));
        assertThat(events.get(1)).contains("\"newStatus\": \"VERIFIED\"");
    }

    @Test
    void sameDocumentUsedByAnotherUserIsFlaggedAndCannotBeApproved() throws Exception {
        submit("mia", ID, LocalDate.of(1995, 4, 12));
        submit("leo", "a 123 456 789", LocalDate.of(1990, 1, 1))
                .andExpect(jsonPath("$.data.flags[0]").value("DUPLICATE_DOCUMENT"));

        mvc.perform(reviewer(get("/api/kyc/review-queue").param("flaggedOnly", "true")))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value("leo"));

        mvc.perform(reviewer(post("/api/kyc/leo/approve")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("KYC_FLAGGED"));
        send(reviewer(post("/api/kyc/leo/reject")), Map.of("reason", "證件已被其他帳號使用"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void underageApplicantsAreFlagged() throws Exception {
        submit("kid", "B123456780", LocalDate.now().minusYears(16))
                .andExpect(jsonPath("$.data.flags[0]").value("UNDERAGE"));
    }

    @Test
    void reviewersCannotDecideTheirOwnSubmission() throws Exception {
        submit("reviewer", "B123456780", LocalDate.of(1988, 8, 8));
        mvc.perform(reviewer(post("/api/kyc/reviewer/approve")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
    }

    @Test
    void rejectedApplicantCanResubmit() throws Exception {
        submit("mia", ID, LocalDate.of(1995, 4, 12));
        submit("mia", ID, LocalDate.of(1995, 4, 12)).andExpect(jsonPath("$.code").value("KYC_UNDER_REVIEW"));
        send(reviewer(post("/api/kyc/mia/reject")), Map.of("reason", "地址不完整"));
        mvc.perform(as("mia", "", get("/api/kyc/me"))).andExpect(jsonPath("$.data.rejectReason").value("地址不完整"));

        submit("mia", ID, LocalDate.of(1995, 4, 12)).andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
    }

    @Test
    void accessControl() throws Exception {
        mvc.perform(post("/api/kyc/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as("mia", "IAM:USER_READ", get("/api/kyc/review-queue"))).andExpect(status().isForbidden());
        submit("mia", "A12345678X", LocalDate.of(1995, 4, 12))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NUMBER_INVALID"));
    }

    private ResultActions submit(String user, String idNumber, LocalDate dob) throws Exception {
        return send(as(user, "", post("/api/kyc/submit")), Map.of(
                "firstName", "Mia", "lastName", "Chen", "dateOfBirth", dob.toString(), "documentType", "national_id",
                "countryCode", "TW", "idNumber", idNumber,
                "address", Map.of("countryCode", "TW", "city", "Taipei", "postalCode", "100", "detail", "測試路 1 號")));
    }

    private MockHttpServletRequestBuilder as(String user, String permissions, MockHttpServletRequestBuilder request) {
        return request.header(RootlineHeaders.TENANT_ID, tenant).header(RootlineHeaders.USER_ID, user)
                .header(RootlineHeaders.USER_PERMISSIONS, permissions);
    }

    private MockHttpServletRequestBuilder reviewer(MockHttpServletRequestBuilder request) {
        return as("reviewer", "KYC:REVIEW", request);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
