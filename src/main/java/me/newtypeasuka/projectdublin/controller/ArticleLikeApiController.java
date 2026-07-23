package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.ArticleLikeResponse;
import me.newtypeasuka.projectdublin.service.ArticleLikeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/articles/{articleId}/likes")
public class ArticleLikeApiController {

    private final ArticleLikeService articleLikeService;

    @GetMapping
    public ResponseEntity<ArticleLikeResponse> getStatus(@PathVariable long articleId,
                                                         Principal principal) {
        return ResponseEntity.ok(articleLikeService.getStatus(articleId, principal.getName()));
    }

    @PutMapping
    public ResponseEntity<ArticleLikeResponse> like(@PathVariable long articleId,
                                                    Principal principal) {
        return ResponseEntity.ok(articleLikeService.like(articleId, principal.getName()));
    }

    @DeleteMapping
    public ResponseEntity<ArticleLikeResponse> unlike(@PathVariable long articleId,
                                                      Principal principal) {
        return ResponseEntity.ok(articleLikeService.unlike(articleId, principal.getName()));
    }
}
