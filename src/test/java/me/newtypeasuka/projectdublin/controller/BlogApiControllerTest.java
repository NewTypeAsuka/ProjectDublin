package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.ArticleListViewResponse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
                        .with(csrf())
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"bi bi-eye\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-view-count\">2</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"bi bi-chat-dots\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-comment-count\">0</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"article-modified\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("alert('xss')"))));

        UpdateArticleRequest updateRequest = new UpdateArticleRequest(
                "Updated title",
                "<h2>Updated</h2><iframe src=\"//www.youtube.com/embed/video-id\"></iframe>"
        );

        mockMvc.perform(put("/api/articles/{id}", articleId)
                        .with(loginUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString(
                        "https://www.youtube.com/embed/video-id")));

        mockMvc.perform(get("/articles/{id}", articleId).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-modified\">수정됨</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Posted on"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"bi bi-heart-fill article-meta__like-icon\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"bi bi-heart article-like__icon\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<h1 id=\"article-title\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"h3 mb-0 text-break\">Updated title</h1>")));

        mockMvc.perform(get("/articles").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"article-card__modified\">수정됨</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"article-card__comment-count\">0</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"h5 mb-2 article-card__title\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("글 번호"))));
    }

    @DisplayName("게시글 제목은 40자까지 허용하고 작성 화면에 글자 수 표시를 제공한다")
    @Test
    void limitArticleTitleToFortyCharacters() throws Exception {
        String validTitle = "가".repeat(40);
        AddArticleRequest validRequest = new AddArticleRequest(
                validTitle,
                "<p>Content</p>"
        );

        String createResponse = mockMvc.perform(post("/api/articles")
                        .with(loginUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(validTitle))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long articleId = objectMapper.readTree(createResponse).get("id").asLong();

        UpdateArticleRequest invalidRequest = new UpdateArticleRequest(
                "가".repeat(41),
                "<p>Updated content</p>"
        );
        mockMvc.perform(put("/api/articles/{id}", articleId)
                        .with(loginUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertThat(blogRepository.findById(articleId).orElseThrow().getTitle())
                .isEqualTo(validTitle);

        mockMvc.perform(get("/new-article").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-max-length=\"40\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"title-length\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "0 / 40")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"title-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "제목은 40자 이내로 작성해주세요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"editor-actions\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/siteNavigation.js\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/articleForm.js\"")));
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
                        .with(csrf())
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

        mockMvc.perform(delete("/api/articles/{id}", articleId)
                        .with(loginUser())
                        .with(csrf()))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-view-count\">1</span>")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-view-count\">2</span>")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-view-count\">3</span>")));

        assertThat(blogRepository.findById(article.getId()).orElseThrow().getViewCount())
                .isEqualTo(3);
    }

    @DisplayName("최초 목록은 고정 글과 일반 글 10개를 보여주고 이후 일반 글을 커서로 조회한다")
    @Test
    void loadArticleFeedByCursor() throws Exception {
        List<Article> normalArticles = new ArrayList<>();
        for (int number = 1; number <= 23; number++) {
            normalArticles.add(blogRepository.save(Article.builder()
                    .author(user)
                    .title("Normal " + number)
                    .content("<p>Content " + number + "</p>")
                    .build()));
        }
        for (int number = 1; number <= 2; number++) {
            Article pinnedArticle = blogRepository.save(Article.builder()
                    .author(user)
                    .title("Pinned " + number)
                    .content("<p>Pinned content</p>")
                    .build());
            pinnedArticle.updatePinned(true);
            blogRepository.save(pinnedArticle);
        }

        var initialResult = mockMvc.perform(get("/articles").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists(
                        "articles",
                        "nextCursor",
                        "hasNextArticles"
                ))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"article-feed\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-has-next=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/articleFeed.js\"")))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ArticleListViewResponse> initialArticles =
                (List<ArticleListViewResponse>) initialResult
                        .getModelAndView()
                        .getModel()
                        .get("articles");
        assertThat(initialArticles).hasSize(12);
        assertThat(initialArticles.subList(0, 2))
                .allMatch(ArticleListViewResponse::isPinned);
        assertThat(initialArticles.subList(2, 12))
                .noneMatch(ArticleListViewResponse::isPinned);
        assertThat(initialArticles.subList(2, 12))
                .extracting(ArticleListViewResponse::getId)
                .containsExactly(
                        normalArticles.get(22).getId(),
                        normalArticles.get(21).getId(),
                        normalArticles.get(20).getId(),
                        normalArticles.get(19).getId(),
                        normalArticles.get(18).getId(),
                        normalArticles.get(17).getId(),
                        normalArticles.get(16).getId(),
                        normalArticles.get(15).getId(),
                        normalArticles.get(14).getId(),
                        normalArticles.get(13).getId()
                );

        String firstCursor = (String) initialResult
                .getModelAndView()
                .getModel()
                .get("nextCursor");
        assertThat(firstCursor).isNotBlank();
        assertThat(initialResult.getModelAndView().getModel().get("hasNextArticles"))
                .isEqualTo(true);

        String secondResponse = mockMvc.perform(get("/api/articles/feed")
                        .with(loginUser())
                        .param("cursor", firstCursor)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles.length()").value(10))
                .andExpect(jsonPath("$.articles[0].id")
                        .value(normalArticles.get(12).getId()))
                .andExpect(jsonPath("$.articles[9].id")
                        .value(normalArticles.get(3).getId()))
                .andExpect(jsonPath("$.articles[0].pinned").value(false))
                .andExpect(jsonPath("$.articles[0].likeCount").value(0))
                .andExpect(jsonPath("$.articles[0].commentCount").value(0))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondPage = objectMapper.readTree(secondResponse);
        String secondCursor = secondPage.get("nextCursor").asText();
        assertThat(secondCursor).isNotBlank().isNotEqualTo(firstCursor);

        String finalResponse = mockMvc.perform(get("/api/articles/feed")
                        .with(loginUser())
                        .param("cursor", secondCursor)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles.length()").value(3))
                .andExpect(jsonPath("$.articles[0].id")
                        .value(normalArticles.get(2).getId()))
                .andExpect(jsonPath("$.articles[2].id")
                        .value(normalArticles.get(0).getId()))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode finalPage = objectMapper.readTree(finalResponse);
        assertThat(finalPage.get("nextCursor").isNull()).isTrue();
    }

    @DisplayName("게시글 피드는 잘못된 커서와 과도한 조회 크기를 거부한다")
    @Test
    void rejectInvalidArticleFeedRequest() throws Exception {
        mockMvc.perform(get("/api/articles/feed")
                        .with(loginUser())
                        .param("cursor", "invalid-cursor"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/articles/feed")
                        .with(loginUser())
                        .param("cursor", "invalid-cursor")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
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
