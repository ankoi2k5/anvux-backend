package com.anvux;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class ProjectTaskApiIntegrationTest extends AbstractIntegrationTest {

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
        String body = """
                {"fullName":"Nguoi Dung","email":"%s","password":"12345678"}""".formatted(email);
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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

    private String createTask(String token, String projectId, String title, String dueAt) throws Exception {
        String body = dueAt == null
                ? """
                {"projectId":"%s","title":"%s"}""".formatted(projectId, title)
                : """
                {"projectId":"%s","title":"%s","dueAt":"%s"}""".formatted(projectId, title, dueAt);
        String json = mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(json, "$.id");
    }

    // ---------- Xác thực ----------

    @Test
    void projects_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Dự án ----------

    @Test
    void project_createListUpdateAndArchive() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("DoAn"));

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn2\",\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("DoAn2"))
                .andExpect(jsonPath("$.archived").value(true));

        // Dự án đã lưu trữ không hiện trong danh sách mặc định
        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/projects?includeArchived=true").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void project_withBlankName_returns400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- Phân quyền theo chủ sở hữu ----------

    @Test
    void otherUser_cannotReadChangeOrDeleteProject() throws Exception {
        String owner = registerAndGetToken();
        String other = registerAndGetToken();
        String projectId = createProject(owner, "RiengCuaA");

        mockMvc.perform(get("/api/v1/projects/" + projectId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/projects/" + projectId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(other)))
                .andExpect(jsonPath("$.length()").value(0));

        // Chủ sở hữu vẫn thấy nguyên vẹn
        mockMvc.perform(get("/api/v1/projects/" + projectId).header(AUTH, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("RiengCuaA"));
    }

    @Test
    void otherUser_cannotCreateTaskInForeignProject_orAccessForeignTask() throws Exception {
        String owner = registerAndGetToken();
        String other = registerAndGetToken();
        String projectId = createProject(owner, "RiengCuaA");
        String taskId = createTask(owner, projectId, "ViecCuaA", null);

        mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Chen ngang"}""".formatted(projectId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header(AUTH, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/tasks/" + taskId).header(AUTH, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(other)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- Công việc ----------

    @Test
    void task_statusChange_setsAndClearsCompletedAt() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");
        String taskId = createTask(token, projectId, "VietBaoCao", null);

        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void task_filters_byProjectStatusAndDueDate() throws Exception {
        String token = registerAndGetToken();
        String projectA = createProject(token, "A");
        String projectB = createProject(token, "B");
        createTask(token, projectA, "SomVaA", "2026-11-05T10:00:00Z");
        createTask(token, projectA, "MuonVaA", "2026-12-20T10:00:00Z");
        String doneTask = createTask(token, projectB, "XongVaB", null);

        mockMvc.perform(patch("/api/v1/tasks/" + doneTask)
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks").param("projectId", projectA).header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/tasks").param("status", "DONE").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("XongVaB"));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("dueBefore", "2026-11-30T00:00:00Z")
                        .header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("SomVaA"));
    }

    @Test
    void task_withBlankTitle_returns400() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");

        mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":""}""".formatted(projectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deletingProject_removesItsTasks() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");
        String taskId = createTask(token, projectId, "VietBaoCao", null);

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(AUTH, bearer(token)))
                .andExpect(status().isNotFound());
    }
    // ---------- Idempotency ----------

    @Test
    void createTask_withSameIdempotencyKey_createsOnlyOnce() throws Exception {
        String token = registerAndGetToken();
        String projectId = createProject(token, "DoAn");
        String body = """
                {"projectId":"%s","title":"VietBaoCao"}""".formatted(projectId);
        String key = UUID.randomUUID().toString();

        String first = mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String firstId = JsonPath.read(first, "$.id");

        mockMvc.perform(post("/api/v1/tasks")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(get("/api/v1/tasks").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createProject_withSameIdempotencyKey_createsOnlyOnce() throws Exception {
        String token = registerAndGetToken();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void idempotencyKey_isScopedPerUser() throws Exception {
        String userA = registerAndGetToken();
        String userB = registerAndGetToken();
        String key = UUID.randomUUID().toString();

        for (String token : new String[] {userA, userB}) {
            mockMvc.perform(post("/api/v1/projects")
                            .header(AUTH, bearer(token))
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"DuAnChung\"}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void idempotencyKey_tooLong_returns400() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/v1/projects")
                        .header(AUTH, bearer(token))
                        .header("Idempotency-Key", "a".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DoAn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }
}