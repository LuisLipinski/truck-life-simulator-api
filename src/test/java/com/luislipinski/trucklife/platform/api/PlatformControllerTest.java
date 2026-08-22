package com.luislipinski.trucklife.platform.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luislipinski.trucklife.shared.error.ApiExceptionHandler;
import com.luislipinski.trucklife.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class PlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void describesThePlatformAndPreservesAValidCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/platform")
                        .header(CorrelationIdFilter.HEADER_NAME, "request-42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-42"))
                .andExpect(jsonPath("$.service").value("truck-life-simulator-api"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.moduleCount").value(10));
    }

    @Test
    void listsAllBoundedModules() throws Exception {
        mockMvc.perform(get("/api/v1/platform/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].name").value("identity"))
                .andExpect(jsonPath("$[9].name").value("audit"));
    }

    @Test
    void returnsAStandardProblemForAnUnknownModule() throws Exception {
        mockMvc.perform(get("/api/v1/platform/modules/unknown")
                        .header(CorrelationIdFilter.HEADER_NAME, "request-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-404"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("request-404"));
    }
}
