package me.newtypeasuka.projectdublin.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor // lombok의 RequiredArgsConstructor 어노테이션으로 final이 붙거나 @NotNull 어노테이션이 붙은 생성자 자동 생성
@Service // 스프링의 Service 어노테이션으로 빈 등록
public class BlogService {

    private final BlogRepository blogRepository;

    // 블로그 글 작성
    public Article save(AddArticleRequest request, String userName) {
        return blogRepository.save(request.toEntity(userName));
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
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        authorizeArticleAuthor(article);
        blogRepository.delete(article);
    }

    // 블로그 글 수정
    @Transactional // 트랜잭션 처리를 위해 @Transactional 어노테이션 사용
    public Article update(long id, UpdateArticleRequest request) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        authorizeArticleAuthor(article);
        article.update(request.getTitle(), request.getContent());

        return article; // @Transactional 어노테이션을 사용하면, 엔티티를 조회한 후 변경된 값을 디비에 반환하지 않아도 JPA가 자동으로 1차 캐시를 통해 변경을 감지하고 이를 DB에 반영함
    }

    // 게시물을 작성한 유저인지 확인
    private static void authorizeArticleAuthor(Article article) {
        String userName = SecurityContextHolder.getContext().getAuthentication().getName();

        if(!article.getAuthor().equals(userName)) {
            throw new IllegalArgumentException("not authorized: " + userName);
        }
    }

}
