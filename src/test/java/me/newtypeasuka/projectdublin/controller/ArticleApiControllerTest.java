package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import me.newtypeasuka.projectdublin.config.LocaleConfig;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

    @Autowired
    EntityManager entityManager;

    @Autowired
    @Qualifier("oAuth2AuthenticationSuccessHandler")
    AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    User admin;
    User member;
    Article article;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .name("Admin")
                .nickname("관리자닉")
                .role(1)
                .build());
        member = userRepository.save(User.builder()
                .email("member@example.com")
                .name("Member")
                .nickname("회원닉네임")
                .build());
        article = blogRepository.save(Article.builder()
                .author(admin)
                .title("Article API test")
                .content("<p>Content</p>")
                .searchContent("Content")
                .build());
    }

    @DisplayName("Google 이름과 필수 닉네임을 서로 다른 컬럼에 저장한다")
    @Test
    void mapUserNameAndRequiredNickname() {
        User user = userRepository.save(User.builder()
                .email("with-nickname@example.com")
                .name("Old Google Name")
                .nickname("공개별명")
                .build());
        user.updateName("New Google Name");
        userRepository.flush();
        Long userId = user.getId();
        entityManager.clear();

        User savedUser = userRepository.findById(userId).orElseThrow();

        assertThat(savedUser.getName()).isEqualTo("New Google Name");
        assertThat(savedUser.getNickname()).isEqualTo("공개별명");
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
                        .with(loginUser(member))
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

    @DisplayName("Google 로그인 화면에 활성화된 한국어와 일본어 전환 토글을 렌더링한다")
    @Test
    void renderLanguageToggleOnLoginPage() throws Exception {
        MvcResult result = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauthLogin"))
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());
        Element languageToggle = document.selectFirst("[data-language-toggle]");

        assertThat(languageToggle).isNotNull();
        assertThat(languageToggle.hasAttr("disabled")).isFalse();
        assertThat(document.select("script[src=/js/languageToggle.js]")).hasSize(1);
        assertThat(languageToggle.select(".language-toggle__option"))
                .extracting(Element::text)
                .containsExactly("한국어", "日本語");
        assertThat(languageToggle.select(".language-toggle__option.is-active").text())
                .isEqualTo("한국어");
        assertThat(languageToggle.selectFirst("[data-language=ko] img").attr("src"))
                .isEqualTo("/img/koreaFlag.svg");
        assertThat(languageToggle.selectFirst("[data-language=ja] img").attr("src"))
                .isEqualTo("/img/japanFlag.svg");
        assertThat(document.selectFirst(".google-login-link__title").text())
                .isEqualTo("Google로 계속하기");
    }

    @DisplayName("일본어 선택을 쿠키에 저장하고 로그인과 닉네임 설정 화면에 유지한다")
    @Test
    void persistJapaneseAcrossAuthenticationPages() throws Exception {
        MvcResult loginResult = mockMvc.perform(get("/login")
                        .param(LocaleConfig.LANGUAGE_PARAMETER, "ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauthLogin"))
                .andReturn();

        Cookie languageCookie = loginResult.getResponse().getCookie(
                LocaleConfig.LANGUAGE_COOKIE
        );
        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Document loginDocument = Jsoup.parse(
                loginResult.getResponse().getContentAsString()
        );
        Element loginAction = loginDocument.selectFirst(".google-login-link__title");

        assertThat(languageCookie).isNotNull();
        assertThat(languageCookie.getValue()).isEqualTo("ja");
        assertThat(languageCookie.isHttpOnly()).isTrue();
        assertThat(languageCookie.getMaxAge()).isEqualTo(365 * 24 * 60 * 60);
        assertThat(setCookie)
                .contains("Path=/")
                .contains("SameSite=Lax");
        assertThat(loginDocument.selectFirst("html").attr("lang")).isEqualTo("ja");
        assertThat(loginDocument.title()).isEqualTo("ログイン · NewTypeBlog");
        assertThat(loginDocument.selectFirst("meta[property=og:locale]").attr("content"))
                .isEqualTo("ja_JP");
        assertThat(loginDocument.select(".language-toggle__option.is-active").text())
                .isEqualTo("日本語");
        assertThat(loginAction.text()).isEqualTo("Googleで続行");

        MvcResult signupResult = mockMvc.perform(get("/signup/nickname")
                        .with(loginOAuthUser(
                                "japanese-new-user@example.com",
                                "Japanese New User"
                        ))
                        .cookie(languageCookie))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andReturn();

        Document signupDocument = Jsoup.parse(
                signupResult.getResponse().getContentAsString()
        );
        Element signupAction = signupDocument.selectFirst("#nickname-submit span");
        Element switchAction = signupDocument.selectFirst(".account-switch");

        assertThat(signupDocument.selectFirst("html").attr("lang")).isEqualTo("ja");
        assertThat(signupDocument.title()).isEqualTo("ニックネーム設定 · NewTypeBlog");
        assertThat(signupDocument.select(".language-toggle__option.is-active").text())
                .isEqualTo("日本語");
        assertThat(signupDocument.selectFirst("#nickname").attr("placeholder"))
                .isEqualTo("3～12文字で入力してください。");
        assertThat(signupDocument.selectFirst("#nickname-signup-form")
                .attr("data-guide-valid"))
                .isEqualTo("使用可能な長さです。重複は登録時に確認します。");
        assertThat(signupAction.text()).isEqualTo("登録");
        assertThat(signupAction.text().codePointCount(0, signupAction.text().length()))
                .isEqualTo(2);
        assertThat(switchAction.text()).isEqualTo("別のGoogleアカウントでログイン");
    }

    @DisplayName("일본어 닉네임 설정 화면은 서버 검증 오류도 일본어로 표시한다")
    @Test
    void renderJapaneseNicknameValidationErrors() throws Exception {
        Cookie languageCookie = new Cookie(LocaleConfig.LANGUAGE_COOKIE, "ja");

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser("japanese-short@example.com", "Short User"))
                        .with(csrf())
                        .cookie(languageCookie)
                        .param("nickname", "二字"))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"))
                .andExpect(content().string(containsString(
                        "ニックネームは3文字以上12文字以下で入力してください。"
                )));

        userRepository.saveAndFlush(User.builder()
                .email("japanese-nickname-owner@example.com")
                .name("Nickname Owner")
                .nickname("TakenNick")
                .build());

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser(
                                "japanese-duplicate@example.com",
                                "Duplicate User"
                        ))
                        .with(csrf())
                        .cookie(languageCookie)
                        .param("nickname", "takennick"))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"))
                .andExpect(content().string(containsString(
                        "すでに使用されているニックネームです。"
                )));
    }

    @DisplayName("일본어 선택 시 게시글 목록의 정적·동적 문구를 일본어로 렌더링한다")
    @Test
    void renderJapaneseArticleList() throws Exception {
        MvcResult result = mockMvc.perform(get("/articles")
                        .with(loginUser(member))
                        .param(LocaleConfig.LANGUAGE_PARAMETER, "ja"))
                .andExpect(status().isOk())
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());
        Element languageToggle = document.selectFirst(
                ".site-nav-dropdown__menu [data-language-toggle]"
        );
        Element messageConfig = document.selectFirst("#article-list-messages");

        assertThat(document.selectFirst("html").attr("lang")).isEqualTo("ja");
        assertThat(document.title()).isEqualTo("記事 · NewTypeBlog");
        assertThat(document.selectFirst(".article-list-toolbar__title").text())
                .isEqualTo("記事");
        assertThat(document.selectFirst("#article-list-search-input")
                .attr("placeholder"))
                .isEqualTo("記事を検索（タイトル＋本文）");
        assertThat(document.text()).contains(article.getTitle());
        assertThat(languageToggle).isNotNull();
        assertThat(languageToggle.hasAttr("disabled")).isFalse();
        assertThat(languageToggle.attr("data-current-language")).isEqualTo("ja");
        assertThat(languageToggle.select(".language-toggle__option.is-active").text())
                .isEqualTo("日本語");
        assertThat(document.select("script[src=/js/languageToggle.js]")).hasSize(1);
        assertThat(messageConfig).isNotNull();
        assertThat(messageConfig.attr("data-load-error"))
                .isEqualTo("記事を読み込めませんでした。もう一度お試しください。");
        assertThat(messageConfig.attr("data-admin")).isEqualTo("管理者");
    }

    @DisplayName("일본어 선택 시 게시글 상세와 댓글의 정적·동적 문구를 일본어로 렌더링한다")
    @Test
    void renderJapaneseArticleDetailAndComments() throws Exception {
        MvcResult result = mockMvc.perform(get("/articles/{id}", article.getId())
                        .with(loginUser(member))
                        .param(LocaleConfig.LANGUAGE_PARAMETER, "ja"))
                .andExpect(status().isOk())
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());
        Element messageConfig = document.selectFirst("#article-detail-messages");
        Element languageToggle = document.selectFirst("[data-language-toggle]");

        assertThat(document.selectFirst("html").attr("lang")).isEqualTo("ja");
        assertThat(document.selectFirst(".article-back-link").text())
                .isEqualTo("すべての記事");
        assertThat(document.selectFirst("#like-btn > span").text()).isEqualTo("いいね");
        assertThat(document.selectFirst("#comment-heading > span").text())
                .isEqualTo("コメント");
        assertThat(document.selectFirst("#comment-content").attr("placeholder"))
                .isEqualTo("コメントを入力してください。");
        assertThat(document.selectFirst("body").attr("data-language-change-confirm"))
                .contains("入力中のコメントまたは返信");
        assertThat(languageToggle.hasAttr("disabled")).isFalse();
        assertThat(languageToggle.attr("data-current-language")).isEqualTo("ja");
        assertThat(messageConfig).isNotNull();
        assertThat(messageConfig.attr("data-comment-reply")).isEqualTo("返信");
        assertThat(messageConfig.attr("data-comment-delete-confirm"))
                .isEqualTo("コメントを削除しますか？");
        assertThat(messageConfig.attr("data-comment-validation-length"))
                .isEqualTo("コメントは{0}文字以下で入力してください。");
        assertThat(messageConfig.attr("data-like-error"))
                .startsWith("いいねを変更できませんでした。");
    }

    @DisplayName("일본어 선택 시 글쓰기·글수정 화면과 Summernote를 일본어로 렌더링한다")
    @Test
    void renderJapaneseArticleFormsAndSummernote() throws Exception {
        MvcResult createResult = mockMvc.perform(get("/new-article")
                        .with(loginUser(member))
                        .param(LocaleConfig.LANGUAGE_PARAMETER, "ja"))
                .andExpect(status().isOk())
                .andReturn();

        Document createDocument = Jsoup.parse(
                createResult.getResponse().getContentAsString()
        );
        Element createMessageConfig = createDocument.selectFirst(
                "#article-form-messages"
        );

        assertThat(createDocument.selectFirst("html").attr("lang")).isEqualTo("ja");
        assertThat(createDocument.title()).isEqualTo("新規投稿 · NewTypeBlog");
        assertThat(createDocument.selectFirst(".editor-intro__title").text())
                .isEqualTo("新規投稿");
        assertThat(createDocument.selectFirst("#title").attr("placeholder"))
                .isEqualTo("タイトルを入力してください。");
        assertThat(createDocument.selectFirst("#create-btn span").text())
                .isEqualTo("投稿");
        assertThat(createDocument.selectFirst("body")
                .attr("data-language-change-dirty"))
                .isEqualTo("false");
        assertThat(createMessageConfig.attr("data-editor-language"))
                .isEqualTo("ja-JP");
        assertThat(createMessageConfig.attr("data-title-limit"))
                .isEqualTo("タイトルは{0}文字以内で入力してください。");
        assertThat(createMessageConfig.attr("data-image-uploading-remaining"))
                .isEqualTo("画像をアップロードしています（残り{0}件）。");
        assertThat(createMessageConfig.attr("data-image-complete"))
                .isEqualTo("画像のアップロードが完了しました。");
        assertThat(createDocument.select("script[src$=summernote-ja-JP.min.js]"))
                .hasSize(1);
        assertThat(createDocument.select("script[src$=summernote-ko-KR.min.js]"))
                .isEmpty();
        assertThat(createDocument.select("script[src=/js/languageToggle.js]"))
                .hasSize(1);

        MvcResult editResult = mockMvc.perform(get("/new-article")
                        .param("id", article.getId().toString())
                        .with(loginUser(admin))
                        .cookie(new Cookie(LocaleConfig.LANGUAGE_COOKIE, "ja")))
                .andExpect(status().isOk())
                .andReturn();
        Document editDocument = Jsoup.parse(
                editResult.getResponse().getContentAsString()
        );

        assertThat(editDocument.title()).isEqualTo("記事編集 · NewTypeBlog");
        assertThat(editDocument.selectFirst(".editor-intro__title").text())
                .isEqualTo("記事編集");
        assertThat(editDocument.selectFirst("#modify-btn span").text())
                .isEqualTo("更新");
        assertThat(editDocument.selectFirst("#title").attr("value"))
                .isEqualTo(article.getTitle());
    }

    @DisplayName("지원하지 않는 언어 요청은 기본 한국어를 유지한다")
    @Test
    void ignoreUnsupportedLanguageParameter() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")
                        .param(LocaleConfig.LANGUAGE_PARAMETER, "en"))
                .andExpect(status().isOk())
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getCookie(LocaleConfig.LANGUAGE_COOKIE)).isNull();
        assertThat(document.selectFirst("html").attr("lang")).isEqualTo("ko");
        assertThat(document.title()).isEqualTo("로그인 · NewTypeBlog");
        assertThat(document.select(".language-toggle__option.is-active").text())
                .isEqualTo("한국어");
    }

    @DisplayName("Google 로그인 성공 후 기존 사용자는 목록, 신규 사용자는 닉네임 설정으로 이동한다")
    @Test
    void redirectOAuthLoginByRegistrationStatus() throws Exception {
        MockHttpServletRequest registeredRequest = new MockHttpServletRequest();
        MockHttpServletResponse registeredResponse = new MockHttpServletResponse();
        oAuth2AuthenticationSuccessHandler.onAuthenticationSuccess(
                registeredRequest,
                registeredResponse,
                oauthAuthentication(admin.getEmail(), admin.getName())
        );

        MockHttpServletRequest newUserRequest = new MockHttpServletRequest();
        MockHttpServletResponse newUserResponse = new MockHttpServletResponse();
        oAuth2AuthenticationSuccessHandler.onAuthenticationSuccess(
                newUserRequest,
                newUserResponse,
                oauthAuthentication("new-user@example.com", "New Google User")
        );

        assertThat(registeredResponse.getRedirectedUrl()).isEqualTo("/articles");
        assertThat(newUserResponse.getRedirectedUrl()).isEqualTo("/signup/nickname");
        assertThat(userRepository.findByEmail("new-user@example.com")).isEmpty();
    }

    @DisplayName("최초 로그인 사용자는 닉네임 설정 화면과 로그아웃 외의 기능을 사용할 수 없다")
    @Test
    void requireNicknameBeforeUsingService() throws Exception {
        RequestPostProcessor newUser = loginOAuthUser(
                "new-user@example.com",
                "New Google User"
        );

        mockMvc.perform(get("/signup/nickname").with(newUser))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(content().string(containsString("id=\"nickname-signup-form\"")))
                .andExpect(content().string(containsString("minlength=\"3\"")))
                .andExpect(content().string(containsString("maxlength=\"12\"")))
                .andExpect(content().string(containsString("0/12")))
                .andExpect(content().string(containsString("data-language-toggle")))
                .andExpect(content().string(containsString("src=\"/img/koreaFlag.svg\"")))
                .andExpect(content().string(containsString("src=\"/img/japanFlag.svg\"")))
                .andExpect(content().string(containsString(
                        "src=\"/js/nicknameSignup.js\""
                )))
                .andExpect(content().string(containsString("가입 완료하기")))
                .andExpect(content().string(containsString(
                        "다른 Google 계정으로 로그인"
                )));

        mockMvc.perform(get("/articles").with(newUser))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signup/nickname"));

        mockMvc.perform(get("/api/articles/{articleId}/comments", article.getId())
                        .with(newUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/logout").with(newUser).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @DisplayName("최초 로그인 사용자는 3~12자의 닉네임으로 가입을 완료한다")
    @Test
    void completeRegistrationWithNickname() throws Exception {
        String email = "new-user@example.com";

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser(email, "New Google User"))
                        .with(csrf())
                        .param("nickname", "  새사용자닉  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/articles"));

        User registeredUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(registeredUser.getName()).isEqualTo("New Google User");
        assertThat(registeredUser.getNickname()).isEqualTo("새사용자닉");
        assertThat(registeredUser.getRole()).isEqualTo(2);

        mockMvc.perform(get("/articles").with(loginUser(registeredUser)))
                .andExpect(status().isOk());
    }

    @DisplayName("닉네임은 공백을 제외한 3자 이상 12자 이하만 허용한다")
    @Test
    void rejectInvalidNicknameLength() throws Exception {
        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser("short@example.com", "Short User"))
                        .with(csrf())
                        .param("nickname", "두자"))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"));

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser("long@example.com", "Long User"))
                        .with(csrf())
                        .param("nickname", "가".repeat(13)))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"));

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser("blank@example.com", "Blank User"))
                        .with(csrf())
                        .param("nickname", "     "))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"));

        assertThat(userRepository.findByEmail("short@example.com")).isEmpty();
        assertThat(userRepository.findByEmail("long@example.com")).isEmpty();
        assertThat(userRepository.findByEmail("blank@example.com")).isEmpty();
    }

    @DisplayName("닉네임은 영문 대소문자를 구분하지 않고 중복을 거절한다")
    @Test
    void rejectDuplicateNickname() throws Exception {
        userRepository.saveAndFlush(User.builder()
                .email("nickname-owner@example.com")
                .name("Nickname Owner")
                .nickname("AlphaNick")
                .build());

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser("duplicate@example.com", "Duplicate User"))
                        .with(csrf())
                        .param("nickname", "alphanick"))
                .andExpect(status().isOk())
                .andExpect(view().name("nicknameSignup"))
                .andExpect(model().attributeHasFieldErrors("nicknameForm", "nickname"))
                .andExpect(content().string(containsString("이미 사용 중인 닉네임입니다.")));

        assertThat(userRepository.findByEmail("duplicate@example.com")).isEmpty();
    }

    @DisplayName("닉네임 가입 요청에는 유효한 CSRF 토큰이 필요하다")
    @Test
    void requireCsrfForNicknameSignup() throws Exception {
        String email = "no-csrf@example.com";

        mockMvc.perform(post("/signup/nickname")
                        .with(loginOAuthUser(email, "No Csrf User"))
                        .param("nickname", "새사용자"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmail(email)).isEmpty();
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
                .andExpect(jsonPath("$.commenterNickname").value(admin.getNickname()))
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
                .andExpect(jsonPath("$[0].content").value("삭제된 댓글입니다."))
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

        mockMvc.perform(get("/js/articleComment.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "modifiedMarker.className = 'comment-item__modified-marker'")))
                .andExpect(content().string(not(containsString(" · 수정됨"))));
    }

    private long responseId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String commentBody(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("content", content));
    }

    private RequestPostProcessor loginUser(User user) {
        return loginOAuthUser(user.getEmail(), user.getName());
    }

    private RequestPostProcessor loginOAuthUser(String email, String name) {
        return oauth2Login().oauth2User(oAuth2User(email, name));
    }

    private OAuth2AuthenticationToken oauthAuthentication(String email, String name) {
        DefaultOAuth2User oAuth2User = oAuth2User(email, name);
        return new OAuth2AuthenticationToken(
                oAuth2User,
                oAuth2User.getAuthorities(),
                "google"
        );
    }

    private DefaultOAuth2User oAuth2User(String email, String name) {
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", email, "name", name),
                "email"
        );
        return oauth2User;
    }
}
