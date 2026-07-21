package me.newtypeasuka.projectdublin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthSessionSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @DisplayName("OAuth 인증은 HTTP 세션에 저장되어 다음 요청에서도 유지된다")
    @Test
    void keepOAuthAuthenticationInHttpSession() throws Exception {
        MvcResult authenticatedRequest = mockMvc.perform(get("/articles")
                        .with(oauth2Login().attributes(attributes -> {
                            attributes.put("email", "user@gmail.com");
                            attributes.put("name", "테스트 사용자");
                        })))
                .andExpect(status().isOk())
                .andExpect(view().name("articleList"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) authenticatedRequest.getRequest().getSession(false);

        mockMvc.perform(get("/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("articleList"));
    }
}
