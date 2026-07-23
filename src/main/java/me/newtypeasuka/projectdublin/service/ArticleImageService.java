package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.newtypeasuka.projectdublin.config.S3StorageProperties;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleImage;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleImageUploadResponse;
import me.newtypeasuka.projectdublin.repository.ArticleImageRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
@RequiredArgsConstructor
@Service
public class ArticleImageService {

    private static final String UPLOADER_ID_METADATA = "uploader-id";
    private static final String ORIGINAL_FILENAME_METADATA = "original-filename";
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp"
    );

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    private final S3ObjectUrlResolver urlResolver;
    private final ArticleImageRepository articleImageRepository;
    private final UserRepository userRepository;

    public ArticleImageUploadResponse upload(MultipartFile image, String email) {
        validateSize(image);
        User uploader = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보를 찾을 수 없습니다"
                ));

        byte[] content = readContent(image);
        ImageType imageType = ImageType.detect(content);
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
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(imageType.contentType)
                .contentLength((long) content.length)
                .cacheControl("public, max-age=31536000, immutable")
                .metadata(Map.of(
                        UPLOADER_ID_METADATA, uploader.getId().toString(),
                        ORIGINAL_FILENAME_METADATA, encodeFilename(originalFilename)
                ))
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            log.error("S3 게시글 이미지 업로드에 실패했습니다. key={}", key, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "이미지 저장소에 연결할 수 없습니다"
            );
        }

        return new ArticleImageUploadResponse(urlResolver.resolve(key));
    }

    public void synchronize(Article article) {
        if (article.getId() == null) {
            throw new IllegalArgumentException("이미지를 연결하려면 게시글이 먼저 저장되어야 합니다");
        }

        Set<String> requestedKeys = extractImageKeys(article.getContent());
        List<ArticleImage> currentImages =
                articleImageRepository.findAllByArticleId(article.getId());
        Map<String, ArticleImage> currentImagesByKey = currentImages.stream()
                .collect(Collectors.toMap(ArticleImage::getS3Key, Function.identity()));

        Map<String, ArticleImage> mappedImagesByKey = requestedKeys.isEmpty()
                ? Map.of()
                : articleImageRepository.findAllByS3KeyIn(requestedKeys).stream()
                .collect(Collectors.toMap(ArticleImage::getS3Key, Function.identity()));

        List<ArticleImage> newImages = new ArrayList<>();
        for (String key : requestedKeys) {
            ArticleImage mappedImage = mappedImagesByKey.get(key);
            if (mappedImage != null
                    && !mappedImage.getArticle().getId().equals(article.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "다른 게시글에서 사용 중인 이미지는 연결할 수 없습니다"
                );
            }
            if (currentImagesByKey.containsKey(key) || mappedImage != null) {
                continue;
            }

            newImages.add(createArticleImage(article, key));
        }
        if (!newImages.isEmpty()) {
            articleImageRepository.saveAll(newImages);
        }

        List<ArticleImage> removedImages = currentImages.stream()
                .filter(image -> !requestedKeys.contains(image.getS3Key()))
                .toList();
        if (!removedImages.isEmpty()) {
            articleImageRepository.deleteAllInBatch(removedImages);
            deleteAfterCommit(removedImages.stream()
                    .map(ArticleImage::getS3Key)
                    .toList());
        }
    }

    public void removeAllForArticle(Long articleId) {
        List<ArticleImage> images = articleImageRepository.findAllByArticleId(articleId);
        if (images.isEmpty()) {
            return;
        }

        articleImageRepository.deleteAllInBatch(images);
        deleteAfterCommit(images.stream()
                .map(ArticleImage::getS3Key)
                .toList());
    }

    private ArticleImage createArticleImage(Article article, String key) {
        String expectedUserPrefix = "%s/%d/".formatted(
                urlResolver.normalizedKeyPrefix(),
                article.getAuthor().getId()
        );
        if (!key.startsWith(expectedUserPrefix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 사용자가 업로드한 이미지만 연결할 수 있습니다"
            );
        }

        HeadObjectResponse storedObject = loadStoredObject(key);
        String uploaderId = storedObject.metadata().get(UPLOADER_ID_METADATA);
        if (!article.getAuthor().getId().toString().equals(uploaderId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 사용자가 업로드한 이미지만 연결할 수 있습니다"
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

    private Set<String> extractImageKeys(String content) {
        Set<String> keys = new LinkedHashSet<>();
        Jsoup.parseBodyFragment(content)
                .select("img[src]")
                .forEach(image -> urlResolver.extractArticleImageKey(image.attr("src"))
                        .ifPresent(keys::add));
        return keys;
    }

    private void deleteAfterCommit(Collection<String> keys) {
        List<String> keysToDelete = List.copyOf(keys);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteObjects(keysToDelete);
                        }
                    }
            );
            return;
        }

        deleteObjects(keysToDelete);
    }

    private void deleteObjects(Collection<String> keys) {
        for (String key : keys) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build());
            } catch (SdkException exception) {
                // DB 변경은 이미 커밋되었으므로 요청을 실패시키지 않고 운영 로그로 남긴다.
                log.error("사용하지 않는 S3 게시글 이미지 삭제에 실패했습니다. key={}", key, exception);
            }
        }
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

    private byte[] readContent(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 파일을 읽지 못했습니다"
            );
        }
    }

    private String createObjectKey(Long userId, String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "%s/%d/%d/%02d/%s.%s".formatted(
                urlResolver.normalizedKeyPrefix(),
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
