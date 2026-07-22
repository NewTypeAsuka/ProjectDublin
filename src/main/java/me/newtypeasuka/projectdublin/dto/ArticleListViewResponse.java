package me.newtypeasuka.projectdublin.dto;

import lombok.Getter;
import me.newtypeasuka.projectdublin.domain.Article;
import org.jsoup.Jsoup;

@Getter
public class ArticleListViewResponse {

    private final Long id;
    private final String title;
    private final String content;

    public ArticleListViewResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = createPreview(article.getContent());
    }

    private String createPreview(String html) {
        String text = Jsoup.parseBodyFragment(html).text();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
