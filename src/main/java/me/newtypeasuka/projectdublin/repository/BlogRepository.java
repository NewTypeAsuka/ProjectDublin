package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Article;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<Article, Long> {

    long countByAuthorId(Long authorId); // 마이페이지에 표시할 작성 글 수 조회

    @EntityGraph(attributePaths = "author")
    @Query("SELECT article FROM Article article "
            + "ORDER BY article.pinned DESC, article.createdAt DESC, article.id DESC")
    List<Article> findAllPinnedFirst();

    @EntityGraph(attributePaths = "author")
    List<Article> findAllByPinnedTrueOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "author")
    List<Article> findAllByPinnedFalseOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("SELECT article FROM Article article "
            + "WHERE article.pinned = false "
            + "AND (article.createdAt < :createdAt "
            + "OR (article.createdAt = :createdAt AND article.id < :id)) "
            + "ORDER BY article.createdAt DESC, article.id DESC")
    List<Article> findUnpinnedAfterCursor(
            @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id,
            Pageable pageable
    );

    // 제목 또는 검색용 평문 본문에 검색어가 포함된 글을 고정 여부와 최신순으로 조회
    @EntityGraph(attributePaths = "author")
    @Query("SELECT article FROM Article article "
            + "WHERE (LOWER(article.title) LIKE :pattern ESCAPE '!' "
            + "OR LOWER(CAST(article.searchContent AS String)) LIKE :pattern ESCAPE '!') "
            + "ORDER BY article.pinned DESC, article.createdAt DESC, article.id DESC")
    List<Article> findSearchMatches(
            @Param("pattern") String pattern,
            Pageable pageable
    );

    // 검색 결과의 마지막 글 이후를 고정 여부·작성일시·ID 커서로 조회
    @EntityGraph(attributePaths = "author")
    @Query("SELECT article FROM Article article "
            + "WHERE (LOWER(article.title) LIKE :pattern ESCAPE '!' "
            + "OR LOWER(CAST(article.searchContent AS String)) LIKE :pattern ESCAPE '!') "
            + "AND ((article.pinned = :pinned "
            + "AND (article.createdAt < :createdAt "
            + "OR (article.createdAt = :createdAt AND article.id < :id))) "
            + "OR (:pinned = true AND article.pinned = false)) "
            + "ORDER BY article.pinned DESC, article.createdAt DESC, article.id DESC")
    List<Article> findSearchMatchesAfterCursor(
            @Param("pattern") String pattern,
            @Param("pinned") boolean pinned,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id,
            Pageable pageable
    );

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Article> findById(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article article "
            + "SET article.viewCount = article.viewCount + 1 "
            + "WHERE article.id = :id")
    int increaseViewCount(@Param("id") Long id);
}
