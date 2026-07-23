package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(
        name = "article_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article_images_s3_key",
                columnNames = "s3_key"
        ),
        indexes = @Index(
                name = "idx_article_images_article_id",
                columnList = "article_id"
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class ArticleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, updatable = false)
    private Article article;

    @Column(name = "s3_key", nullable = false, updatable = false, length = 512)
    private String s3Key;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ArticleImage(Article article,
                        String s3Key,
                        String originalFilename,
                        String contentType,
                        long fileSize) {
        this.article = article;
        this.s3Key = s3Key;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }
}
