package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.ImageUploadResponse;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.LikeResponse;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.PinResponse;
import me.newtypeasuka.projectdublin.service.ArticleImageService;
import me.newtypeasuka.projectdublin.service.ArticleLikeService;
import me.newtypeasuka.projectdublin.service.BlogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/articles")
public class ArticleApiController {

    private final ArticleImageService articleImageService;
    private final ArticleLikeService articleLikeService;
    private final BlogService blogService;

    @PostMapping(
            value = "/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ImageUploadResponse> upload(
            @RequestParam("image") MultipartFile image,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(articleImageService.upload(image, principal.getName()));
    }

    @GetMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> getLikeStatus(@PathVariable long articleId,
                                                      Principal principal) {
        return ResponseEntity.ok(articleLikeService.getStatus(articleId, principal.getName()));
    }

    @PutMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> like(@PathVariable long articleId,
                                             Principal principal) {
        return ResponseEntity.ok(articleLikeService.like(articleId, principal.getName()));
    }

    @DeleteMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> unlike(@PathVariable long articleId,
                                               Principal principal) {
        return ResponseEntity.ok(articleLikeService.unlike(articleId, principal.getName()));
    }

    @PutMapping("/{articleId}/pin")
    public ResponseEntity<PinResponse> pin(@PathVariable long articleId,
                                           Principal principal) {
        Article article = blogService.updatePinned(articleId, true, principal.getName());
        return ResponseEntity.ok(new PinResponse(article.isPinned()));
    }

    @DeleteMapping("/{articleId}/pin")
    public ResponseEntity<PinResponse> unpin(@PathVariable long articleId,
                                             Principal principal) {
        Article article = blogService.updatePinned(articleId, false, principal.getName());
        return ResponseEntity.ok(new PinResponse(article.isPinned()));
    }
}
