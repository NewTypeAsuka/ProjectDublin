package me.newtypeasuka.projectdublin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;

import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
public class ArticleViewResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long authorId;
    private String author;
    // 게시글 작성자가 관리자인지 화면에 전달
    private boolean authorAdmin;
    private long viewCount;
    private long likeCount;
    private long commentCount;
    private boolean pinned;

    public ArticleViewResponse(Article article) {
        this(article, 0L, 0L);
    }

    public ArticleViewResponse(Article article, long likeCount) {
        this(article, likeCount, 0L);
    }

    public ArticleViewResponse(Article article, long likeCount, long commentCount) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
        this.authorId = article.getAuthor().getId();
        this.author = article.getAuthor().getNickname();
        this.authorAdmin = article.getAuthor().isAdmin();
        this.viewCount = article.getViewCount();
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.pinned = article.isPinned();
    }

}
