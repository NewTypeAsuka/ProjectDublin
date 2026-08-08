package me.newtypeasuka.projectdublin.dto;

import lombok.Getter;
import me.newtypeasuka.projectdublin.domain.Article;
import org.jsoup.Jsoup;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ArticleListViewResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String author;
    // 게시글 작성자가 관리자인지 화면에 전달
    private final boolean authorAdmin;
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
        this.authorAdmin = article.getAuthor().isAdmin();
        this.viewCount = article.getViewCount();
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.pinned = article.isPinned();
    }

    private String createPreview(String html) {
        String text = Jsoup.parseBodyFragment(html).text();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    public record FeedResponse(
            List<ArticleListViewResponse> articles,
            String nextCursor,
            boolean hasNext
    ) {
    }
}
