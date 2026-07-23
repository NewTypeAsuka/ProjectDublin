package me.newtypeasuka.projectdublin.controller;

import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.repository.ArticleLikeRepository;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class ArticlePinApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    ArticleLikeRepository articleLikeRepository;

    User admin;
    User member;
    Article article;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .nickname("Admin")
                .role(1)
                .build());
        member = userRepository.save(User.builder()
                .email("member@example.com")
                .nickname("Member")
                .build());
        article = blogRepository.save(Article.builder()
                .author(admin)
                .title("Pinned article")
                .content("<p>Pinned content</p>")
                .build());
    }

    @DisplayName("관리자만 게시글을 고정하고 해제할 수 있다")
    @Test
    void onlyAdminCanPinAndUnpinArticle() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/pin";

        mockMvc.perform(put(endpoint).with(loginUser(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        assertThat(blogRepository.findById(article.getId()).orElseThrow().isPinned()).isTrue();

        mockMvc.perform(delete(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false));

        assertThat(blogRepository.findById(article.getId()).orElseThrow().isPinned()).isFalse();
    }

    @DisplayName("고정 글을 목록 최상단에 배치하고 글 메타 정보를 표시한다")
    @Test
    void showPinnedArticleFirstWithMetadata() throws Exception {
        Article newerArticle = blogRepository.save(Article.builder()
                .author(member)
                .title("Newer article")
                .content("<p>Newer content</p>")
                .build());
        articleLikeRepository.save(new ArticleLike(admin, article));
        articleLikeRepository.save(new ArticleLike(member, article));

        mockMvc.perform(put("/api/articles/{id}/pin", article.getId()).with(loginUser(admin)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/articles").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"pinned-corner\"")))
                .andExpect(content().string(containsString(
                        "by Admin · 조회수 0 · 좋아요 2")))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html.indexOf(article.getTitle()))
                .isLessThan(html.indexOf(newerArticle.getTitle()));
    }

    @DisplayName("상세 화면의 고정 버튼은 관리자에게만 표시된다")
    @Test
    void showPinButtonOnlyToAdmin() throws Exception {
        mockMvc.perform(put("/api/articles/{id}/pin", article.getId()).with(loginUser(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"pin-btn\"")))
                .andExpect(content().string(containsString("data-pinned=\"true\"")))
                .andExpect(content().string(containsString("id=\"pinned-corner\"")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"pin-btn\""))));
    }

    private RequestPostProcessor loginUser(User user) {
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", user.getEmail(), "name", user.getNickname()),
                "email"
        );
        return oauth2Login().oauth2User(oauth2User);
    }
}
