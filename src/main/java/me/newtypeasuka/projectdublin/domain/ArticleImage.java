package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(
        name = "article_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article_images_s3_key",
                columnNames = "s3_key"
        ),
        indexes = {
                @Index(
                        name = "idx_article_images_article_id",
                        columnList = "article_id"
                ),
                @Index(
                        name = "idx_article_images_uploader_status_created",
                        columnList = "uploader_id, status, created_at, id"
                ),
                @Index(
                        name = "idx_article_images_status_created",
                        columnList = "status, created_at, id"
                ),
                @Index(
                        name = "idx_article_images_delete_retry",
                        columnList = "status, next_delete_attempt_at, id"
                )
        }
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    // 게시글 저장 전 pending 이미지의 소유자와 연결 권한을 확인한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", updatable = false)
    private User uploader;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = StatusConverter.class)
    private Status status;

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

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_attempts", nullable = false)
    private int deleteAttempts;

    @Column(name = "next_delete_attempt_at")
    private LocalDateTime nextDeleteAttemptAt;

    @Column(name = "last_delete_error", length = 1000)
    private String lastDeleteError;

    @Builder
    public ArticleImage(Article article,
                        User uploader,
                        Status status,
                        String s3Key,
                        String originalFilename,
                        String contentType,
                        long fileSize) {
        this.article = article;
        this.uploader = uploader;
        this.status = status == null
                ? (article == null ? Status.PENDING : Status.ATTACHED)
                : status;
        this.s3Key = s3Key;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.deleteAttempts = 0;
    }

    // S3 업로드 전에 정리 가능한 pending 기록을 먼저 생성한다.
    public static ArticleImage pending(User uploader,
                                       String s3Key,
                                       String originalFilename,
                                       String contentType,
                                       long fileSize) {
        return ArticleImage.builder()
                .uploader(uploader)
                .status(Status.PENDING)
                .s3Key(s3Key)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileSize(fileSize)
                .build();
    }

    public void attach(Article article) {
        if (status != Status.PENDING || article == null) {
            throw new IllegalStateException("pending 이미지만 게시글에 연결할 수 있습니다");
        }
        this.article = article;
        this.status = Status.ATTACHED;
        this.deleteAttempts = 0;
        this.nextDeleteAttemptAt = null;
        this.lastDeleteError = null;
    }

    public void queueDeletion(LocalDateTime requestedAt) {
        if (requestedAt == null) {
            throw new IllegalArgumentException("삭제 요청 시각이 필요합니다");
        }
        this.article = null;
        this.status = Status.DELETE_PENDING;
        this.deleteAttempts = 0;
        this.nextDeleteAttemptAt = requestedAt;
        this.lastDeleteError = null;
    }

    public boolean isDeletionDue(LocalDateTime now) {
        return status == Status.DELETE_PENDING
                && nextDeleteAttemptAt != null
                && !nextDeleteAttemptAt.isAfter(now);
    }

    public void claimDeletion(LocalDateTime leaseExpiresAt) {
        if (status != Status.DELETE_PENDING) {
            throw new IllegalStateException("삭제 대기 이미지만 처리할 수 있습니다");
        }
        this.nextDeleteAttemptAt = leaseExpiresAt;
    }

    public void recordDeletionFailure(LocalDateTime nextAttemptAt, String error) {
        if (status != Status.DELETE_PENDING) {
            throw new IllegalStateException("삭제 대기 이미지만 실패를 기록할 수 있습니다");
        }
        this.deleteAttempts = deleteAttempts == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : deleteAttempts + 1;
        this.nextDeleteAttemptAt = nextAttemptAt;
        this.lastDeleteError = error;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isAttached() {
        return status == Status.ATTACHED;
    }

    public boolean isDeletePending() {
        return status == Status.DELETE_PENDING;
    }

    public enum Status {
        PENDING("pending"),
        ATTACHED("attached"),
        DELETE_PENDING("delete_pending");

        private final String databaseValue;

        Status(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public static Status fromDatabaseValue(String databaseValue) {
            for (Status status : values()) {
                if (status.databaseValue.equals(databaseValue)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("unsupported article image status: " + databaseValue);
        }
    }

    // enum을 article_images.status의 소문자 값으로 변환하여 저장한다.
    @Converter
    public static class StatusConverter implements AttributeConverter<Status, String> {

        @Override
        public String convertToDatabaseColumn(Status status) {
            return status == null ? null : status.databaseValue;
        }

        @Override
        public Status convertToEntityAttribute(String databaseValue) {
            return databaseValue == null ? null : Status.fromDatabaseValue(databaseValue);
        }
    }
}
