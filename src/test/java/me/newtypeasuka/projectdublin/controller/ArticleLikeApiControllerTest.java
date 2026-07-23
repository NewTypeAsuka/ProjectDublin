package me.newtypeasuka.projectdublin.controller;

import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLikeId;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
class ArticleLikeApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    ArticleLikeRepository articleLikeRepository;

    User author;
    User reader;
    Article article;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .email("author@example.com")
                .nickname("Author")
                .build());
        reader = userRepository.save(User.builder()
                .email("reader@example.com")
                .nickname("Reader")
                .build());
        article = blogRepository.save(Article.builder()
                .author(author)
                .title("Like test")
                .content("<p>Content</p>")
                .build());
    }

    @DisplayName("한 사용자는 한 글에 좋아요를 한 번만 누르고 취소할 수 있다")
    @Test
    void likeAndUnlikeArticle() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/likes";

        mockMvc.perform(get(endpoint).with(loginUser(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));

        mockMvc.perform(put(endpoint).with(loginUser(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(put(endpoint).with(loginUser(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(articleLikeRepository.count()).isEqualTo(1);
        assertThat(articleLikeRepository.existsById(
                new ArticleLikeId(author.getId(), article.getId()))).isTrue();

        mockMvc.perform(put(endpoint).with(loginUser(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));

        mockMvc.perform(delete(endpoint).with(loginUser(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(delete(endpoint).with(loginUser(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(articleLikeRepository.count()).isEqualTo(1);
    }

    @DisplayName("게시글 상세 화면에 전체 좋아요 수와 좋아요 버튼을 표시한다")
    @Test
    void renderLikeButton() throws Exception {
        articleLikeRepository.save(new ArticleLike(author, article));
        articleLikeRepository.save(new ArticleLike(reader, article));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(reader)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-like-count\">2</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"like-btn\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/article-like.js\"")));
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
