package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.ArticleListViewResponse;
import me.newtypeasuka.projectdublin.dto.ArticleResponse;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.service.ArticleLikeService;
import me.newtypeasuka.projectdublin.service.BlogService;
import me.newtypeasuka.projectdublin.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController // 스프링의 RestController 어노테이션으로 REST API 컨트롤러 빈 등록(json 형태로 반환)
public class BlogApiController {

    private final BlogService blogService;
    private final ArticleLikeService articleLikeService;
    private final CommentService commentService;

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

    // 무한 스크롤에서 커서 이후의 일반 게시글을 10개씩 조회
    @GetMapping("/api/articles/feed")
    public ResponseEntity<ArticleListViewResponse.FeedResponse> findArticleFeed(
            @RequestParam String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        BlogService.ArticleFeed articleFeed = blogService.findArticleFeed(cursor, size);
        List<Article> articleEntities = articleFeed.articles();
        List<Long> articleIds = articleEntities.stream().map(Article::getId).toList();
        Map<Long, Long> likeCounts = articleLikeService.getLikeCounts(articleIds);
        Map<Long, Long> commentCounts = commentService.getCommentCounts(articleIds);
        List<ArticleListViewResponse> articles = articleEntities.stream()
                .map(article -> new ArticleListViewResponse(
                        article,
                        likeCounts.getOrDefault(article.getId(), 0L),
                        commentCounts.getOrDefault(article.getId(), 0L)
                ))
                .toList();

        return ResponseEntity.ok(new ArticleListViewResponse.FeedResponse(
                articles,
                articleFeed.nextCursor(),
                articleFeed.hasNext()
        ));
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
    public ResponseEntity<Void> deleteArticle(@PathVariable long id,
                                              Principal principal) { // @PathVariable 어노테이션으로 URL 경로에서 id 값을 가져옴
        blogService.delete(id, principal.getName());

        return ResponseEntity.ok()
                .build();
    }

    // 블로그 글 수정 API
    @PutMapping("/api/articles/{id}")
    public ResponseEntity<ArticleResponse> updateArticle(@PathVariable long id,
                                                         @Valid @RequestBody UpdateArticleRequest request,
                                                         Principal principal) {
        Article updatedArticle = blogService.update(id, request, principal.getName());

        return ResponseEntity.ok()
                .body(new ArticleResponse(updatedArticle));
    }

}
