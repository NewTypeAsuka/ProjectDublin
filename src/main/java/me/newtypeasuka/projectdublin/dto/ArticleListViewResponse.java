package me.newtypeasuka.projectdublin.dto;

import lombok.Getter;
import me.newtypeasuka.projectdublin.domain.Article;
import org.jsoup.Jsoup;

import java.time.LocalDateTime;

@Getter
public class ArticleListViewResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String author;
    private final long viewCount;
    private final long likeCount;
    private final long commentCount;
    private final boolean pinned;

    public ArticleListViewResponse(Article article) {
        this(article, 0L, 0L);
    }

    public ArticleListViewResponse(Article article, long likeCount) {
        this(article, likeCount, 0L);
    }

    public ArticleListViewResponse(Article article, long likeCount, long commentCount) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = createPreview(article.getContent());
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
        this.author = article.getAuthor().getNickname();
        this.viewCount = article.getViewCount();
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.pinned = article.isPinned();
    }

    private String createPreview(String html) {
        String text = Jsoup.parseBodyFragment(html).text();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
