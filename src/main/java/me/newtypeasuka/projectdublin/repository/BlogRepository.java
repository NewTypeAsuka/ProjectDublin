package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<Article, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    List<Article> findAll();

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Article> findById(Long id);
}
