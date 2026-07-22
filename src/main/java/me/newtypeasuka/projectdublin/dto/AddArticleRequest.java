package me.newtypeasuka.projectdublin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.User;

@NoArgsConstructor // lombok의 NoArgsConstructor 어노테이션으로 기본 생성자 자동 생성
@AllArgsConstructor // lombok의 AllArgsConstructor 어노테이션으로 모든 필드를 매개변수로 받는 생성자 자동 생성
@Getter // lombok의 Getter 어노테이션으로 getter 메서드 자동 생성
public class AddArticleRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String content;

    public Article toEntity(User author, String sanitizedContent) { // 생성자를 사용해 객체 생성
        return Article.builder()
                .title(title)
                .content(sanitizedContent)
                .author(author)
                .build();
    }

}
