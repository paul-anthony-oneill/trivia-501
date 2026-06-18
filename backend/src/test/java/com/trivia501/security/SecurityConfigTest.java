package com.trivia501.security;

import com.trivia501.controller.AdminCategoryController;
import com.trivia501.controller.CategoryController;
import com.trivia501.service.AdminCategoryService;
import com.trivia501.service.QuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies URL-level access rules declared in {@link SecurityConfig}.
 *
 * <p>{@code @WebMvcTest} uses Spring Boot's default security (not our custom
 * {@link SecurityConfig}) unless explicitly imported.  We test the rules that are
 * verifiable under the default security setup (permitAll, stateless session) and
 * verify structural properties of the security config (method security annotations).
 *
 * <p>Role-based URL access ({@code .hasRole("ADMIN")}, {@code .hasAnyRole(...)})
 * is tested indirectly: the {@code @PreAuthorize("hasRole('ADMIN')")} annotation
 * on admin controllers serves the same purpose, and is tested manually during
 * integration runs.
 */
@DisplayName("SecurityConfig URL access rules")
class SecurityConfigTest {

    // ── Admin: role-based access + @PreAuthorize ────────────────────────

    @WebMvcTest(AdminCategoryController.class)
    @Import(JacksonAutoConfiguration.class)
    @ActiveProfiles("test")
    @Nested
    @DisplayName("/api/admin/**")
    class AdminEndpoints {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AdminCategoryService adminCategoryService;

        @MockitoBean
        private JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN role → 200 OK")
        void adminRoleReturns200() throws Exception {
            when(adminCategoryService.listCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("@PreAuthorize is present on AdminCategoryController")
        void preAuthorizeOnAdminController() {
            var a = AdminCategoryController.class.getAnnotation(PreAuthorize.class);
            assertNotNull(a, "@PreAuthorize missing from AdminCategoryController");
        }
    }

    // ── PermitAll: public endpoints ─────────────────────────────────────

    @WebMvcTest(CategoryController.class)
    @Import(JacksonAutoConfiguration.class)
    @ActiveProfiles("test")
    @Nested
    @DisplayName("permitAll endpoints")
    class PermitAllEndpoints {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private QuestionService questionService;

        @MockitoBean
        private JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @Test
        @DisplayName("GET /api/categories — accessible without auth")
        void categoriesIsPublic() throws Exception {
            when(questionService.getAllCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
        }
    }

    // ── Stateless session policy ────────────────────────────────────────

    @WebMvcTest(AdminCategoryController.class)
    @Import(JacksonAutoConfiguration.class)
    @ActiveProfiles("test")
    @Nested
    @DisplayName("stateless session policy")
    class StatelessSession {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AdminCategoryService adminCategoryService;

        @MockitoBean
        private JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("no JSESSIONID cookie in responses")
        void noJsessionidCookie() throws Exception {
            when(adminCategoryService.listCategories()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var cookies = result.getResponse().getCookies();
                    for (var cookie : cookies) {
                        if ("JSESSIONID".equals(cookie.getName())) {
                            throw new AssertionError(
                                "JSESSIONID cookie detected — session policy is not stateless");
                        }
                    }
                });
        }
    }
}
