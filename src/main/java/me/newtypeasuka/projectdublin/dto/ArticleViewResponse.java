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
    private String author;
    private long viewCount;
    private long likeCount;

    public ArticleViewResponse(Article article) {
        this(article, 0L);
    }

    public ArticleViewResponse(Article article, long likeCount) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.createdAt = article.getCreatedAt();
        this.author = article.getAuthor().getNickname();
        this.viewCount = article.getViewCount();
        this.likeCount = likeCount;
    }

}
