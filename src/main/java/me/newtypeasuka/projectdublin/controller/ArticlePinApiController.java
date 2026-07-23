package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.ArticlePinResponse;
import me.newtypeasuka.projectdublin.service.BlogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/articles/{articleId}/pin")
public class ArticlePinApiController {

    private final BlogService blogService;

    @PutMapping
    public ResponseEntity<ArticlePinResponse> pin(@PathVariable long articleId,
                                                  Principal principal) {
        Article article = blogService.updatePinned(articleId, true, principal.getName());
        return ResponseEntity.ok(new ArticlePinResponse(article.isPinned()));
    }

    @DeleteMapping
    public ResponseEntity<ArticlePinResponse> unpin(@PathVariable long articleId,
                                                    Principal principal) {
        Article article = blogService.updatePinned(articleId, false, principal.getName());
        return ResponseEntity.ok(new ArticlePinResponse(article.isPinned()));
    }
}
