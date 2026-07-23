package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Article;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<Article, Long> {

    @EntityGraph(attributePaths = "author")
    @Query("SELECT article FROM Article article "
            + "ORDER BY article.pinned DESC, article.createdAt DESC, article.id DESC")
    List<Article> findAllPinnedFirst();

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Article> findById(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Article article "
            + "SET article.viewCount = article.viewCount + 1 "
            + "WHERE article.id = :id")
    int increaseViewCount(@Param("id") Long id);
}
