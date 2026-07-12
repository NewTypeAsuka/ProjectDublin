package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogRepository extends JpaRepository<Article, Long> {
}
