package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.Comment;
import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLike.ArticleLikeId;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.ImageUploadResponse;
import me.newtypeasuka.projectdublin.repository.ArticleLikeRepository;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import me.newtypeasuka.projectdublin.service.ArticleImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class ArticleApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ArticleImageService articleImageService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    ArticleLikeRepository articleLikeRepository;

    @Autowired
    CommentRepository commentRepository;

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
                .title("Article API test")
                .content("<p>Content</p>")
                .build());
    }

    @DisplayName("로그인 사용자가 Summernote 이미지를 업로드하면 공개 URL을 반환한다")
    @Test
    void uploadArticleImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "image.png",
                "image/png",
                new byte[]{0x01}
        );
        when(articleImageService.upload(any(), any()))
                .thenReturn(new ImageUploadResponse(
                        "https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/"
                                + "articles/2026/07/image.png"
                ));

        mockMvc.perform(multipart("/api/articles/images")
                        .file(image)
                        .with(oauth2Login()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(
                        "https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/"
                                + "articles/2026/07/image.png"
                ));
    }

    @DisplayName("로그인하지 않은 사용자의 이미지 업로드를 거절한다")
    @Test
    void rejectUnauthenticatedUpload() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "image.png",
                "image/png",
                new byte[]{0x01}
        );

        mockMvc.perform(multipart("/api/articles/images").file(image))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("한 사용자는 한 글에 좋아요를 한 번만 누르고 취소할 수 있다")
    @Test
    void likeAndUnlikeArticle() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/likes";

        mockMvc.perform(get(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));

        mockMvc.perform(put(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(put(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(articleLikeRepository.count()).isEqualTo(1);
        assertThat(articleLikeRepository.existsById(
                new ArticleLikeId(admin.getId(), article.getId()))).isTrue();

        mockMvc.perform(put(endpoint).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));

        mockMvc.perform(delete(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(delete(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(articleLikeRepository.count()).isEqualTo(1);
    }

    @DisplayName("게시글 상세 화면에 전체 좋아요 수와 좋아요 버튼을 표시한다")
    @Test
    void renderLikeButton() throws Exception {
        articleLikeRepository.save(new ArticleLike(admin, article));
        articleLikeRepository.save(new ArticleLike(member, article));
        Comment comment = commentRepository.save(
                new Comment(article, admin, null, "일반 댓글")
        );
        commentRepository.save(new Comment(article, member, comment, "대댓글"));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"article-like-count\">2</span>")))
                .andExpect(content().string(containsString("id=\"like-btn\"")))
                .andExpect(content().string(containsString("class=\"bi bi-eye\"")))
                .andExpect(content().string(containsString(
                        "class=\"bi bi-heart-fill article-meta__like-icon\"")))
                .andExpect(content().string(containsString("class=\"bi bi-chat-dots\"")))
                .andExpect(content().string(containsString(
                        "id=\"article-comment-count\">2</span>")))
                .andExpect(content().string(not(containsString("Posted on"))))
                .andExpect(content().string(containsString(
                        "src=\"/js/articleLike.js\"")));
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

    @DisplayName("댓글 API에서 댓글과 대댓글을 작성, 수정, 조회하고 관리자가 삭제한다")
    @Test
    void manageComments() throws Exception {
        String commentsEndpoint = "/api/articles/" + article.getId() + "/comments";

        MvcResult createdComment = mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("회원 댓글 !*$ 😀")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.commenterId").value(member.getId()))
                .andExpect(jsonPath("$.commenterNickname").value(member.getNickname()))
                .andExpect(jsonPath("$.content").value("회원 댓글 !*$ 😀"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.deletable").value(true))
                .andReturn();
        long commentId = responseId(createdComment);

        MvcResult createdReply = mockMvc.perform(post(
                                commentsEndpoint + "/{commentId}/replies",
                                commentId
                        )
                        .with(loginUser(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("관리자 대댓글")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId))
                .andExpect(jsonPath("$.depth").value(2))
                .andReturn();
        long replyId = responseId(createdReply);

        mockMvc.perform(put(commentsEndpoint + "/{commentId}", commentId)
                        .with(loginUser(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("수정된 회원 댓글")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 회원 댓글"));

        mockMvc.perform(get(commentsEndpoint).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(commentId))
                .andExpect(jsonPath("$[0].replies[0].id").value(replyId))
                .andExpect(jsonPath("$[0].replies[0].content").value("관리자 대댓글"));

        mockMvc.perform(delete(commentsEndpoint + "/{commentId}", commentId)
                        .with(loginUser(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(commentsEndpoint).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").value("삭제된 댓글입니다"))
                .andExpect(jsonPath("$[0].replies[0].id").value(replyId));
    }

    @DisplayName("잘못된 댓글 내용과 미인증 댓글 요청을 거절한다")
    @Test
    void rejectInvalidOrUnauthenticatedComment() throws Exception {
        String commentsEndpoint = "/api/articles/" + article.getId() + "/comments";

        mockMvc.perform(post(commentsEndpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("로그인하지 않은 댓글")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("   ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("가".repeat(1001))))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("게시글 상세 화면에 댓글 영역과 댓글 기능 스크립트를 표시한다")
    @Test
    void renderCommentSection() throws Exception {
        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"article-comments\"")))
                .andExpect(content().string(containsString("id=\"comment-form\"")))
                .andExpect(content().string(containsString(
                        "src=\"/js/articleComment.js\"")));
    }

    private long responseId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String commentBody(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("content", content));
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
