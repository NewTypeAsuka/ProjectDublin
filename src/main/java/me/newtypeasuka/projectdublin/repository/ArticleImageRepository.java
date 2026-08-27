package me.newtypeasuka.projectdublin.repository;

import jakarta.persistence.LockModeType;
import me.newtypeasuka.projectdublin.domain.ArticleImage;
import me.newtypeasuka.projectdublin.domain.ArticleImage.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, Long> {

    List<ArticleImage> findAllByArticleId(Long articleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from ArticleImage image where image.article.id = :articleId order by image.id")
    List<ArticleImage> findAllByArticleIdForUpdate(@Param("articleId") Long articleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from ArticleImage image where image.s3Key in :s3Keys order by image.id")
    List<ArticleImage> findAllByS3KeyInForUpdate(@Param("s3Keys") Collection<String> s3Keys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from ArticleImage image where image.id = :id")
    Optional<ArticleImage> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from ArticleImage image where image.s3Key = :s3Key")
    Optional<ArticleImage> findByS3KeyForUpdate(@Param("s3Key") String s3Key);

    // 보존 기간이 지난 미첨부 이미지를 작은 배치로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ArticleImage> findByStatusAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
            Status status,
            LocalDateTime cutoff,
            Pageable pageable
    );

    // 재시도 시각이 된 S3 삭제 대기 이미지를 작은 배치로 조회한다.
    List<ArticleImage> findByStatusAndNextDeleteAttemptAtLessThanEqualOrderByNextDeleteAttemptAtAscIdAsc(
            Status status,
            LocalDateTime nextDeleteAttemptAt,
            Pageable pageable
    );
}
