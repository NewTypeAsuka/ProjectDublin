package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLikeId;
import me.newtypeasuka.projectdublin.dto.ArticleLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArticleLikeRepository extends JpaRepository<ArticleLike, ArticleLikeId> {

    long countByIdArticleId(Long articleId);

    @Query("SELECT new me.newtypeasuka.projectdublin.dto.ArticleLikeCount("
            + "articleLike.article.id, COUNT(articleLike)) "
            + "FROM ArticleLike articleLike "
            + "WHERE articleLike.article.id IN :articleIds "
            + "GROUP BY articleLike.article.id")
    List<ArticleLikeCount> countByArticleIds(@Param("articleIds") Collection<Long> articleIds);
}
