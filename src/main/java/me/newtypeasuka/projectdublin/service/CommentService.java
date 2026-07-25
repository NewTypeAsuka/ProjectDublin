package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.Comment;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentContentRequest;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentResponse;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(long articleId, String email) {
        findArticle(articleId);
        User currentUser = findUser(email);
        List<Comment> comments = commentRepository.findAllByArticleIdOldestFirst(articleId);

        Map<Long, List<Comment>> repliesByParentId = new LinkedHashMap<>();
        for (Comment comment : comments) {
            if (comment.isReply()) {
                repliesByParentId
                        .computeIfAbsent(comment.getParent().getId(), key -> new ArrayList<>())
                        .add(comment);
            }
        }

        return comments.stream()
                .filter(Comment::isRoot)
                .map(comment -> {
                    List<CommentResponse> replies = repliesByParentId
                            .getOrDefault(comment.getId(), List.of())
                            .stream()
                            .map(reply -> toResponse(reply, currentUser, List.of()))
                            .toList();
                    return toResponse(comment, currentUser, replies);
                })
                .toList();
    }

    public long getCommentCount(long articleId) {
        return commentRepository.countByArticleIdAndDeletedAtIsNull(articleId);
    }

    // 게시글 목록의 댓글 수를 한 번에 조회해 N+1 쿼리를 방지
    public Map<Long, Long> getCommentCounts(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }

        return commentRepository.countByArticleIds(articleIds).stream()
                .collect(Collectors.toMap(
                        commentCount -> commentCount.getArticleId(),
                        commentCount -> commentCount.getCommentCount()
                ));
    }

    // 게시글에 새로운 일반 댓글을 작성
    @Transactional
    public CommentResponse createComment(long articleId,
                                         CommentContentRequest request,
                                         String email) {
        Article article = findArticle(articleId);
        User commenter = findUser(email);
        Comment comment = commentRepository.save(
                new Comment(article, commenter, null, normalizeContent(request.content()))
        );

        return toResponse(comment, commenter, List.of());
    }

    // 일반 댓글에 한 단계 대댓글을 작성
    @Transactional
    public CommentResponse createReply(long articleId,
                                       long parentId,
                                       CommentContentRequest request,
                                       String email) {
        Article article = findArticle(articleId);
        User commenter = findUser(email);
        Comment parent = findCommentForUpdate(articleId, parentId);

        if (!parent.isRoot()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "대댓글에는 추가 답글을 작성할 수 없습니다"
            );
        }
        if (parent.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "삭제된 댓글에는 답글을 작성할 수 없습니다"
            );
        }

        Comment reply = commentRepository.save(
                new Comment(article, commenter, parent, normalizeContent(request.content()))
        );
        return toResponse(reply, commenter, List.of());
    }

    @Transactional
    public CommentResponse updateComment(long articleId,
                                         long commentId,
                                         CommentContentRequest request,
                                         String email) {
        User currentUser = findUser(email);
        Comment comment = findCommentForUpdate(articleId, commentId);

        if (comment.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "삭제된 댓글은 수정할 수 없습니다"
            );
        }
        if (!isOwner(comment, currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        comment.updateContent(normalizeContent(request.content()));
        commentRepository.flush();
        return toResponse(comment, currentUser, List.of());
    }

    @Transactional
    public void deleteComment(long articleId, long commentId, String email) {
        User currentUser = findUser(email);
        Comment comment = findCommentForUpdate(articleId, commentId);

        if (comment.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미 삭제된 댓글입니다"
            );
        }
        if (!isOwner(comment, currentUser) && !currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (comment.isRoot() && commentRepository.existsByParentId(comment.getId())) {
            comment.softDelete();
            return;
        }

        Comment deletedParent = null;
        // 소프트 삭제된 부모의 마지막 대댓글을 지우면 부모 댓글도 실제 삭제
        if (comment.isReply() && comment.getParent().isDeleted()) {
            deletedParent = findCommentForUpdate(articleId, comment.getParent().getId());
        }

        commentRepository.delete(comment);
        if (deletedParent != null) {
            commentRepository.flush();
            if (!commentRepository.existsByParentId(deletedParent.getId())) {
                commentRepository.delete(deletedParent);
            }
        }
    }

    private CommentResponse toResponse(Comment comment,
                                       User currentUser,
                                       List<CommentResponse> replies) {
        boolean owner = isOwner(comment, currentUser);
        boolean deleted = comment.isDeleted();

        return new CommentResponse(
                comment.getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getDepth(),
                comment.getCommenter().getId(),
                comment.getCommenter().getNickname(),
                deleted ? Comment.DELETED_CONTENT : comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                deleted,
                !deleted && owner,
                !deleted && (owner || currentUser.isAdmin()),
                List.copyOf(replies)
        );
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해주세요");
        }

        String normalized = content.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해주세요");
        }
        if (length > Comment.MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "댓글은 1000자 이하로 작성해주세요"
            );
        }
        return normalized;
    }

    private boolean isOwner(Comment comment, User user) {
        return comment.getCommenter().getId().equals(user.getId());
    }

    private Comment findCommentForUpdate(long articleId, long commentId) {
        return commentRepository.findByIdAndArticleIdForUpdate(commentId, articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Article findArticle(long articleId) {
        return blogRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
