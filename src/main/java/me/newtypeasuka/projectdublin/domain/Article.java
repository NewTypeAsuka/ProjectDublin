package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity // 엔티티로 지정
@Getter // lombok의 Getter 어노테이션으로 getter 메서드 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED) // lombok의 NoArgsConstructor 어노테이션으로 기본 생성자 자동 생성(접근제한자는 protected)
public class Article {

    @Id // id 필드를 기본키로 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본키를 자동으로 1씩 증가
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "title", nullable = false) // title 필드를 컬럼으로 지정, null 허용하지 않음
    private String title;

    @Column(name = "content", nullable = false) // content 필드를 컬럼으로 지정, null 허용하지 않음
    private String content;

    @Builder // 빌더 패턴으로 객체 생성
    public Article(String title, String content) {
        this.title = title;
        this.content = content;
    }

}
