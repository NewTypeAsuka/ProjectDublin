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

    public static final int MAX_TITLE_LENGTH = 40;

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

    // HTML 태그와 속성을 제외하고 본문 검색에 사용하는 평문
    @Column(name = "search_content", nullable = false)
    @Lob
    private String searchContent;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "language", nullable = false, length = 30)
    @Convert(converter = LanguageConverter.class)
    private Language language;

    @Builder // 빌더 패턴으로 객체 생성
    public Article(User author,
                   String title,
                   String content,
                   String searchContent,
                   Language language) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.searchContent = searchContent;
        this.viewCount = 0L;
        this.pinned = false;
        this.language = language == null ? Language.KOREAN : language;
    }

    public void update(String title,
                       String content,
                       String searchContent,
                       Language language) { // 블로그 글 수정 시 본문 언어도 다시 반영
        this.title = title;
        this.content = content;
        this.searchContent = searchContent;
        this.language = language;
    }

    public void updatePinned(boolean pinned) {
        this.pinned = pinned;
    }

    @CreatedDate // 생성일시 자동 저장
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // 수정일시 자동 저장
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 게시글 목록의 표시 언어를 구분하는 articles.language 값
    public enum Language {
        KOREAN("korean"),
        JAPANESE("japanese"),
        OTHER("other"),
        UNDETERMINED("undetermined");

        private final String databaseValue;

        Language(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String getDatabaseValue() {
            return databaseValue;
        }

        public static Language fromDatabaseValue(String databaseValue) {
            for (Language language : values()) {
                if (language.databaseValue.equals(databaseValue)) {
                    return language;
                }
            }
            throw new IllegalArgumentException("unsupported article language: " + databaseValue);
        }
    }

    // enum을 기존 소문자 language 컬럼 값으로 변환하여 저장
    @Converter
    public static class LanguageConverter implements AttributeConverter<Language, String> {

        @Override
        public String convertToDatabaseColumn(Language language) {
            return language == null ? null : language.getDatabaseValue();
        }

        @Override
        public Language convertToEntityAttribute(String databaseValue) {
            return databaseValue == null ? null : Language.fromDatabaseValue(databaseValue);
        }
    }

}
