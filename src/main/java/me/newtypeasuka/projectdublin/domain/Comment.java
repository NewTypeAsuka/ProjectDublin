package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Table(
        name = "comments",
        indexes = {
                @Index(
                        name = "idx_comments_article_parent_created",
                        columnList = "article_id, parent_id, created_at, id"
                ),
                @Index(
                        name = "idx_comments_commenter_id",
                        columnList = "commenter_id"
                ),
                @Index(
                        name = "idx_comments_parent_id",
                        columnList = "parent_id"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class Comment {

    public static final int MAX_CONTENT_LENGTH = 1000;
    public static final String DELETED_CONTENT = "삭제된 댓글입니다";

    private static final int ROOT_DEPTH = 1;
    private static final int REPLY_DEPTH = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, updatable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commenter_id", nullable = false, updatable = false)
    private User commenter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private Comment parent;

    @Column(name = "depth", nullable = false, updatable = false)
    private int depth;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 일반 댓글 또는 한 단계 대댓글을 생성
    public Comment(Article article, User commenter, Comment parent, String content) {
        if (parent != null
                && (!parent.isRoot()
                || !Objects.equals(parent.getArticle().getId(), article.getId()))) {
            throw new IllegalArgumentException("대댓글은 같은 게시글의 일반 댓글에만 작성할 수 있습니다");
        }

        this.article = article;
        this.commenter = commenter;
        this.parent = parent;
        this.depth = parent == null ? ROOT_DEPTH : REPLY_DEPTH;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isRoot() {
        return depth == ROOT_DEPTH;
    }

    public boolean isReply() {
        return depth == REPLY_DEPTH;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
