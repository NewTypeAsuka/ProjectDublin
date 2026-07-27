package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.Comment;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentContentRequest;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentResponse;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
class CommentServiceTest {

    @Autowired
    CommentService commentService;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

    User admin;
    User member;
    User other;
    Article article;
    Article otherArticle;

    @BeforeEach
    void setUp() {
        admin = saveUser("admin@example.com", "Admin", 1);
        member = saveUser("member@example.com", "Member", 2);
        other = saveUser("other@example.com", "Other", 2);
        article = saveArticle(admin, "Comment test article");
        otherArticle = saveArticle(admin, "Other article");
    }

    @DisplayName("댓글과 대댓글을 모두 오래된 순서로 조회한다")
    @Test
    void getCommentsOldestFirst() {
        CommentResponse first = createComment(article, member, "첫 번째 댓글 !*$ 😀");
        CommentResponse firstReply = createReply(article, first, other, "첫 번째 답글");
        CommentResponse secondReply = createReply(article, first, member, "두 번째 답글");
        CommentResponse second = createComment(article, admin, "두 번째 댓글");

        List<CommentResponse> comments =
                commentService.getComments(article.getId(), member.getEmail());

        assertThat(comments).extracting(CommentResponse::id)
                .containsExactly(first.id(), second.id());
        assertThat(comments.get(0).replies()).extracting(CommentResponse::id)
                .containsExactly(firstReply.id(), secondReply.id());
        assertThat(comments.get(0).commenterId()).isEqualTo(member.getId());
        assertThat(comments.get(0).commenterNickname()).isEqualTo(member.getNickname());
        assertThat(comments.get(0).commenterAdmin()).isFalse();
        assertThat(comments.get(0).replies()).allMatch(reply -> !reply.commenterAdmin());
        assertThat(comments.get(1).commenterAdmin()).isTrue();
        assertThat(comments.get(0).content()).isEqualTo("첫 번째 댓글 !*$ 😀");
        assertThat(comments.get(0).createdAt()).isNotNull();
        assertThat(comments.get(0).editable()).isTrue();
        assertThat(comments.get(1).editable()).isFalse();
    }

