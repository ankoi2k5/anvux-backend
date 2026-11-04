package com.anvux;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

class ReminderApiIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final Instant BASE = Instant.parse("2026-12-01T00:00:00Z");

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

    /** Tạo một dự án kèm một công việc, trả về id công việc. */
    private String createTask(String token) throws Exception {
        String projectJson = mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String projectId = JsonPath.read(projectJson, "$.id");

        String taskJson = mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"VietBaoCao"}""".formatted(projectId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(taskJson, "$.id");
    }

    private static String reminderBody(String taskId, Instant remindAt) {
        return """
                {"taskId":"%s","remindAt":"%s"}""".formatted(taskId, remindAt);
    }

    private String createReminder(String token, String taskId, Instant remindAt) throws Exception {
        String json = mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, remindAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    // ---------- Các ca kiểm tra ----------

    @Test
    void reminders_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reminders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReminder_thenListByTask() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        createReminder(token, taskId, BASE);

        mockMvc.perform(get("/api/v1/reminders").param("taskId", taskId).header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void createReminder_withSameTime_returns409() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        createReminder(token, taskId, BASE);

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, BASE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_EXISTS"));
    }

    @Test
    void createReminder_overLimit_returns400() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        for (int i = 0; i < 10; i++) {
            createReminder(token, taskId, BASE.plusSeconds(60L * i));
        }

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, BASE.plusSeconds(60L * 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIMIT_REACHED"));
    }

    @Test
    void updateReminder_togglesEnabledAndChangesTime() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        String reminderId = createReminder(token, taskId, BASE);

        mockMvc.perform(patch("/api/v1/reminders/" + reminderId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(patch("/api/v1/reminders/" + reminderId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remindAt\":\"2026-12-02T08:30:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindAt").value("2026-12-02T08:30:00Z"));
    }

    @Test
    void listReminders_filtersByTimeRange() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        createReminder(token, taskId, Instant.parse("2026-12-01T09:00:00Z"));
        createReminder(token, taskId, Instant.parse("2026-12-20T09:00:00Z"));

        mockMvc.perform(get("/api/v1/reminders")
                        .param("taskId", taskId)
                        .param("from", "2026-12-10T00:00:00Z")
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void otherUser_cannotCreateReadChangeOrDeleteForeignReminder() throws Exception {
        String owner = registerAndGetToken();
        String other = registerAndGetToken();
        String taskId = createTask(owner);
        String reminderId = createReminder(owner, taskId, BASE);

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, BASE.plusSeconds(3600))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/reminders/" + reminderId)
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/reminders/" + reminderId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reminders").header(AUTH, bearer(other)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingTask_removesItsReminders() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        createReminder(token, taskId, BASE);

        mockMvc.perform(delete("/api/v1/tasks/" + taskId).header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reminders").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createReminder_withSameIdempotencyKey_createsOnlyOnce() throws Exception {
        String token = registerAndGetToken();
        String taskId = createTask(token);
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, BASE)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderBody(taskId, BASE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reminders").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }
}