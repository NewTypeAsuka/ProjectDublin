package me.newtypeasuka.projectdublin.controller;

import me.newtypeasuka.projectdublin.dto.ArticleImageUploadResponse;
import me.newtypeasuka.projectdublin.service.ArticleImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ArticleImageApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ArticleImageService articleImageService;

    @DisplayName("로그인 사용자가 Summernote 이미지를 업로드하면 공개 URL을 반환한다")
    @Test
    void uploadArticleImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "image.png",
                "image/png",
                new byte[]{0x01}
        );
        when(articleImageService.upload(any(), any()))
                .thenReturn(new ArticleImageUploadResponse(
                        "https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/"
                                + "articles/2026/07/image.png"
                ));

        mockMvc.perform(multipart("/api/articles/images")
                        .file(image)
                        .with(oauth2Login()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(
                        "https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/"
                                + "articles/2026/07/image.png"
                ));
    }

    @DisplayName("로그인하지 않은 사용자의 이미지 업로드를 거절한다")
    @Test
    void rejectUnauthenticatedUpload() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "image.png",
                "image/png",
                new byte[]{0x01}
        );

        mockMvc.perform(multipart("/api/articles/images").file(image))
                .andExpect(status().isUnauthorized());
    }
}
