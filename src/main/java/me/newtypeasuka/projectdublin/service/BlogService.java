package me.newtypeasuka.projectdublin.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RequiredArgsConstructor // lombok의 RequiredArgsConstructor 어노테이션으로 final이 붙거나 @NotNull 어노테이션이 붙은 생성자 자동 생성
@Service // 스프링의 Service 어노테이션으로 빈 등록
public class BlogService {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final ArticleContentSummarizer articleContentSanitizer;
    private final ArticleImageService articleImageService;

    // 블로그 글 작성
    @Transactional
    public Article save(AddArticleRequest request, String userName) {
        User author = findUserByEmail(userName);
        String sanitizedContent = articleContentSanitizer.sanitize(request.getContent());
        Article article = blogRepository.save(request.toEntity(author, sanitizedContent));
        articleImageService.synchronize(article);
        return article;
    }

    // 블로그 글 모두 조회
    public List<Article> findAll() {
        return blogRepository.findAllPinnedFirst();
    }

    // 블로그 글 단건 조회
    public Article findById(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));
    }

    // 게시글 상세 조회 시 조회수를 1 증가시킨 후 최신 게시글을 반환
    @Transactional
    public Article findByIdAndIncreaseViewCount(Long id) {
        if (blogRepository.increaseViewCount(id) == 0) {
            throw new IllegalArgumentException("not found: " + id);
        }

        return findById(id);
    }

    // 블로그 글 삭제
    @Transactional
    public void delete(long id) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        authorizeArticleAuthor(article, findCurrentUser());
        articleImageService.removeAllForArticle(article.getId());
        blogRepository.delete(article);
    }

    // 블로그 글 수정
    @Transactional // 트랜잭션 처리를 위해 @Transactional 어노테이션 사용
    public Article update(long id, UpdateArticleRequest request) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        authorizeArticleAuthor(article, findCurrentUser());
        String sanitizedContent = articleContentSanitizer.sanitize(request.getContent());
        article.update(request.getTitle(), sanitizedContent);
        articleImageService.synchronize(article);

        return article; // @Transactional 어노테이션을 사용하면, 엔티티를 조회한 후 변경된 값을 디비에 반환하지 않아도 JPA가 자동으로 1차 캐시를 통해 변경을 감지하고 이를 DB에 반영함
    }

    @Transactional
    public Article updatePinned(long id, boolean pinned, String email) {
        User currentUser = findUserByEmail(email);
        if (!currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin role required");
        }

        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        article.updatePinned(pinned);
        return article;
    }

    public boolean isAdmin(String email) {
        return findUserByEmail(email).isAdmin();
    }

    // 게시물을 작성한 유저인지 확인
    private void authorizeArticleAuthor(Article article, User currentUser) {
        if (!article.getAuthor().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("not authorized: " + currentUser.getEmail());
        }
    }

    private User findCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findUserByEmail(email);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + email));
    }

}
