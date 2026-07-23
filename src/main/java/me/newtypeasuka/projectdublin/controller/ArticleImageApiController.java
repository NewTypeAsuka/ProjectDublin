package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.ArticleImageUploadResponse;
import me.newtypeasuka.projectdublin.service.ArticleImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
public class ArticleImageApiController {

    private final ArticleImageService articleImageService;

    @PostMapping(
            value = "/api/articles/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ArticleImageUploadResponse> upload(
            @RequestParam("image") MultipartFile image,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(articleImageService.upload(image, principal.getName()));
    }
}
