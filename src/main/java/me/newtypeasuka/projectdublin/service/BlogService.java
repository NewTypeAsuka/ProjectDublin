package me.newtypeasuka.projectdublin.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor // lombok의 RequiredArgsConstructor 어노테이션으로 final이 붙거나 @NotNull 어노테이션이 붙은 생성자 자동 생성
@Service // 스프링의 Service 어노테이션으로 빈 등록
public class BlogService {

    private final BlogRepository blogRepository;

    // 블로그 글 작성
    public Article save(AddArticleRequest request) {
        return blogRepository.save(request.toEntity());
    }

    // 블로그 글 모두 조회
    public List<Article> findAll() {
        return blogRepository.findAll();
    }

    // 블로그 글 단건 조회
    public Article findById(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));
    }

    // 블로그 글 삭제
    public void delete(long id) {
        blogRepository.deleteById(id);
    }

    // 블로그 글 수정
    @Transactional // 트랜잭션 처리를 위해 @Transactional 어노테이션 사용
    public Article update(long id, UpdateArticleRequest request) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        article.update(request.getTitle(), request.getContent());

        return article;
    }

}
