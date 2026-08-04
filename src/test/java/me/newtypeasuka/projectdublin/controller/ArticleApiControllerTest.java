package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
                .searchContent("Content")
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
                        .with(oauth2Login())
                        .with(csrf()))
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

        mockMvc.perform(multipart("/api/articles/images")
                        .file(image)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("CSRF 토큰이 없거나 올바르지 않은 상태 변경 요청을 거절한다")
    @Test
    void rejectArticleMutationWithoutValidCsrfToken() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/likes";

        mockMvc.perform(put(endpoint).with(loginUser(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(endpoint)
                        .with(loginUser(member))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        assertThat(articleLikeRepository.count()).isZero();

        // 프론트엔드와 동일하게 CSRF 토큰을 요청 헤더로 보내면 상태 변경을 허용한다.
        mockMvc.perform(put(endpoint)
                        .with(loginUser(member))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));
    }

    @DisplayName("화면에 CSRF 토큰과 POST 로그아웃 폼을 제공한다")
    @Test
    void renderCsrfTokenAndPostLogoutForm() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/articles/{id}", article.getId()).with(loginUser(member))
                )
                .andExpect(status().isOk())
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());
        Element tokenMeta = document.selectFirst("meta[name=_csrf]");
        Element headerMeta = document.selectFirst("meta[name=_csrf_header]");
        Element logoutForm = document.selectFirst("form[action=/logout][method=post]");

        assertThat(tokenMeta).isNotNull();
        assertThat(tokenMeta.attr("content")).isNotBlank();
        assertThat(headerMeta).isNotNull();
        assertThat(headerMeta.attr("content")).isEqualTo("X-CSRF-TOKEN");
        assertThat(document.select("script[src=/js/csrf.js]")).hasSize(1);
        assertThat(logoutForm).isNotNull();
        assertThat(logoutForm.select("input[name=_csrf]").attr("value")).isNotBlank();
        assertThat(document.select("a[href=/logout]")).isEmpty();
    }

    @DisplayName("로그아웃은 유효한 CSRF 토큰이 포함된 POST 요청만 처리한다")
    @Test
    void requireCsrfTokenForLogout() throws Exception {
        mockMvc.perform(post("/logout").with(loginUser(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/logout")
                        .with(loginUser(member))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @DisplayName("Google OAuth 인증 요청을 세션에 저장하고 직렬화 쿠키를 생성하지 않는다")
    @Test
    void storeOAuthAuthorizationRequestInSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        OAuth2AuthorizationRequest authorizationRequest = (OAuth2AuthorizationRequest) session.getAttribute(
                HttpSessionOAuth2AuthorizationRequestRepository.class.getName() + ".AUTHORIZATION_REQUEST"
        );

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getState()).isNotBlank();
        assertThat(result.getResponse().getCookie("oauth2_auth_request")).isNull();
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("state=");
    }

    @DisplayName("H2 웹 콘솔은 애플리케이션에 노출하지 않는다")
    @Test
    void doNotExposeH2Console() throws Exception {
        mockMvc.perform(get("/h2-console/").with(loginUser(member)))
                .andExpect(status().isNotFound());
    }

    @DisplayName("한 사용자는 한 글에 좋아요를 한 번만 누르고 취소할 수 있다")
    @Test
    void likeAndUnlikeArticle() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/likes";

        mockMvc.perform(get(endpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));

        mockMvc.perform(put(endpoint).with(loginUser(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(put(endpoint).with(loginUser(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(articleLikeRepository.count()).isEqualTo(1);
        assertThat(articleLikeRepository.existsById(
                new ArticleLikeId(admin.getId(), article.getId()))).isTrue();

        mockMvc.perform(put(endpoint).with(loginUser(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(2));

        mockMvc.perform(delete(endpoint).with(loginUser(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(delete(endpoint).with(loginUser(admin)).with(csrf()))
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
                        "src=\"/js/articleLike.js?v=2\"")));
    }

    @DisplayName("관리자만 게시글을 고정하고 해제할 수 있다")
    @Test
    void onlyAdminCanPinAndUnpinArticle() throws Exception {
        String endpoint = "/api/articles/" + article.getId() + "/pin";

        mockMvc.perform(put(endpoint).with(loginUser(member)).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(endpoint).with(loginUser(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        assertThat(blogRepository.findById(article.getId()).orElseThrow().isPinned()).isTrue();

        mockMvc.perform(delete(endpoint).with(loginUser(admin)).with(csrf()))
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
                .searchContent("Newer content")
                .build());
        articleLikeRepository.save(new ArticleLike(admin, article));
        articleLikeRepository.save(new ArticleLike(member, article));
        Comment comment = commentRepository.save(
                new Comment(article, admin, null, "목록의 일반 댓글")
        );
        commentRepository.save(new Comment(article, member, comment, "목록의 대댓글"));

        mockMvc.perform(put("/api/articles/{id}/pin", article.getId())
                        .with(loginUser(admin))
                        .with(csrf()))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/articles").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "class=\"bi bi-pin-angle-fill article-card__pin\"")))
                .andExpect(content().string(containsString(
                        "class=\"bi bi-eye\"")))
                .andExpect(content().string(containsString(
                        "class=\"bi bi-heart-fill article-card__like-icon\"")))
                .andExpect(content().string(containsString(
                        "class=\"article-card__like-count\">2</span>")))
                .andExpect(content().string(containsString(
                        "class=\"bi bi-chat-dots\"")))
                .andExpect(content().string(containsString(
                        "class=\"article-card__comment-count\">2</span>")))
                .andExpect(content().string(containsString(
                        "src=\"/js/siteNavigation.js\"")))
                .andExpect(content().string(not(containsString("글 번호"))))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html.indexOf(article.getTitle()))
                .isLessThan(html.indexOf(newerArticle.getTitle()));

        Document document = Jsoup.parse(html);
        Element adminArticleLink = document.selectFirst(
                "a[href='/articles/" + article.getId() + "']"
        );
        Element memberArticleLink = document.selectFirst(
                "a[href='/articles/" + newerArticle.getId() + "']"
        );
        assertThat(adminArticleLink).isNotNull();
        assertThat(memberArticleLink).isNotNull();
        assertThat(adminArticleLink.select(".author-admin-badge")).hasSize(1);
        Element adminBadge = adminArticleLink.selectFirst(".author-admin-badge");
        assertThat(adminBadge).isNotNull();
        assertThat(adminBadge.classNames()).contains("bi", "bi-patch-check-fill");
        assertThat(adminBadge.previousElementSibling().text()).isEqualTo(admin.getNickname());
        assertThat(memberArticleLink.select(".author-admin-badge")).isEmpty();
    }

    @DisplayName("게시글 상세 화면에서 관리자 작성자에게만 관리자 표시를 붙인다")
    @Test
    void showAdminBadgeOnlyForAdminArticleAuthor() throws Exception {
        Article memberArticle = blogRepository.save(Article.builder()
                .author(member)
                .title("Member article")
                .content("<p>Member content</p>")
                .searchContent("Member content")
                .build());

        MvcResult adminArticleResult = mockMvc.perform(
                        get("/articles/{id}", article.getId()).with(loginUser(member))
                )
                .andExpect(status().isOk())
                .andReturn();
        MvcResult memberArticleResult = mockMvc.perform(
                        get("/articles/{id}", memberArticle.getId()).with(loginUser(member))
                )
                .andExpect(status().isOk())
                .andReturn();

        Document adminArticleDocument =
                Jsoup.parse(adminArticleResult.getResponse().getContentAsString());
        Document memberArticleDocument =
                Jsoup.parse(memberArticleResult.getResponse().getContentAsString());

        assertThat(adminArticleDocument.select(".article-meta .author-admin-badge"))
                .hasSize(1);
        Element adminBadge =
                adminArticleDocument.selectFirst(".article-meta .author-admin-badge");
        assertThat(adminBadge).isNotNull();
        assertThat(adminBadge.classNames()).contains("bi", "bi-patch-check-fill");
        assertThat(adminBadge.previousElementSibling().text()).isEqualTo(admin.getNickname());
        assertThat(memberArticleDocument.select(".article-meta .author-admin-badge"))
                .isEmpty();
    }

    @DisplayName("게시글 작성자와 관리자는 수정·삭제할 수 있고 다른 사용자는 접근할 수 없다")
    @Test
    void authorizeArticleManagement() throws Exception {
        Article memberArticle = blogRepository.save(Article.builder()
                .author(member)
                .title("Member article")
                .content("<p>Member content</p>")
                .searchContent("Member content")
                .build());

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"edit-btn\""))))
                .andExpect(content().string(not(containsString("id=\"delete-btn\""))));

        mockMvc.perform(get("/articles/{id}", memberArticle.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"edit-btn\"")))
                .andExpect(content().string(containsString(
                        "href=\"/new-article?id=" + memberArticle.getId() + "\"")))
                .andExpect(content().string(containsString("id=\"delete-btn\"")));

        mockMvc.perform(get("/articles/{id}", memberArticle.getId()).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"edit-btn\"")))
                .andExpect(content().string(containsString("id=\"delete-btn\"")));

        mockMvc.perform(get("/new-article")
                        .param("id", article.getId().toString())
                        .with(loginUser(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/new-article")
                        .param("id", memberArticle.getId().toString())
                        .with(loginUser(admin)))
                .andExpect(status().isOk());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "Managed by admin",
                "content", "<p>Updated by admin</p>"
        ));
        mockMvc.perform(put("/api/articles/{id}", article.getId())
                        .with(loginUser(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/articles/{id}", memberArticle.getId())
                        .with(loginUser(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Managed by admin"));

        mockMvc.perform(delete("/api/articles/{id}", article.getId())
                        .with(loginUser(member))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/articles/{id}", memberArticle.getId())
                        .with(loginUser(admin))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(blogRepository.findById(memberArticle.getId())).isEmpty();
    }

    @DisplayName("상세 화면의 고정 버튼은 관리자에게만 표시된다")
    @Test
    void showPinButtonOnlyToAdmin() throws Exception {
        mockMvc.perform(put("/api/articles/{id}/pin", article.getId())
                        .with(loginUser(admin))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<section class=\"card border-0 shadow-sm article-detail-card\"")))
                .andExpect(content().string(containsString("id=\"pin-btn\"")))
                .andExpect(content().string(containsString("data-pinned=\"true\"")))
                .andExpect(content().string(containsString("id=\"pin-icon\"")))
                .andExpect(content().string(containsString("bi-pin-angle-fill")))
                .andExpect(content().string(containsString(
                        "id=\"pin-label\">해제</span>")))
                .andExpect(content().string(containsString(
                        "id=\"article-pinned-marker\"")));

        mockMvc.perform(get("/articles/{id}", article.getId()).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"pin-btn\""))));
    }

    @DisplayName("댓글 API에서 관리자가 다른 사용자의 댓글을 수정·삭제할 수 있다")
    @Test
    void manageComments() throws Exception {
        String commentsEndpoint = "/api/articles/" + article.getId() + "/comments";

        MvcResult createdComment = mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("회원 댓글 !*$ 😀")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depth").value(1))
                .andExpect(jsonPath("$.commenterId").value(member.getId()))
                .andExpect(jsonPath("$.commenterNickname").value(member.getNickname()))
                .andExpect(jsonPath("$.commenterAdmin").value(false))
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("관리자 대댓글")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId))
                .andExpect(jsonPath("$.depth").value(2))
                .andExpect(jsonPath("$.commenterAdmin").value(true))
                .andReturn();
        long replyId = responseId(createdReply);

        mockMvc.perform(put(commentsEndpoint + "/{commentId}", commentId)
                        .with(loginUser(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("관리자가 수정한 회원 댓글")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("관리자가 수정한 회원 댓글"));

        mockMvc.perform(get(commentsEndpoint).with(loginUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].editable").value(true))
                .andExpect(jsonPath("$[0].deletable").value(true))
                .andExpect(jsonPath("$[0].replies[0].editable").value(true))
                .andExpect(jsonPath("$[0].replies[0].deletable").value(true));

        mockMvc.perform(get(commentsEndpoint).with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(commentId))
                .andExpect(jsonPath("$[0].replies[0].id").value(replyId))
                .andExpect(jsonPath("$[0].commenterAdmin").value(false))
                .andExpect(jsonPath("$[0].replies[0].commenterAdmin").value(true))
                .andExpect(jsonPath("$[0].editable").value(true))
                .andExpect(jsonPath("$[0].replies[0].editable").value(false))
                .andExpect(jsonPath("$[0].replies[0].content").value("관리자 대댓글"));

        mockMvc.perform(delete(commentsEndpoint + "/{commentId}", commentId)
                        .with(loginUser(admin))
                        .with(csrf()))
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("로그인하지 않은 댓글")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("   ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(commentsEndpoint)
                        .with(loginUser(member))
                        .with(csrf())
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
                        "src=\"/js/articleComment.js\"")))
                .andExpect(content().string(containsString(
                        "src=\"/js/articleDelete.js\"")));
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
