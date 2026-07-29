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

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Article> findById(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article article "
            + "SET article.viewCount = article.viewCount + 1 "
            + "WHERE article.id = :id")
    int increaseViewCount(@Param("id") Long id);
}
