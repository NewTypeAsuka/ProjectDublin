package me.newtypeasuka.projectdublin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;

@NoArgsConstructor // lombok의 NoArgsConstructor 어노테이션으로 기본 생성자 자동 생성
@AllArgsConstructor // lombok의 AllArgsConstructor 어노테이션으로 모든 필드를 매개변수로 받는 생성자 자동 생성
@Getter // lombok의 Getter 어노테이션으로 getter 메서드 자동 생성
public class AddArticleRequest {

    private String title;
    private String content;

    public Article toEntity(String author) { // 생성자를 사용해 객체 생성
        return Article.builder()
                .title(title)
                .content(content)
                .author(author)
                .build();
    }

}