    @DisplayName("대댓글에는 추가 답글을 작성할 수 없다")
    @Test
    void rejectReplyToReply() {
        CommentResponse root = createComment(article, member, "일반 댓글");
        CommentResponse reply = createReply(article, root, other, "대댓글");

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> createReply(article, reply, member, "대대댓글")
        );
        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> commentService.createReply(
                        otherArticle.getId(),
                        root.id(),
                        request("다른 게시글 대댓글"),
                        member.getEmail()
                )
        );
    }

    @DisplayName("대댓글이 있는 댓글은 소프트 삭제하고 원문을 반환하지 않는다")
    @Test
    void softDeleteRootWithReply() {
        CommentResponse root = createComment(article, member, "삭제할 원문");
        CommentResponse firstReply = createReply(article, root, other, "첫 번째 대댓글");
        CommentResponse lastReply = createReply(article, root, member, "마지막 대댓글");

        commentService.deleteComment(article.getId(), root.id(), member.getEmail());

        Comment deleted = commentRepository.findById(root.id()).orElseThrow();
        List<CommentResponse> comments =
                commentService.getComments(article.getId(), member.getEmail());

        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).deleted()).isTrue();
        assertThat(comments.get(0).content()).isEqualTo(Comment.DELETED_CONTENT);
        assertThat(comments.get(0).editable()).isFalse();
        assertThat(comments.get(0).deletable()).isFalse();
        assertThat(comments.get(0).replies()).extracting(CommentResponse::id)
                .containsExactly(firstReply.id(), lastReply.id());
        assertThat(commentService.getCommentCount(article.getId())).isEqualTo(2);

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> commentService.updateComment(
                        article.getId(),
                        root.id(),
                        request("수정 시도"),
                        member.getEmail()
                )
        );
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> commentService.createReply(
                        article.getId(),
                        root.id(),
                        request("새 답글"),
                        member.getEmail()
                )
        );

        commentService.deleteComment(article.getId(), firstReply.id(), other.getEmail());

        assertThat(commentRepository.existsById(firstReply.id())).isFalse();
        assertThat(commentRepository.existsById(lastReply.id())).isTrue();
        assertThat(commentRepository.existsById(root.id())).isTrue();
        assertThat(commentService.getCommentCount(article.getId())).isEqualTo(1);

        commentService.deleteComment(article.getId(), lastReply.id(), member.getEmail());

        assertThat(commentRepository.existsById(lastReply.id())).isFalse();
        assertThat(commentRepository.existsById(root.id())).isFalse();
        assertThat(commentService.getCommentCount(article.getId())).isZero();
        assertThat(commentService.getComments(article.getId(), member.getEmail())).isEmpty();
    }

    @DisplayName("작성자만 댓글을 수정하고 작성자 또는 관리자가 삭제할 수 있다")
    @Test
    void authorizeUpdateAndDelete() {
        CommentResponse memberComment = createComment(article, member, "회원 댓글");

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> commentService.updateComment(
                        article.getId(),
                        memberComment.id(),
                        request("다른 사용자 수정"),
                        other.getEmail()
                )
        );
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> commentService.deleteComment(
                        article.getId(),
                        memberComment.id(),
                        other.getEmail()
                )
        );

        CommentResponse updated = commentService.updateComment(
                article.getId(),
                memberComment.id(),
                request("작성자가 수정한 댓글"),
                member.getEmail()
        );
        assertThat(updated.content()).isEqualTo("작성자가 수정한 댓글");

        commentService.deleteComment(article.getId(), memberComment.id(), admin.getEmail());
        assertThat(commentRepository.existsById(memberComment.id())).isFalse();
    }

    @DisplayName("대댓글과 자식이 없는 댓글은 실제 삭제한다")
    @Test
    void hardDeleteReplyAndRootWithoutReplies() {
        CommentResponse root = createComment(article, member, "일반 댓글");
        CommentResponse reply = createReply(article, root, other, "삭제할 대댓글");

        commentService.deleteComment(article.getId(), reply.id(), other.getEmail());
        assertThat(commentRepository.existsById(reply.id())).isFalse();

        commentService.deleteComment(article.getId(), root.id(), member.getEmail());
        assertThat(commentRepository.existsById(root.id())).isFalse();
    }

    @DisplayName("댓글은 평문과 이모지를 보존하고 1000자를 초과할 수 없다")
    @Test
    void validateCommentContent() {
        String plainText = "<script>alert('x')</script> ! * $ 😀";
        CommentResponse response = createComment(article, member, "  " + plainText + "  ");

        assertThat(response.content()).isEqualTo(plainText);
        assertThat(createComment(article, member, "가".repeat(1000)).content())
                .hasSize(1000);

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> createComment(article, member, "가".repeat(1001))
        );
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> createComment(article, member, "   \n\t")
        );
    }

    private CommentResponse createComment(Article target, User commenter, String content) {
        return commentService.createComment(
                target.getId(),
                request(content),
                commenter.getEmail()
        );
    }

    private CommentResponse createReply(Article target,
                                        CommentResponse parent,
                                        User commenter,
                                        String content) {
        return commentService.createReply(
                target.getId(),
                parent.id(),
                request(content),
                commenter.getEmail()
        );
    }

    private CommentContentRequest request(String content) {
        return new CommentContentRequest(content);
    }

    private User saveUser(String email, String nickname, int role) {
        return userRepository.save(User.builder()
                .email(email)
                .nickname(nickname)
                .role(role)
                .build());
    }

    private Article saveArticle(User author, String title) {
        return blogRepository.save(Article.builder()
                .author(author)
                .title(title)
                .content("<p>Content</p>")
                .build());
    }

    private void assertStatus(HttpStatus expectedStatus, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(expectedStatus)
                );
    }
}
