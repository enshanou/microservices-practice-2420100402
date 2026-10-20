package com.zjgsu.oes.controller;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Sql("/test-data.sql")
class ApiTests {
    private static final String COURSE = "11111111-1111-4111-8111-111111111111";
    @Autowired MockMvc mvc;

    @Test
    void createsCourseAndReturnsLocationThenReadsIt() throws Exception {
        MvcResult created = mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerId":"oes","title":"Java course","sourceReference":"imports/java.srt"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Java course"))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/courses/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("oes"));
    }

    @Test
    void listsAutomaticallyPreparedDemoCourseAndHistory() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Microservices demo"));
        mvc.perform(get("/api/courses/" + COURSE + "/tasks")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }

    @Test
    void createsTaskThenRejectsDuplicateWith409() throws Exception {
        mvc.perform(post("/api/courses/" + COURSE + "/tasks"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/courses/" + COURSE + "/tasks"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Course already has an active processing task"));
        mvc.perform(get("/api/courses/" + COURSE + "/tasks"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updatesTaskToSuccessThenAllowsNewAttempt() throws Exception {
        String task = createTask();
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/tasks/" + task)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(COURSE));
        mvc.perform(post("/api/courses/" + COURSE + "/tasks")).andExpect(status().isCreated());
    }

    @Test
    void reportsFailureReasonAndRejectsEmptyReason() throws Exception {
        String task = createTask();
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FAILED\",\"failureReason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FAILED\",\"failureReason\":\"source unavailable\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.failureReason").value("source unavailable"));
    }

    @Test
    void rejectsBlankCourseInputWith400() throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"oes\",\"title\":\"\",\"sourceReference\":\"imports/test.srt\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsOversizedInputWith400() throws Exception {
        String json = "{\"ownerId\":\"oes\",\"title\":\"" + "x".repeat(201)
                + "\",\"sourceReference\":\"imports/test.srt\"}";
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForMissingCourseAndTask() throws Exception {
        mvc.perform(get("/api/courses/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/" + UUID.randomUUID() + "/tasks")).andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMalformedUuidAndJsonWith400() throws Exception {
        mvc.perform(get("/api/courses/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidStateAndUnknownStatus() throws Exception {
        String task = createTask();
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/tasks/" + task + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/" + task)).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retainsHelloAndHealthEndpoints() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String createTask() throws Exception {
        MvcResult result = mvc.perform(post("/api/courses/" + COURSE + "/tasks"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
