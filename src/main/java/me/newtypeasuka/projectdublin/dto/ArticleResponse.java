package me.newtypeasuka.projectdublin.dto;

import lombok.Getter;
import me.newtypeasuka.projectdublin.domain.Article;

@Getter
public class ArticleResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final Long authorId;
    private final String author;

    public ArticleResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.content = article.getContent();
        this.authorId = article.getAuthor().getId();
        this.author = article.getAuthor().getNickname();
    }

}
