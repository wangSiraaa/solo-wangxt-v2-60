package com.railwindow.sim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 冒烟：springdoc 文档可生成且覆盖核心 API；同时把快照写入 docs/openapi.json，
 * 便于在不启动服务时查阅接口契约。
 */
class OpenApiSmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApiDocumentCoversPlanLifecycleApis() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").exists())
                .andExpect(jsonPath("$.paths./api/plans.post").exists())
                .andExpect(jsonPath("$.paths./api/plans/{id}/schedule.post").exists())
                .andExpect(jsonPath("$.paths./api/plans/{id}/reschedule.post").exists())
                .andExpect(jsonPath("$.paths./api/plans/{id}/protections.post").exists())
                .andExpect(jsonPath("$.paths./api/plans/{id}/start.post").exists())
                .andExpect(jsonPath("$.paths./api/plans/{id}/closeout.post").exists())
                .andExpect(jsonPath("$.paths./api/reference/mutex-rules.post").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Path out = Paths.get("docs/openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out,
                objectMapper.readTree(json).toPrettyString() + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }
}
