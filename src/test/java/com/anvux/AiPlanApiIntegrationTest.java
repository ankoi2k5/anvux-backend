package com.anvux;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

class AiPlanApiIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final String GOAL = "{\"prompt\":\"Chuan bi phong van thuc tap\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // ---------- Hàm hỗ trợ ----------

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nguoi Dung","email":"%s","password":"12345678"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String createPlanJson(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String createPlanId(String token) throws Exception {
        return JsonPath.read(createPlanJson(token, GOAL), "$.id");
    }

    private String createProject(String token, String name) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    // ---------- Các ca kiểm tra ----------

    @Test
    void plans_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOAL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPlan_returnsDraftWithItems() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOAL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.provider").value("fake"))
                .andExpect(jsonPath("$.projectName").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].createdTaskId").doesNotExist());
    }

    @Test
    void createPlan_doesNotCreateAnyRealProjectOrTask() throws Exception {
        String token = registerAndGetToken();
        createPlanId(token);

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createPlan_withBlankPrompt_returns400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPlan_withInvalidTimeZone_returns400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hoc bai\",\"timeZone\":\"Khong/Co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
    }

    @Test
    void createPlan_withForeignTargetProject_returns404() throws Exception {
        String owner = registerAndGetToken();
        String other = registerAndGetToken();
        String projectId = createProject(owner, "RiengCuaA");

        mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hoc bai","targetProjectId":"%s"}""".formatted(projectId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvePlan_createsProjectTasksAndReminders() throws Exception {
        String token = registerAndGetToken();
        String planId = createPlanId(token);

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.createdProjectId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].createdTaskId").isNotEmpty());

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(4));
        // Kế hoạch mẫu có hai việc kèm nhắc nhở.
        mockMvc.perform(get("/api/v1/reminders").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void approvePlan_twice_isIdempotent() throws Exception {
        String token = registerAndGetToken();
        String planId = createPlanId(token);

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void approvePlan_withEditedItems_createsOnlyTheEditedOnes() throws Exception {
        String token = registerAndGetToken();
        String planId = createPlanId(token);

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectName":"DoAnAI","items":[
                                  {"title":"Viec mot","priority":"HIGH"},
                                  {"title":"Viec hai","priority":"LOW"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$[0].name").value("DoAnAI"));
    }

    @Test
    void approvePlan_withEmptyItems_returns400AndCreatesNothing() throws Exception {
        String token = registerAndGetToken();
        String planId = createPlanId(token);

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_EMPTY"));

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void approvePlan_withTargetProject_addsTasksToExistingProject() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");
        String planId = JsonPath.read(createPlanJson(token, """
                {"prompt":"Hoc bai","targetProjectId":"%s"}""".formatted(projectId)), "$.id");

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks").param("projectId", projectId).header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void rejectedPlan_cannotBeApproved() throws Exception {
        String token = registerAndGetToken();
        String planId = createPlanId(token);

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/reject").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_DRAFT"));

        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void otherUser_cannotReadApproveOrRejectPlan() throws Exception {
        String owner = registerAndGetToken();
        String other = registerAndGetToken();
        String planId = createPlanId(owner);

        mockMvc.perform(get("/api/v1/ai/plans/" + planId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/approve").header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/ai/plans/" + planId + "/reject").header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ai/plans").header(AUTH, bearer(other)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listPlans_returnsOwnPlansNewestFirst() throws Exception {
        String token = registerAndGetToken();
        createPlanId(token);
        createPlanId(token);

        mockMvc.perform(get("/api/v1/ai/plans").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createPlan_overRateLimit_returns429() throws Exception {
        String token = registerAndGetToken();
        for (int i = 0; i < 10; i++) {
            createPlanId(token);
        }

        mockMvc.perform(post("/api/v1/ai/plans")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOAL))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}