package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentContentRequest;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.CommentResponse;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.ImageUploadResponse;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.LikeResponse;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.PinResponse;
import me.newtypeasuka.projectdublin.service.ArticleImageService;
import me.newtypeasuka.projectdublin.service.ArticleLikeService;
import me.newtypeasuka.projectdublin.service.BlogService;
import me.newtypeasuka.projectdublin.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/articles")
public class ArticleApiController {

    private final ArticleImageService articleImageService;
    private final ArticleLikeService articleLikeService;
    private final BlogService blogService;
    private final CommentService commentService;

    // 게시글 본문에 첨부할 이미지 업로드 API
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

    // 게시글의 좋아요 수와 현재 사용자의 좋아요 여부 조회 API
    @GetMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> getLikeStatus(@PathVariable long articleId,
                                                      Principal principal) {
        return ResponseEntity.ok(articleLikeService.getStatus(articleId, principal.getName()));
    }

    // 게시글 좋아요 등록 API
    @PutMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> like(@PathVariable long articleId,
                                             Principal principal) {
        return ResponseEntity.ok(articleLikeService.like(articleId, principal.getName()));
    }

    // 게시글 좋아요 취소 API
    @DeleteMapping("/{articleId}/likes")
    public ResponseEntity<LikeResponse> unlike(@PathVariable long articleId,
                                               Principal principal) {
        return ResponseEntity.ok(articleLikeService.unlike(articleId, principal.getName()));
    }

    // 관리자용 게시글 고정 API
    @PutMapping("/{articleId}/pin")
    public ResponseEntity<PinResponse> pin(@PathVariable long articleId,
                                           Principal principal) {
        Article article = blogService.updatePinned(articleId, true, principal.getName());
        return ResponseEntity.ok(new PinResponse(article.isPinned()));
    }

    // 관리자용 게시글 고정 해제 API
    @DeleteMapping("/{articleId}/pin")
    public ResponseEntity<PinResponse> unpin(@PathVariable long articleId,
                                             Principal principal) {
        Article article = blogService.updatePinned(articleId, false, principal.getName());
        return ResponseEntity.ok(new PinResponse(article.isPinned()));
    }

    // 게시글 댓글 목록 조회 API
    @GetMapping("/{articleId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable long articleId,
                                                             Principal principal) {
        return ResponseEntity.ok(commentService.getComments(articleId, principal.getName()));
    }

    // 게시글 일반 댓글 작성 API
    @PostMapping("/{articleId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable long articleId,
            @Valid @RequestBody CommentContentRequest request,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(articleId, request, principal.getName()));
    }

    // 일반 댓글에 한 단계 대댓글 작성 API
    @PostMapping("/{articleId}/comments/{parentId}/replies")
    public ResponseEntity<CommentResponse> createReply(
            @PathVariable long articleId,
            @PathVariable long parentId,
            @Valid @RequestBody CommentContentRequest request,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createReply(
                        articleId,
                        parentId,
                        request,
                        principal.getName()
                ));
    }

    // 댓글 또는 대댓글 수정 API
    @PutMapping("/{articleId}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable long articleId,
            @PathVariable long commentId,
            @Valid @RequestBody CommentContentRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(commentService.updateComment(
                articleId,
                commentId,
                request,
                principal.getName()
        ));
    }

    // 댓글 또는 대댓글 삭제 API
    @DeleteMapping("/{articleId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable long articleId,
            @PathVariable long commentId,
            Principal principal
    ) {
        commentService.deleteComment(articleId, commentId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
