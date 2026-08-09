package me.newtypeasuka.projectdublin.service;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Size;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.Comment;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.UserDto.NicknameResponse;
import me.newtypeasuka.projectdublin.dto.UserDto.ProfileResponse;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import me.newtypeasuka.projectdublin.service.UserService.InvalidNicknameException;
import me.newtypeasuka.projectdublin.service.UserService.NicknameAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    CommentRepository commentRepository;

    User member;
    User other;
    Article article;

    @BeforeEach
    void setUp() {
        member = saveUser("member@example.com", "Member", "회원닉네임");
        other = saveUser("other@example.com", "Other", "다른닉네임");
        article = saveArticle(member, "회원 글");
    }

    @DisplayName("마이페이지에서 작성한 글과 삭제되지 않은 댓글·답글 수를 조회한다")
    @Test
    void getProfileWithActivityCounts() {
        saveArticle(member, "회원의 두 번째 글");
        saveArticle(other, "다른 사용자 글");
        Comment root = commentRepository.save(
                new Comment(article, member, null, "일반 댓글")
        );
        commentRepository.save(new Comment(article, member, root, "답글"));
        Comment deleted = commentRepository.save(
                new Comment(article, member, null, "삭제된 댓글")
        );
        deleted.softDelete();
        commentRepository.save(new Comment(article, other, root, "다른 사용자 답글"));
        commentRepository.flush();

        ProfileResponse profile = userService.getProfile(member.getEmail());

        assertThat(profile.userId()).isEqualTo(member.getId());
        assertThat(profile.email()).isEqualTo(member.getEmail());
        assertThat(profile.nickname()).isEqualTo(member.getNickname());
        assertThat(profile.articleCount()).isEqualTo(2);
        assertThat(profile.commentCount()).isEqualTo(2);
    }

    @DisplayName("닉네임 앞뒤 공백을 제거해 변경하고 같은 닉네임 요청은 그대로 성공한다")
    @Test
    void updateNicknameIdempotently() {
        NicknameResponse updated = userService.updateNickname(
                member.getEmail(),
                "  새닉네임  "
        );
        NicknameResponse unchanged = userService.updateNickname(
                member.getEmail(),
                "새닉네임"
        );

        assertThat(updated.userId()).isEqualTo(member.getId());
        assertThat(updated.nickname()).isEqualTo("새닉네임");
        assertThat(unchanged.nickname()).isEqualTo("새닉네임");
        assertThat(member.getNickname()).isEqualTo("새닉네임");
    }

    @DisplayName("현재 사용자를 제외하고 영문 대소문자 구분 없이 닉네임 중복을 거절한다")
    @Test
    void rejectDuplicateNickname() {
        User alpha = saveUser("alpha@example.com", "Alpha", "AlphaNick");

        assertThatThrownBy(() -> userService.updateNickname(
                member.getEmail(),
                "alphanick"
        )).isInstanceOf(NicknameAlreadyExistsException.class);

        assertThat(userService.updateNickname(
                alpha.getEmail(),
                "AlphaNick"
        ).nickname()).isEqualTo("AlphaNick");
    }

    @DisplayName("서비스에서도 닉네임을 3자 이상 12자 이하로 제한한다")
    @Test
    void validateNicknameInService() {
        assertThatThrownBy(() -> userService.updateNickname(member.getEmail(), "두자"))
                .isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> userService.updateNickname(
                member.getEmail(),
                "가".repeat(13)
        )).isInstanceOf(InvalidNicknameException.class);
        assertThatThrownBy(() -> userService.updateNickname(
                member.getEmail(),
                "닉네임\n변경"
        )).isInstanceOf(InvalidNicknameException.class);
    }

    @DisplayName("닉네임 컬럼은 255자로 매핑하고 서비스 허용 길이는 12자로 제한한다")
    @Test
    void separateNicknameColumnAndBusinessLengths() throws NoSuchFieldException {
        Column column = User.class.getDeclaredField("nickname").getAnnotation(Column.class);
        Size size = User.class.getDeclaredField("nickname").getAnnotation(Size.class);

        assertThat(column.length()).isEqualTo(255);
        assertThat(size.max()).isEqualTo(12);
    }

    private User saveUser(String email, String name, String nickname) {
        return userRepository.save(User.builder()
                .email(email)
                .name(name)
                .nickname(nickname)
                .build());
    }

    private Article saveArticle(User author, String title) {
        return blogRepository.save(Article.builder()
                .author(author)
                .title(title)
                .content("<p>Content</p>")
                .searchContent("Content")
                .build());
    }
}
