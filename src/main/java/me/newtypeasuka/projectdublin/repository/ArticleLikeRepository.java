package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleLikeRepository extends JpaRepository<ArticleLike, ArticleLikeId> {

    long countByIdArticleId(Long articleId);
}
