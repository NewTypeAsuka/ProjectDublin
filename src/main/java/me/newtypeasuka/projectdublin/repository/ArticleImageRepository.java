package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.ArticleImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, Long> {

    List<ArticleImage> findAllByArticleId(Long articleId);

    List<ArticleImage> findAllByS3KeyIn(Collection<String> s3Keys);
}
