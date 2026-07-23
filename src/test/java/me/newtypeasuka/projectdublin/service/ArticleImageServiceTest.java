package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.config.S3StorageProperties;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleImage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleImageUploadResponse;
import me.newtypeasuka.projectdublin.repository.ArticleImageRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleImageServiceTest {

    private static final String EMAIL = "writer@example.com";
    private static final long USER_ID = 42L;

    @Mock
    S3Client s3Client;

    @Mock
    ArticleImageRepository articleImageRepository;

    @Mock
    UserRepository userRepository;

    ArticleImageService articleImageService;
    S3ObjectUrlResolver urlResolver;
    User user;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "projectdublin-test-images",
                "ap-northeast-2",
                "",
                "",
                "articles",
                "",
                DataSize.ofMegabytes(10)
        );
        urlResolver = new S3ObjectUrlResolver(
                properties,
                S3Utilities.builder().region(Region.AP_NORTHEAST_2).build()
        );
        articleImageService = new ArticleImageService(
                s3Client,
                properties,
                urlResolver,
                articleImageRepository,
                userRepository
        );
        user = User.builder()
                .email(EMAIL)
                .nickname("Writer")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @DisplayName("PNG 파일과 업로더 정보를 UUID 기반 키로 S3에 업로드한다")
    @Test
    void uploadPngImage() {
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "한글 이미지.png",
                "application/octet-stream",
                png
        );
        when(userRepository.findByEmail(EMAIL)).thenReturn(java.util.Optional.of(user));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ArticleImageUploadResponse response = articleImageService.upload(image, EMAIL);

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("projectdublin-test-images");
        assertThat(request.key())
                .matches("articles/42/\\d{4}/\\d{2}/[0-9a-f-]+\\.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.metadata().get("uploader-id")).isEqualTo("42");
        assertThat(decodeFilename(request.metadata().get("original-filename")))
                .isEqualTo("한글 이미지.png");
        assertThat(response.url())
                .startsWith("https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/")
                .endsWith(".png");
    }

    @DisplayName("게시글 본문의 S3 이미지를 검증한 후 article_images에 연결한다")
    @Test
    void synchronizeUploadedImageWithArticle() {
        String key = "articles/42/2026/07/image.png";
        Article article = articleWithContent(
                100L,
                "<p>본문</p><img src=\"" + urlResolver.resolve(key) + "\" alt=\"image.png\">"
        );
        when(articleImageRepository.findAllByArticleId(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyIn(any())).thenReturn(List.of());
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                storedImage("42", "image.png")
        );

        articleImageService.synchronize(article);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ArticleImage>> imagesCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(articleImageRepository).saveAll(imagesCaptor.capture());
        ArticleImage savedImage = StreamSupport.stream(
                        imagesCaptor.getValue().spliterator(),
                        false
                )
                .findFirst()
                .orElseThrow();

        assertThat(savedImage.getArticle()).isSameAs(article);
        assertThat(savedImage.getS3Key()).isEqualTo(key);
        assertThat(savedImage.getOriginalFilename()).isEqualTo("image.png");
        assertThat(savedImage.getContentType()).isEqualTo("image/png");
        assertThat(savedImage.getFileSize()).isEqualTo(9L);
    }

    @DisplayName("다른 사용자가 업로드한 S3 이미지는 게시글에 연결하지 않는다")
    @Test
    void rejectImageUploadedByAnotherUser() {
        String key = "articles/42/2026/07/image.png";
        Article article = articleWithContent(
                100L,
                "<img src=\"" + urlResolver.resolve(key) + "\">"
        );
        when(articleImageRepository.findAllByArticleId(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyIn(any())).thenReturn(List.of());
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                storedImage("99", "image.png")
        );

        assertThatThrownBy(() -> articleImageService.synchronize(article))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode()
                ).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(articleImageRepository, never()).saveAll(any());
    }

    @DisplayName("수정된 본문에서 빠진 이미지는 DB 연결과 S3 객체를 제거한다")
    @Test
    void removeImageMissingFromUpdatedContent() {
        Article article = articleWithContent(100L, "<p>이미지를 제거한 본문</p>");
        ArticleImage existingImage = ArticleImage.builder()
                .article(article)
                .s3Key("articles/42/2026/07/image.png")
                .originalFilename("image.png")
                .contentType("image/png")
                .fileSize(9L)
                .build();
        when(articleImageRepository.findAllByArticleId(100L))
                .thenReturn(List.of(existingImage));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        articleImageService.synchronize(article);

        verify(articleImageRepository).deleteAllInBatch(List.of(existingImage));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @DisplayName("이미지로 위장한 파일은 S3에 업로드하지 않는다")
    @Test
    void rejectFileWithoutSupportedImageSignature() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "fake.png",
                "image/png",
                "not-an-image".getBytes()
        );
        when(userRepository.findByEmail(EMAIL)).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> articleImageService.upload(image, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode()
                ).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(s3Client, never()).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    private Article articleWithContent(Long id, String content) {
        Article article = Article.builder()
                .author(user)
                .title("Title")
                .content(content)
                .build();
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private HeadObjectResponse storedImage(String uploaderId, String originalFilename) {
        String encodedFilename = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(originalFilename.getBytes(StandardCharsets.UTF_8));
        return HeadObjectResponse.builder()
                .contentType("image/png")
                .contentLength(9L)
                .metadata(Map.of(
                        "uploader-id", uploaderId,
                        "original-filename", encodedFilename
                ))
                .build();
    }

    private String decodeFilename(String encodedFilename) {
        return new String(
                Base64.getUrlDecoder().decode(encodedFilename),
                StandardCharsets.UTF_8
        );
    }
}
