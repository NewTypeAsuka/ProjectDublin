package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.ArticleResponse;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.service.BlogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RequiredArgsConstructor
@RestController // 스프링의 RestController 어노테이션으로 REST API 컨트롤러 빈 등록(json 형태로 반환)
public class BlogApiController {

    private final BlogService blogService;

    // 블로그 글 작성 API
    @PostMapping("/api/articles")
    public ResponseEntity<ArticleResponse> addArticle(@Valid @RequestBody AddArticleRequest request, Principal principal) {
        Article savedArticle = blogService.save(request, principal.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ArticleResponse(savedArticle));
    }

    // 블로그 글 모두 조회 API
    @GetMapping("/api/articles")
    public ResponseEntity<List<ArticleResponse>> findAllArticles() {
        List<ArticleResponse> articles = blogService.findAll()
                .stream()
                .map(ArticleResponse::new)
                .toList();

        return ResponseEntity.ok()
                .body(articles);
    }

    // 블로그 글 단건 조회 API
    @GetMapping("/api/articles/{id}")
    public ResponseEntity<ArticleResponse> findArticle(@PathVariable long id) { // @PathVariable 어노테이션으로 URL 경로에서 id 값을 가져옴
        Article article = blogService.findByIdAndIncreaseViewCount(id);

        return ResponseEntity.ok()
                .body(new ArticleResponse(article));
    }

    // 블로그 글 삭제 API
    @DeleteMapping("/api/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable long id) { // @PathVariable 어노테이션으로 URL 경로에서 id 값을 가져옴
        blogService.delete(id);

        return ResponseEntity.ok()
                .build();
    }

    // 블로그 글 수정 API
    @PutMapping("/api/articles/{id}")
    public ResponseEntity<ArticleResponse> updateArticle(@PathVariable long id,
                                                         @Valid @RequestBody UpdateArticleRequest request) {
        Article updatedArticle = blogService.update(id, request);

        return ResponseEntity.ok()
                .body(new ArticleResponse(updatedArticle));
    }

}
