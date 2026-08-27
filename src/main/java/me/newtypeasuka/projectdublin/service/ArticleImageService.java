package me.newtypeasuka.projectdublin.service;

import lombok.extern.slf4j.Slf4j;
import me.newtypeasuka.projectdublin.config.S3Config.S3StorageProperties;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleImage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleApiDto.ImageUploadResponse;
import me.newtypeasuka.projectdublin.repository.ArticleImageRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleImageService {

    public static final int MAX_IMAGES_PER_ARTICLE = 50;

    private static final String UPLOADER_ID_METADATA = "uploader-id";
    private static final String ORIGINAL_FILENAME_METADATA = "original-filename";
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final int MAX_IMAGE_SIGNATURE_LENGTH = 12;
    private static final int MAX_DELETE_ERROR_LENGTH = 1000;
    private static final Duration DELETE_CLAIM_DURATION = Duration.ofMinutes(10);
    private static final ZoneId IMAGE_CLEANUP_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp"
    );

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    private final S3ObjectUrlService s3ObjectUrlService;
    private final ArticleImageRepository articleImageRepository;
    private final UserRepository userRepository;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    @Autowired
    public ArticleImageService(S3Client s3Client,
                               S3StorageProperties properties,
                               S3ObjectUrlService s3ObjectUrlService,
                               ArticleImageRepository articleImageRepository,
                               UserRepository userRepository,
                               PlatformTransactionManager transactionManager) {
        this(
                s3Client,
                properties,
                s3ObjectUrlService,
                articleImageRepository,
                userRepository,
                createRequiresNewTransactionTemplate(transactionManager),
                Clock.system(IMAGE_CLEANUP_ZONE_ID)
        );
    }

    // 테스트에서는 트랜잭션과 현재 시각을 고정하여 상태 전이를 검증한다.
    ArticleImageService(S3Client s3Client,
                        S3StorageProperties properties,
                        S3ObjectUrlService s3ObjectUrlService,
                        ArticleImageRepository articleImageRepository,
                        UserRepository userRepository,
                        TransactionOperations transactionOperations,
                        Clock clock) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.s3ObjectUrlService = s3ObjectUrlService;
        this.articleImageRepository = articleImageRepository;
        this.userRepository = userRepository;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    public ImageUploadResponse upload(MultipartFile image, String email) {
        validateSize(image);
        User uploader = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보를 찾을 수 없습니다"
                ));

        ImageType imageType = ImageType.detect(readSignature(image));
        if (imageType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PNG, JPEG, GIF, WebP 이미지만 업로드할 수 있습니다"
            );
        }

        String originalFilename = normalizeOriginalFilename(
                image.getOriginalFilename(),
                imageType.extension
        );
        String key = createObjectKey(uploader.getId(), imageType.extension);
        savePendingImage(
                uploader,
                key,
                originalFilename,
                imageType.contentType,
                image.getSize()
        );

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(imageType.contentType)
                .contentLength(image.getSize())
                .cacheControl("public, max-age=31536000, immutable")
                .metadata(Map.of(
                        UPLOADER_ID_METADATA, uploader.getId().toString(),
                        ORIGINAL_FILENAME_METADATA, encodeFilename(originalFilename)
                ))
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromContentProvider(
                    () -> openUploadStream(image),
                    image.getSize(),
                    imageType.contentType
            ));
        } catch (UncheckedIOException exception) {
            queueUploadFailure(key, exception);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 파일을 읽지 못했습니다"
            );
        } catch (SdkException exception) {
            log.error("S3 게시글 이미지 업로드에 실패했습니다. key={}", key, exception);
            queueUploadFailure(key, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "이미지 저장소에 연결할 수 없습니다"
            );
        }

        return new ImageUploadResponse(s3ObjectUrlService.resolve(key));
    }

    public void synchronize(Article article, User currentEditor) {
        if (article.getId() == null) {
            throw new IllegalArgumentException("이미지를 연결하려면 게시글이 먼저 저장되어야 합니다");
        }
        if (!currentEditor.isAdmin()
                && !article.getAuthor().getId().equals(currentEditor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        ArticleImageReferences references = inspectArticleImages(article.getContent());
        if (references.imageCount() > MAX_IMAGES_PER_ARTICLE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지는 게시글당 최대 " + MAX_IMAGES_PER_ARTICLE + "장까지 첨부할 수 있습니다"
            );
        }

        Set<String> requestedKeys = references.managedKeys();
        List<ArticleImage> currentImages =
                articleImageRepository.findAllByArticleIdForUpdate(article.getId());
        Map<String, ArticleImage> currentImagesByKey = currentImages.stream()
                .collect(Collectors.toMap(ArticleImage::getS3Key, Function.identity()));

        Map<String, ArticleImage> mappedImagesByKey = requestedKeys.isEmpty()
                ? Map.of()
                : articleImageRepository.findAllByS3KeyInForUpdate(requestedKeys).stream()
                .collect(Collectors.toMap(ArticleImage::getS3Key, Function.identity()));

        List<ArticleImage> changedImages = new ArrayList<>();
        for (String key : requestedKeys) {
            ArticleImage mappedImage = mappedImagesByKey.get(key);
            if (currentImagesByKey.containsKey(key)) {
                continue;
            }

            if (mappedImage == null) {
                changedImages.add(createLegacyArticleImage(article, currentEditor, key));
                continue;
            }
            if (mappedImage.isAttached()) {
                if (mappedImage.getArticle() == null
                        || !mappedImage.getArticle().getId().equals(article.getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "다른 게시글에서 사용 중인 이미지는 연결할 수 없습니다"
                    );
                }
                continue;
            }
            if (!mappedImage.isPending()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "삭제 중인 이미지는 게시글에 연결할 수 없습니다"
                );
            }

            authorizePendingImage(mappedImage, currentEditor, key);
            mappedImage.attach(article);
            changedImages.add(mappedImage);
        }

        LocalDateTime now = currentTime();
        List<Long> removedImageIds = new ArrayList<>();
        for (ArticleImage currentImage : currentImages) {
            if (requestedKeys.contains(currentImage.getS3Key())) {
                continue;
            }
            currentImage.queueDeletion(now);
            changedImages.add(currentImage);
            removedImageIds.add(currentImage.getId());
        }

        if (!changedImages.isEmpty()) {
            articleImageRepository.saveAll(changedImages);
        }
        deleteAfterCommit(removedImageIds, "article-update");
    }

    public void removeAllForArticle(Long articleId) {
        List<ArticleImage> images = articleImageRepository.findAllByArticleIdForUpdate(articleId);
        if (images.isEmpty()) {
            return;
        }

        LocalDateTime now = currentTime();
        images.forEach(image -> image.queueDeletion(now));
        articleImageRepository.saveAllAndFlush(images);
        deleteAfterCommit(images.stream()
                .map(ArticleImage::getId)
                .toList(), "article-delete");
    }

    private ArticleImage createLegacyArticleImage(Article article,
                                                   User currentEditor,
                                                   String key) {
        String expectedUserPrefix = "%s/%d/".formatted(
                s3ObjectUrlService.normalizedKeyPrefix(),
                currentEditor.getId()
        );
        if (!key.startsWith(expectedUserPrefix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 편집자가 업로드한 이미지만 연결할 수 있습니다"
            );
        }

        HeadObjectResponse storedObject = loadStoredObject(key);
        String uploaderId = storedObject.metadata().get(UPLOADER_ID_METADATA);
        if (!currentEditor.getId().toString().equals(uploaderId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 편집자가 업로드한 이미지만 연결할 수 있습니다"
            );
        }

        String contentType = storedObject.contentType();
        Long contentLength = storedObject.contentLength();
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)
                || contentLength == null
                || contentLength <= 0
                || contentLength > properties.maxFileSize().toBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "저장된 이미지 정보가 올바르지 않습니다"
            );
        }

        String encodedFilename = storedObject.metadata().get(ORIGINAL_FILENAME_METADATA);
        String originalFilename = decodeFilename(encodedFilename);

        return ArticleImage.builder()
                .article(article)
                .uploader(currentEditor)
                .s3Key(key)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileSize(contentLength)
                .build();
    }

    private HeadObjectResponse loadStoredObject(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "S3에서 게시글 이미지를 찾을 수 없습니다"
                );
            }
            log.error("S3 게시글 이미지 조회에 실패했습니다. key={}", key, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "이미지 저장소에 연결할 수 없습니다"
            );
        } catch (SdkException exception) {
            log.error("S3 게시글 이미지 조회에 실패했습니다. key={}", key, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "이미지 저장소에 연결할 수 없습니다"
            );
        }
    }

    private void savePendingImage(User uploader,
                                  String key,
                                  String originalFilename,
                                  String contentType,
                                  long fileSize) {
        transactionOperations.executeWithoutResult(status ->
                articleImageRepository.saveAndFlush(ArticleImage.pending(
                        uploader,
                        key,
                        originalFilename,
                        contentType,
                        fileSize
                ))
        );
    }

    // S3 업로드 결과가 불확실해도 DB 기록을 남겨 이후 삭제를 재시도한다.
    private void queueUploadFailure(String key, RuntimeException uploadException) {
        try {
            transactionOperations.executeWithoutResult(status ->
                    articleImageRepository.findByS3KeyForUpdate(key)
                            .filter(ArticleImage::isPending)
                            .ifPresent(image -> {
                                image.queueDeletion(currentTime());
                                articleImageRepository.saveAndFlush(image);
                            })
            );
        } catch (RuntimeException cleanupException) {
            log.error(
                    "S3 업로드 실패 이미지를 삭제 대기 상태로 전환하지 못했습니다. key={}",
                    key,
                    cleanupException
            );
            uploadException.addSuppressed(cleanupException);
        }
    }

    private void authorizePendingImage(ArticleImage image, User currentEditor, String key) {
        User uploader = image.getUploader();
        String expectedUserPrefix = "%s/%d/".formatted(
                s3ObjectUrlService.normalizedKeyPrefix(),
                currentEditor.getId()
        );
        if (uploader == null
                || !currentEditor.getId().equals(uploader.getId())
                || !key.startsWith(expectedUserPrefix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 편집자가 업로드한 이미지만 연결할 수 있습니다"
            );
        }
    }

    private ArticleImageReferences inspectArticleImages(String content) {
        Set<String> keys = new LinkedHashSet<>();
        var images = Jsoup.parseBodyFragment(content).select("img[src]");
        images.forEach(image -> s3ObjectUrlService.extractArticleImageKey(image.attr("src"))
                .ifPresent(keys::add));
        return new ArticleImageReferences(images.size(), Set.copyOf(keys));
    }

    private void deleteAfterCommit(Collection<Long> imageIds, String reason) {
        List<Long> idsToDelete = imageIds.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (idsToDelete.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            idsToDelete.forEach(id -> processDeletionSafely(id, reason));
                        }
                    }
            );
            return;
        }

        idsToDelete.forEach(id -> processDeletionSafely(id, reason));
    }

    // 매시간 미첨부 pending 이미지와 실패한 S3 삭제 작업을 작은 배치로 정리한다.
    @Scheduled(cron = "${aws.s3.cleanup-cron:0 15 * * * *}", zone = "Asia/Seoul")
    public void cleanupArticleImages() {
        try {
            LocalDateTime now = currentTime();
            queueExpiredPendingImages(now);
            findDueDeletionIds(now).forEach(id -> processDeletionSafely(id, "scheduled-cleanup"));
        } catch (RuntimeException exception) {
            log.error("게시글 이미지 정리 작업을 실행하지 못했습니다.", exception);
        }
    }

    private void queueExpiredPendingImages(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(properties.pendingExpiration());
        transactionOperations.executeWithoutResult(status -> {
            List<ArticleImage> expiredImages = articleImageRepository
                    .findByStatusAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
                            ArticleImage.Status.PENDING,
                            cutoff,
                            PageRequest.of(0, properties.cleanupBatchSize())
                    );
            expiredImages.forEach(image -> image.queueDeletion(now));
            if (!expiredImages.isEmpty()) {
                articleImageRepository.saveAllAndFlush(expiredImages);
            }
        });
    }

    private List<Long> findDueDeletionIds(LocalDateTime now) {
        List<Long> imageIds = transactionOperations.execute(status -> articleImageRepository
                .findByStatusAndNextDeleteAttemptAtLessThanEqualOrderByNextDeleteAttemptAtAscIdAsc(
                        ArticleImage.Status.DELETE_PENDING,
                        now,
                        PageRequest.of(0, properties.cleanupBatchSize())
                )
                .stream()
                .map(ArticleImage::getId)
                .toList());
        return imageIds == null ? List.of() : imageIds;
    }

    private void processDeletionSafely(Long imageId, String reason) {
        try {
            processDeletion(imageId, reason);
        } catch (RuntimeException exception) {
            log.error(
                    "S3 게시글 이미지 삭제 작업을 처리하지 못했습니다. reason={}, imageId={}",
                    reason,
                    imageId,
                    exception
            );
        }
    }

    private void processDeletion(Long imageId, String reason) {
        LocalDateTime now = currentTime();
        DeletionTarget target = transactionOperations.execute(status ->
                articleImageRepository.findByIdForUpdate(imageId)
                        .filter(image -> image.isDeletionDue(now))
                        .map(image -> {
                            image.claimDeletion(now.plus(DELETE_CLAIM_DURATION));
                            articleImageRepository.saveAndFlush(image);
                            return new DeletionTarget(image.getId(), image.getS3Key());
                        })
                        .orElse(null)
        );
        if (target == null) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(target.s3Key())
                    .build());
            completeDeletion(target);
            log.info(
                    "S3 게시글 이미지를 삭제했습니다. reason={}, key={}",
                    reason,
                    target.s3Key()
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
                completeDeletion(target);
                return;
            }
            recordDeletionFailure(target, exception, reason);
        } catch (SdkException exception) {
            recordDeletionFailure(target, exception, reason);
        }
    }

    private void completeDeletion(DeletionTarget target) {
        transactionOperations.executeWithoutResult(status ->
                articleImageRepository.findByIdForUpdate(target.imageId())
                        .filter(ArticleImage::isDeletePending)
                        .filter(image -> image.getS3Key().equals(target.s3Key()))
                        .ifPresent(image -> {
                            articleImageRepository.delete(image);
                            articleImageRepository.flush();
                        })
        );
    }

    private void recordDeletionFailure(DeletionTarget target,
                                       SdkException exception,
                                       String reason) {
        transactionOperations.executeWithoutResult(status ->
                articleImageRepository.findByIdForUpdate(target.imageId())
                        .filter(ArticleImage::isDeletePending)
                        .filter(image -> image.getS3Key().equals(target.s3Key()))
                        .ifPresent(image -> {
                            int nextAttempt = image.getDeleteAttempts() == Integer.MAX_VALUE
                                    ? Integer.MAX_VALUE
                                    : image.getDeleteAttempts() + 1;
                            image.recordDeletionFailure(
                                    currentTime().plus(retryDelay(nextAttempt)),
                                    normalizeDeleteError(exception)
                            );
                            articleImageRepository.saveAndFlush(image);
                        })
        );
        log.error(
                "S3 게시글 이미지 삭제에 실패했습니다. reason={}, key={}",
                reason,
                target.s3Key(),
                exception
        );
    }

    private Duration retryDelay(int attempt) {
        return switch (attempt) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(30);
            case 4 -> Duration.ofHours(2);
            case 5 -> Duration.ofHours(12);
            default -> Duration.ofHours(24);
        };
    }

    private String normalizeDeleteError(SdkException exception) {
        String message = exception.getClass().getSimpleName()
                + (StringUtils.hasText(exception.getMessage())
                ? ": " + exception.getMessage()
                : "");
        if (message.codePointCount(0, message.length()) <= MAX_DELETE_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, message.offsetByCodePoints(0, MAX_DELETE_ERROR_LENGTH));
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.ofInstant(clock.instant(), IMAGE_CLEANUP_ZONE_ID);
    }

    private static TransactionTemplate createRequiresNewTransactionTemplate(
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private record ArticleImageReferences(int imageCount, Set<String> managedKeys) {
    }

    private record DeletionTarget(Long imageId, String s3Key) {
    }

    private void validateSize(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 이미지를 선택해주세요");
        }
        if (image.getSize() > properties.maxFileSize().toBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "이미지는 " + properties.maxFileSize().toMegabytes() + "MB 이하만 업로드할 수 있습니다"
            );
        }
    }

    private byte[] readSignature(MultipartFile image) {
        try (InputStream inputStream = image.getInputStream()) {
            return inputStream.readNBytes(MAX_IMAGE_SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 파일을 읽지 못했습니다"
            );
        }
    }

    private InputStream openUploadStream(MultipartFile image) {
        try {
            return image.getInputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String createObjectKey(Long userId, String extension) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return "%s/%d/%d/%02d/%s.%s".formatted(
                s3ObjectUrlService.normalizedKeyPrefix(),
                userId,
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extension
        );
    }

    private String normalizeOriginalFilename(String rawFilename, String extension) {
        String filename = rawFilename == null ? "" : rawFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();
        if (!StringUtils.hasText(filename)) {
            filename = "image." + extension;
        }
        if (filename.codePointCount(0, filename.length()) > MAX_ORIGINAL_FILENAME_LENGTH) {
            int endIndex = filename.offsetByCodePoints(0, MAX_ORIGINAL_FILENAME_LENGTH);
            filename = filename.substring(0, endIndex);
        }
        return filename;
    }

    private String encodeFilename(String filename) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(filename.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeFilename(String encodedFilename) {
        if (!StringUtils.hasText(encodedFilename)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "저장된 이미지의 원본 파일명 정보가 없습니다"
            );
        }

        try {
            String filename = new String(
                    Base64.getUrlDecoder().decode(encodedFilename),
                    StandardCharsets.UTF_8
            );
            if (!StringUtils.hasText(filename)
                    || filename.codePointCount(0, filename.length())
                    > MAX_ORIGINAL_FILENAME_LENGTH) {
                throw new IllegalArgumentException("invalid original filename");
            }
            return filename;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "저장된 이미지의 원본 파일명 정보가 올바르지 않습니다"
            );
        }
    }

    private enum ImageType {
        PNG("png", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }),
        JPEG("jpg", "image/jpeg", new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
        }),
        GIF_87A("gif", "image/gif", "GIF87a".getBytes(StandardCharsets.US_ASCII)),
        GIF_89A("gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)),
        WEBP("webp", "image/webp", new byte[]{
                0x52, 0x49, 0x46, 0x46
        });

        private final String extension;
        private final String contentType;
        private final byte[] signature;

        ImageType(String extension, String contentType, byte[] signature) {
            this.extension = extension;
            this.contentType = contentType;
            this.signature = signature;
        }

        private static ImageType detect(byte[] content) {
            for (ImageType imageType : values()) {
                if (imageType.matches(content)) {
                    return imageType;
                }
            }
            return null;
        }

        private boolean matches(byte[] content) {
            if (this == WEBP) {
                return content.length >= 12
                        && startsWith(content, signature)
                        && Arrays.equals(
                                Arrays.copyOfRange(content, 8, 12),
                                new byte[]{0x57, 0x45, 0x42, 0x50}
                        );
            }
            return startsWith(content, signature);
        }

        private boolean startsWith(byte[] content, byte[] expected) {
            return content.length >= expected.length
                    && Arrays.equals(Arrays.copyOf(content, expected.length), expected);
        }
    }
}
