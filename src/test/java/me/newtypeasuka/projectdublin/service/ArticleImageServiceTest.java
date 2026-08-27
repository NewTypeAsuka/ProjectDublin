package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.config.S3Config.S3StorageProperties;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleImage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.ImageUploadResponse;
import me.newtypeasuka.projectdublin.repository.ArticleImageRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleImageServiceTest {

    private static final String EMAIL = "writer@example.com";
    private static final long USER_ID = 42L;
    private static final Instant CURRENT_INSTANT = Instant.parse("2026-07-10T00:00:00Z");
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Mock
    S3Client s3Client;

    @Mock
    ArticleImageRepository articleImageRepository;

    @Mock
    UserRepository userRepository;

    ArticleImageService articleImageService;
    S3ObjectUrlService s3ObjectUrlService;
    User user;
    AtomicReference<ArticleImage> savedImage;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "projectdublin-test-images",
                "ap-northeast-2",
                "",
                "",
                "articles",
                "",
                List.of(),
                DataSize.ofMegabytes(10),
                Duration.ofHours(24),
                100
        );
        s3ObjectUrlService = new S3ObjectUrlService(
                properties,
                S3Utilities.builder().region(Region.AP_NORTHEAST_2).build()
        );
        articleImageService = new ArticleImageService(
                s3Client,
                properties,
                s3ObjectUrlService,
                articleImageRepository,
                userRepository,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(CURRENT_INSTANT, SEOUL_ZONE_ID)
        );
        user = User.builder()
                .email(EMAIL)
                .name("Writer")
                .nickname("작성자닉")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        savedImage = new AtomicReference<>();
        lenient().when(articleImageRepository.saveAndFlush(any(ArticleImage.class)))
                .thenAnswer(invocation -> {
                    ArticleImage image = invocation.getArgument(0);
                    savedImage.set(image);
                    return image;
                });
    }

    @DisplayName("S3 업로드 전에 pending 이미지와 업로더를 먼저 DB에 기록한다")
    @Test
    void uploadPngImageAfterSavingPendingRecord() throws IOException {
        byte[] png = pngBytes();
        MockMultipartFile image = spy(new MockMultipartFile(
                "image",
                "한글 이미지.png",
                "application/octet-stream",
                png
        ));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ImageUploadResponse response = articleImageService.upload(image, EMAIL);

        InOrder uploadOrder = inOrder(articleImageRepository, s3Client);
        uploadOrder.verify(articleImageRepository).saveAndFlush(any(ArticleImage.class));
        uploadOrder.verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        ArticleImage pendingImage = savedImage.get();
        assertThat(pendingImage.getStatus()).isEqualTo(ArticleImage.Status.PENDING);
        assertThat(pendingImage.getArticle()).isNull();
        assertThat(pendingImage.getUploader()).isSameAs(user);
        assertThat(pendingImage.getS3Key())
                .matches("articles/42/2026/07/[0-9a-f-]+\\.png");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("projectdublin-test-images");
        assertThat(request.key()).isEqualTo(pendingImage.getS3Key());
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.metadata().get("uploader-id")).isEqualTo("42");
        assertThat(decodeFilename(request.metadata().get("original-filename")))
                .isEqualTo("한글 이미지.png");
        assertThat(bodyCaptor.getValue().optionalContentLength()).contains((long) png.length);
        verify(image, never()).getBytes();
        assertThat(response.url())
                .startsWith("https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/")
                .endsWith(".png");
    }

    @DisplayName("S3 업로드 실패 시 pending 이미지를 S3 삭제 대기로 전환한다")
    @Test
    void queuePendingImageDeletionWhenUploadFails() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "image.png",
                "image/png",
                pngBytes()
        );
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(articleImageRepository.findByS3KeyForUpdate(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(savedImage.get()));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("upload timeout"));

        assertThatThrownBy(() -> articleImageService.upload(image, EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode()
                ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(savedImage.get().getStatus())
                .isEqualTo(ArticleImage.Status.DELETE_PENDING);
        assertThat(savedImage.get().getNextDeleteAttemptAt())
                .isEqualTo(currentTime());
    }

    @DisplayName("pending 이미지를 게시글에 연결하면 attached 상태로 전환한다")
    @Test
    void attachPendingImageToArticle() {
        String key = "articles/42/2026/07/image.png";
        ArticleImage pendingImage = pendingImage(user, key);
        Article article = articleWithContent(
                100L,
                "<p>본문</p><img src=\"" + s3ObjectUrlService.resolve(key) + "\">"
        );
        when(articleImageRepository.findAllByArticleIdForUpdate(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyInForUpdate(any()))
                .thenReturn(List.of(pendingImage));

        articleImageService.synchronize(article, user);

        assertThat(pendingImage.getStatus()).isEqualTo(ArticleImage.Status.ATTACHED);
        assertThat(pendingImage.getArticle()).isSameAs(article);
        verify(articleImageRepository).saveAll(any());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @DisplayName("관리자는 자신이 올린 pending 이미지를 다른 작성자의 게시글에 연결한다")
    @Test
    void attachAdminPendingImageToAnotherAuthorsArticle() {
        User admin = user(7L, "admin@example.com", 1);
        String key = "articles/7/2026/07/admin-image.png";
        ArticleImage pendingImage = pendingImage(admin, key);
        Article article = articleWithContent(
                100L,
                "<img src=\"" + s3ObjectUrlService.resolve(key) + "\">"
        );
        when(articleImageRepository.findAllByArticleIdForUpdate(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyInForUpdate(any()))
                .thenReturn(List.of(pendingImage));

        articleImageService.synchronize(article, admin);

        assertThat(pendingImage.getStatus()).isEqualTo(ArticleImage.Status.ATTACHED);
        assertThat(pendingImage.getArticle()).isSameAs(article);
    }

    @DisplayName("다른 사용자가 올린 pending 이미지는 게시글에 연결하지 않는다")
    @Test
    void rejectPendingImageUploadedByAnotherUser() {
        String key = "articles/42/2026/07/image.png";
        ArticleImage pendingImage = pendingImage(
                user(99L, "other@example.com", 2),
                key
        );
        Article article = articleWithContent(
                100L,
                "<img src=\"" + s3ObjectUrlService.resolve(key) + "\">"
        );
        when(articleImageRepository.findAllByArticleIdForUpdate(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyInForUpdate(any()))
                .thenReturn(List.of(pendingImage));

        assertBadRequest(() -> articleImageService.synchronize(article, user));

        verify(articleImageRepository, never()).saveAll(any());
    }

    @DisplayName("과거 방식으로 업로드되어 DB에 없는 S3 이미지도 검증 후 연결한다")
    @Test
    void attachLegacyS3ImageWithoutPendingRecord() {
        String key = "articles/42/2026/07/legacy.png";
        Article article = articleWithContent(
                100L,
                "<img src=\"" + s3ObjectUrlService.resolve(key) + "\">"
        );
        when(articleImageRepository.findAllByArticleIdForUpdate(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyInForUpdate(any())).thenReturn(List.of());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(storedImage("42", "legacy.png"));

        articleImageService.synchronize(article, user);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Iterable<ArticleImage>> imagesCaptor =
                org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(articleImageRepository).saveAll(imagesCaptor.capture());
        ArticleImage attachedImage = imagesCaptor.getValue().iterator().next();
        assertThat(attachedImage.getStatus()).isEqualTo(ArticleImage.Status.ATTACHED);
        assertThat(attachedImage.getUploader()).isSameAs(user);
        assertThat(attachedImage.getArticle()).isSameAs(article);
    }

    @DisplayName("수정 본문에서 빠진 이미지는 게시글 연결 해제 후 S3와 DB에서 삭제한다")
    @Test
    void deleteImageMissingFromUpdatedContent() {
        Article article = articleWithContent(100L, "<p>이미지를 제거한 본문</p>");
        ArticleImage existingImage = attachedImage(1L, article, user, "image.png");
        when(articleImageRepository.findAllByArticleIdForUpdate(100L))
                .thenReturn(List.of(existingImage));
        when(articleImageRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingImage));

        articleImageService.synchronize(article, user);

        assertThat(existingImage.getStatus()).isEqualTo(ArticleImage.Status.DELETE_PENDING);
        assertThat(existingImage.getArticle()).isNull();
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(articleImageRepository).delete(existingImage);
        verify(articleImageRepository).flush();
    }

    @DisplayName("게시글 본문의 이미지가 51장이면 백엔드에서 저장을 거부한다")
    @Test
    void rejectArticleWithMoreThanFiftyImages() {
        String imageUrl = s3ObjectUrlService.resolve("articles/42/2026/07/image.png");
        Article article = articleWithContent(100L, repeatedImages(imageUrl, 51));

        assertBadRequest(() -> articleImageService.synchronize(article, user));

        verify(articleImageRepository, never()).findAllByArticleIdForUpdate(any());
    }

    @DisplayName("동일한 이미지 URL을 포함하더라도 본문 이미지 50장까지 허용한다")
    @Test
    void allowArticleWithFiftyImages() {
        String key = "articles/42/2026/07/image.png";
        ArticleImage pendingImage = pendingImage(user, key);
        Article article = articleWithContent(
                100L,
                repeatedImages(s3ObjectUrlService.resolve(key), 50)
        );
        when(articleImageRepository.findAllByArticleIdForUpdate(100L)).thenReturn(List.of());
        when(articleImageRepository.findAllByS3KeyInForUpdate(any()))
                .thenReturn(List.of(pendingImage));

        articleImageService.synchronize(article, user);

        assertThat(pendingImage.getStatus()).isEqualTo(ArticleImage.Status.ATTACHED);
    }

    @DisplayName("24시간 동안 첨부되지 않은 pending 이미지를 S3와 DB에서 정리한다")
    @Test
    void cleanupExpiredPendingImage() {
        ArticleImage pendingImage = pendingImage(
                user,
                "articles/42/2026/07/expired.png"
        );
        ReflectionTestUtils.setField(pendingImage, "id", 1L);
        ReflectionTestUtils.setField(
                pendingImage,
                "createdAt",
                currentTime().minusHours(25)
        );
        when(articleImageRepository
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
                        any(),
                        any(),
                        any(Pageable.class)
                )).thenReturn(List.of(pendingImage));
        when(articleImageRepository
                .findByStatusAndNextDeleteAttemptAtLessThanEqualOrderByNextDeleteAttemptAtAscIdAsc(
                        any(),
                        any(),
                        any(Pageable.class)
                )).thenReturn(List.of(pendingImage));
        when(articleImageRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(pendingImage));

        articleImageService.cleanupArticleImages();

        assertThat(pendingImage.getStatus()).isEqualTo(ArticleImage.Status.DELETE_PENDING);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(articleImageRepository).delete(pendingImage);
    }

    @DisplayName("S3 삭제 실패는 오류와 재시도 시각을 남기고 DB 기록을 보존한다")
    @Test
    void retryFailedS3Deletion() {
        ArticleImage deletionPendingImage = pendingImage(
                user,
                "articles/42/2026/07/retry.png"
        );
        ReflectionTestUtils.setField(deletionPendingImage, "id", 1L);
        deletionPendingImage.queueDeletion(currentTime());
        when(articleImageRepository
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
                        any(),
                        any(),
                        any(Pageable.class)
                )).thenReturn(List.of());
        when(articleImageRepository
                .findByStatusAndNextDeleteAttemptAtLessThanEqualOrderByNextDeleteAttemptAtAscIdAsc(
                        any(),
                        any(),
                        any(Pageable.class)
                )).thenReturn(List.of(deletionPendingImage));
        when(articleImageRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(deletionPendingImage));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("delete timeout"));

        articleImageService.cleanupArticleImages();

        assertThat(deletionPendingImage.getDeleteAttempts()).isEqualTo(1);
        assertThat(deletionPendingImage.getNextDeleteAttemptAt())
                .isEqualTo(currentTime().plusMinutes(1));
        assertThat(deletionPendingImage.getLastDeleteError())
                .contains("SdkClientException", "delete timeout");
        verify(articleImageRepository, never()).delete(any(ArticleImage.class));
    }

    @DisplayName("이미지로 위장한 파일은 pending 기록이나 S3 객체를 만들지 않는다")
    @Test
    void rejectFileWithoutSupportedImageSignature() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "fake.png",
                "image/png",
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertBadRequest(() -> articleImageService.upload(image, EMAIL));

        verify(articleImageRepository, never()).saveAndFlush(any(ArticleImage.class));
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
                .searchContent("")
                .build();
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private ArticleImage pendingImage(User uploader, String key) {
        return ArticleImage.pending(uploader, key, "image.png", "image/png", 9L);
    }

    private ArticleImage attachedImage(Long id,
                                       Article article,
                                       User uploader,
                                       String filename) {
        ArticleImage image = ArticleImage.builder()
                .article(article)
                .uploader(uploader)
                .s3Key("articles/42/2026/07/" + filename)
                .originalFilename(filename)
                .contentType("image/png")
                .fileSize(9L)
                .build();
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    private User user(Long id, String email, int role) {
        User result = User.builder()
                .email(email)
                .name("User")
                .nickname("사용자" + id)
                .role(role)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
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

    private String repeatedImages(String imageUrl, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "<img src=\"" + imageUrl + "\">")
                .reduce("", String::concat);
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.ofInstant(CURRENT_INSTANT, SEOUL_ZONE_ID);
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode()
                ).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private String decodeFilename(String encodedFilename) {
        return new String(
                Base64.getUrlDecoder().decode(encodedFilename),
                StandardCharsets.UTF_8
        );
    }
}
