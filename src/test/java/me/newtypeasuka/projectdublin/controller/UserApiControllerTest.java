package me.newtypeasuka.projectdublin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.Comment;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class UserApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    CommentRepository commentRepository;

    User member;
    User other;
    Article article;
    Comment comment;

    @BeforeEach
    void setUp() {
        member = saveUser("member@example.com", "Member", "회원닉네임");
        other = saveUser("other@example.com", "Other", "다른닉네임");
        article = blogRepository.save(Article.builder()
                .author(member)
                .title("마이페이지 테스트 글")
                .content("<p>Content</p>")
                .searchContent("Content")
                .build());
        comment = commentRepository.save(
                new Comment(article, member, null, "회원 댓글")
        );
        commentRepository.save(new Comment(article, member, comment, "회원 답글"));
    }

    @DisplayName("로그인 사용자의 이메일과 닉네임 및 활동 수를 조회한다")
    @Test
    void getCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/me").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(member.getId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()))
                .andExpect(jsonPath("$.nickname").value(member.getNickname()))
                .andExpect(jsonPath("$.articleCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(2));
    }

    @DisplayName("로그인하지 않은 사용자는 마이페이지 API를 사용할 수 없다")
    @Test
    void rejectUnauthenticatedProfileRequest() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("CSRF 토큰으로 본인의 닉네임을 변경하고 기존 글과 댓글에도 반영한다")
    @Test
    void updateNicknameAndReflectExistingContent() throws Exception {
        mockMvc.perform(put("/api/users/me/nickname")
                        .with(loginUser(member))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nicknameBody("새회원닉")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(member.getId()))
                .andExpect(jsonPath("$.nickname").value("새회원닉"));

        assertThat(userRepository.findByEmail(member.getEmail()).orElseThrow().getNickname())
                .isEqualTo("새회원닉");

        mockMvc.perform(get("/api/articles/{id}", article.getId())
                        .with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("새회원닉"));
        mockMvc.perform(get("/api/articles/{id}/comments", article.getId())
                        .with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commenterNickname").value("새회원닉"))
                .andExpect(jsonPath("$[0].replies[0].commenterNickname").value("새회원닉"));
    }

    @DisplayName("닉네임 변경은 유효한 CSRF 토큰이 필요하다")
    @Test
    void requireCsrfForNicknameUpdate() throws Exception {
        mockMvc.perform(put("/api/users/me/nickname")
                        .with(loginUser(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nicknameBody("변경닉네임")))
                .andExpect(status().isForbidden());

        assertThat(member.getNickname()).isEqualTo("회원닉네임");
    }

    @DisplayName("잘못된 닉네임과 다른 사용자가 사용 중인 닉네임을 구분해 거절한다")
    @Test
    void rejectInvalidAndDuplicateNickname() throws Exception {
        mockMvc.perform(put("/api/users/me/nickname")
                        .with(loginUser(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nicknameBody("두자")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "닉네임은 3자 이상 12자 이하로 입력해주세요."
                ));

        mockMvc.perform(put("/api/users/me/nickname")
                        .with(loginUser(member))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nicknameBody(other.getNickname())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
    }

    @DisplayName("공통 내비게이션에 마이페이지 모달과 사용자 닉네임 DOM hook을 렌더링한다")
    @Test
    void renderProfileDialogFromCommonNavigation() throws Exception {
        MvcResult result = mockMvc.perform(get("/articles").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/userProfile.js\""
                )))
                .andReturn();

        Document document = Jsoup.parse(result.getResponse().getContentAsString());
        Element menu = document.selectFirst(".site-nav-dropdown__menu");
        Element languageToggle = document.selectFirst(
                ".site-nav-dropdown__menu [data-language-toggle]"
        );
        Element profileButton = document.selectFirst("#user-profile-open");
        Element dialog = document.selectFirst("dialog#user-profile-dialog");
        Element nickname = document.selectFirst(
                ".user-nickname[data-user-id='" + member.getId() + "']"
        );

        assertThat(menu).isNotNull();
        assertThat(menu.select(".site-nav-dropdown__item")).extracting(Element::text)
                .containsExactly("포스트", "마이페이지", "주식", "채팅");
        assertThat(menu.child(0).selectFirst("i").classNames())
                .containsExactly("bi", "bi-journal-richtext");
        assertThat(languageToggle).isNotNull();
        assertThat(languageToggle.hasAttr("disabled")).isTrue();
        assertThat(languageToggle.select("[data-language=ko]").text()).isEqualTo("한국어");
        assertThat(languageToggle.select("[data-language=ja]").text()).isEqualTo("日本語");
        assertThat(languageToggle.selectFirst("[data-language=ko] img").attr("src"))
                .isEqualTo("/img/koreaFlag.svg");
        assertThat(languageToggle.selectFirst("[data-language=ja] img").attr("src"))
                .isEqualTo("/img/japanFlag.svg");
        assertThat(profileButton).isNotNull();
        assertThat(profileButton.attr("aria-controls")).isEqualTo("user-profile-dialog");
        assertThat(dialog).isNotNull();
        assertThat(dialog.select("#user-profile-description")).isEmpty();
        assertThat(dialog.select(".site-profile-dialog__avatar")).isEmpty();
        assertThat(dialog.select("#user-profile-nickname-form")).hasSize(1);
        assertThat(dialog.selectFirst("#user-profile-cancel i").classNames())
                .containsExactly("bi", "bi-x-lg");
        assertThat(dialog.selectFirst("#user-profile-submit-icon").classNames())
                .containsExactly("bi", "bi-check2");
        assertThat(nickname).isNotNull();
        assertThat(nickname.text()).isEqualTo(member.getNickname());
    }

    private String nicknameBody(String nickname) throws Exception {
        return objectMapper.writeValueAsString(Map.of("nickname", nickname));
    }

    private User saveUser(String email, String name, String nickname) {
        return userRepository.save(User.builder()
                .email(email)
                .name(name)
                .nickname(nickname)
                .build());
    }

    private RequestPostProcessor loginUser(User user) {
        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", user.getEmail(), "name", user.getName()),
                "email"
        );
        return oauth2Login().oauth2User(oAuth2User);
    }
}
