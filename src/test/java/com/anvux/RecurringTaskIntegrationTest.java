package com.anvux;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

class RecurringTaskIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "Authorization";

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

    private String createProject(String token) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    private String createRecurringTask(String token, String projectId, String dueAt, String recurrence)
            throws Exception {
        String json = mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"UongThuoc","dueAt":"%s","recurrence":"%s"}"""
                                .formatted(projectId, dueAt, recurrence)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    private void setStatus(String token, String taskId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    // ---------- Các ca kiểm tra ----------

    @Test
    void recurrence_withoutDueDate_returns400() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);

        mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"X","recurrence":"DAILY"}""".formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECURRENCE_NEEDS_DUE_DATE"));
    }

    @Test
    void completingDailyTask_createsNextOccurrenceOneDayLater() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);
        String taskId = createRecurringTask(token, projectId, "2027-01-10T09:00:00Z", "DAILY");

        setStatus(token, taskId, "DONE");

        mockMvc.perform(get("/api/v1/tasks")
                        .param("projectId", projectId)
                        .param("status", "TODO")
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("UongThuoc"))
                .andExpect(jsonPath("$[0].recurrence").value("DAILY"))
                .andExpect(jsonPath("$[0].dueAt").value("2027-01-11T09:00:00Z"));
    }

    @Test
    void completingWeeklyTask_createsNextOccurrenceOneWeekLater() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);
        String taskId = createRecurringTask(token, projectId, "2027-01-10T09:00:00Z", "WEEKLY");

        setStatus(token, taskId, "DONE");

        mockMvc.perform(get("/api/v1/tasks")
                        .param("projectId", projectId)
                        .param("status", "TODO")
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$[0].dueAt").value("2027-01-17T09:00:00Z"));
    }

    @Test
    void completingTaskTwice_doesNotCreateDuplicateOccurrences() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);
        String taskId = createRecurringTask(token, projectId, "2027-01-10T09:00:00Z", "DAILY");

        setStatus(token, taskId, "DONE");
        setStatus(token, taskId, "TODO");
        setStatus(token, taskId, "DONE");

        mockMvc.perform(get("/api/v1/tasks").param("projectId", projectId).header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void completingOverdueRecurringTask_schedulesNextOccurrenceInTheFuture() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);
        String taskId = createRecurringTask(token, projectId, "2026-01-01T09:00:00Z", "DAILY");

        setStatus(token, taskId, "DONE");

        String json = mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "TODO")
                        .param("dueAfter", "2026-11-01T00:00:00Z")
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String nextDue = JsonPath.read(json, "$[0].dueAt");
        // Vẫn giữ đúng giờ trong ngày của lần đặt ban đầu.
        assertTrue(nextDue.endsWith("T09:00:00Z"), "Giờ trong ngày phải giữ nguyên: " + nextDue);
    }

    @Test
    void completingRecurringTask_copiesRemindersRelativeToNewDueDate() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token);
        String taskId = createRecurringTask(token, projectId, "2027-01-10T09:00:00Z", "DAILY");

        mockMvc.perform(post("/api/v1/reminders")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskId":"%s","remindAt":"2027-01-10T08:30:00Z"}""".formatted(taskId)))
                .andExpect(status().isCreated());

        setStatus(token, taskId, "DONE");

        String tasksJson = mockMvc.perform(get("/api/v1/tasks")
                        .param("projectId", projectId)
                        .param("status", "TODO")
                        .header(AUTH, bearer(token)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String nextTaskId = JsonPath.read(tasksJson, "$[0].id");

        mockMvc.perform(get("/api/v1/reminders")
                        .param("taskId", nextTaskId)
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].remindAt").value("2027-01-11T08:30:00Z"));
    }
}