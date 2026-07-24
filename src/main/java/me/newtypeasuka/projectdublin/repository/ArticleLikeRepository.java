package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLike.ArticleLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArticleLikeRepository extends JpaRepository<ArticleLike, ArticleLikeId> {

    long countByIdArticleId(Long articleId);

    @Query("SELECT articleLike.article.id AS articleId, COUNT(articleLike) AS likeCount "
            + "FROM ArticleLike articleLike "
            + "WHERE articleLike.article.id IN :articleIds "
            + "GROUP BY articleLike.article.id")
    List<ArticleLikeCount> countByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    interface ArticleLikeCount {

        Long getArticleId();

        long getLikeCount();
    }
}
