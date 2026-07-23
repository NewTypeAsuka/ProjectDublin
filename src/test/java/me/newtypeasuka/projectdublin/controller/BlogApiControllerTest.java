package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.repository.ArticleImageRepository;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import me.newtypeasuka.projectdublin.service.S3ObjectUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BlogApiControllerTest {

    private static final String EMAIL = "writer@example.com";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ArticleImageRepository articleImageRepository;

    @Autowired
    S3ObjectUrlResolver urlResolver;

    @MockBean
    S3Client s3Client;

    User user;

    @BeforeEach
    void setUp() {
        articleImageRepository.deleteAll();
        blogRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.builder()
                .email(EMAIL)
                .nickname("Writer")
                .build());
    }

    @DisplayName("로그인 사용자 ID와 Summernote HTML로 글을 생성, 조회, 수정한다")
    @Test
    void createReadAndUpdateSummernoteArticle() throws Exception {
        AddArticleRequest createRequest = new AddArticleRequest(
                "Summernote title",
                "<p>Hello <strong>Summernote</strong></p><script>alert('xss')</script>"
        );

        String createResponse = mockMvc.perform(post("/api/articles")
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(user.getId()))
                .andExpect(jsonPath("$.content").value("<p>Hello <strong>Summernote</strong></p>"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long articleId = objectMapper.readTree(createResponse).get("id").asLong();
        Article savedArticle = blogRepository.findById(articleId).orElseThrow();
        assertThat(savedArticle.getAuthor().getId()).isEqualTo(user.getId());
        assertThat(savedArticle.getContent()).contains("<strong>Summernote</strong>");
        assertThat(savedArticle.getContent()).doesNotContain("<script");

        mockMvc.perform(get("/api/articles/{id}", articleId).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(savedArticle.getContent()))
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(get("/articles/{id}", articleId).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong>Summernote</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조회수 2")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("alert('xss')"))));

        UpdateArticleRequest updateRequest = new UpdateArticleRequest(
                "Updated title",
                "<h2>Updated</h2><iframe src=\"//www.youtube.com/embed/video-id\"></iframe>"
        );

        mockMvc.perform(put("/api/articles/{id}", articleId)
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString(
                        "https://www.youtube.com/embed/video-id")));
    }

    @DisplayName("S3 이미지를 게시글과 연결하고 게시글 삭제 후 S3에서도 제거한다")
    @Test
    void mapAndDeleteArticleImage() throws Exception {
        String key = "articles/%d/2026/07/image.png".formatted(user.getId());
        String encodedFilename = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("image.png".getBytes(StandardCharsets.UTF_8));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(9L)
                        .metadata(Map.of(
                                "uploader-id", user.getId().toString(),
                                "original-filename", encodedFilename
                        ))
                        .build()
        );
        AddArticleRequest createRequest = new AddArticleRequest(
                "Image title",
                "<p>Image content</p><img src=\"" + urlResolver.resolve(key) + "\">"
        );

        String createResponse = mockMvc.perform(post("/api/articles")
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long articleId = objectMapper.readTree(createResponse).get("id").asLong();

        assertThat(articleImageRepository.findAllByArticleId(articleId))
                .singleElement()
                .satisfies(image -> {
                    assertThat(image.getS3Key()).isEqualTo(key);
                    assertThat(image.getOriginalFilename()).isEqualTo("image.png");
                    assertThat(image.getContentType()).isEqualTo("image/png");
                    assertThat(image.getFileSize()).isEqualTo(9L);
                });

        mockMvc.perform(delete("/api/articles/{id}", articleId).with(loginUser()))
                .andExpect(status().isOk());

        assertThat(articleImageRepository.findAllByArticleId(articleId)).isEmpty();
        verify(s3Client).deleteObject(argThat(
                (DeleteObjectRequest request) -> request.key().equals(key)
        ));
    }

    @DisplayName("작성자가 같은 게시글을 반복 조회해도 매번 조회수가 증가한다")
    @Test
    void increaseViewCountOnEveryDetailView() throws Exception {
        Article article = blogRepository.save(Article.builder()
                .author(user)
                .title("View count")
                .content("<p>Content</p>")
                .build());

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조회수 1")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조회수 2")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조회수 3")));

        assertThat(blogRepository.findById(article.getId()).orElseThrow().getViewCount())
                .isEqualTo(3);
    }

    private RequestPostProcessor loginUser() {
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", EMAIL, "name", "Writer"),
                "email"
        );
        return oauth2Login().oauth2User(oauth2User);
    }
}
