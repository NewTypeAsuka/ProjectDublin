package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(name = "articles") // articles 테이블과 매핑
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // lombok으로 기본 생성자
@Getter // lombok으로 getter
@Entity // 엔티티로 지정
public class Article {

    @Id // id 필드를 기본키로 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본키를 자동으로 1씩 증가
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    @Lob
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "language", nullable = false, length = 30)
    private String language;

    @Builder // 빌더 패턴으로 객체 생성
    public Article(User author, String title, String content) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.viewCount = 0L;
        this.pinned = false;
        this.language = "korean";
    }

    public void update(String title, String content) { // 블로그 글 수정
        this.title = title;
        this.content = content;
    }

    @CreatedDate // 생성일시 자동 저장
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // 수정일시 자동 저장
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
