package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.config.S3Config.S3StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class S3ObjectUrlResolver {

    private final S3StorageProperties properties;
    private final S3Utilities s3Utilities;

    public String resolve(String key) {
        if (StringUtils.hasText(properties.publicBaseUrl())) {
            return removeTrailingSlash(properties.publicBaseUrl()) + "/" + removeLeadingSlash(key);
        }

        return s3Utilities.getUrl(GetUrlRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .toExternalForm();
    }

    public boolean isArticleImageUrl(String rawUrl) {
        try {
            URI candidate = URI.create(rawUrl);
            return allowedImagePrefixUris().stream()
                    .anyMatch(allowedPrefix -> matchesPrefix(candidate, allowedPrefix));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Optional<String> extractArticleImageKey(String rawUrl) {
        try {
            URI candidate = URI.create(rawUrl);
            Optional<URI> managedPrefix = managedImagePrefixUris().stream()
                    .filter(prefix -> matchesPrefix(candidate, prefix))
                    .findFirst();
            if (managedPrefix.isEmpty()) {
                // 과거 저장소 이미지는 표시만 허용하고 현재 버킷의 객체로 관리하지 않는다.
                return Optional.empty();
            }

            String keySuffix = candidate.getRawPath()
                    .substring(managedPrefix.get().getRawPath().length());
            if (!StringUtils.hasText(keySuffix)) {
                return Optional.empty();
            }
            return Optional.of(normalizedKeyPrefix() + "/" + keySuffix);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String normalizedKeyPrefix() {
        return removeTrailingSlash(removeLeadingSlash(properties.keyPrefix()));
    }

    private List<URI> managedImagePrefixUris() {
        String prefixKey = normalizedKeyPrefix() + "/";
        LinkedHashSet<String> prefixUrls = new LinkedHashSet<>();
        prefixUrls.add(resolve(prefixKey));
        prefixUrls.add(resolveDirectS3Url(prefixKey));
        return prefixUrls.stream().map(URI::create).toList();
    }

    private List<URI> allowedImagePrefixUris() {
        List<URI> prefixes = new ArrayList<>(managedImagePrefixUris());
        if (properties.legacyImagePrefixUrls() != null) {
            properties.legacyImagePrefixUrls().stream()
                    .filter(StringUtils::hasText)
                    .map(this::ensureTrailingSlash)
                    .map(URI::create)
                    .forEach(prefixes::add);
        }
        return List.copyOf(prefixes);
    }

    private String resolveDirectS3Url(String key) {
        return s3Utilities.getUrl(GetUrlRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .toExternalForm();
    }

    private boolean matchesPrefix(URI candidate, URI allowedPrefix) {
        return "https".equalsIgnoreCase(candidate.getScheme())
                && "https".equalsIgnoreCase(allowedPrefix.getScheme())
                && candidate.getUserInfo() == null
                && candidate.getQuery() == null
                && candidate.getFragment() == null
                && candidate.getRawPath() != null
                && allowedPrefix.getRawPath() != null
                && sameOrigin(candidate, allowedPrefix)
                && candidate.getRawPath().startsWith(allowedPrefix.getRawPath());
    }

    private String ensureTrailingSlash(String value) {
        return removeTrailingSlash(value) + "/";
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getHost() != null
                && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String removeLeadingSlash(String value) {
        return value.replaceFirst("^/+", "");
    }

    private String removeTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }
}
