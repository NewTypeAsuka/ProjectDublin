package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.config.S3StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.net.URI;
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
            URI allowedPrefix = articleImagePrefixUri();

            return "https".equalsIgnoreCase(candidate.getScheme())
                    && "https".equalsIgnoreCase(allowedPrefix.getScheme())
                    && candidate.getUserInfo() == null
                    && candidate.getQuery() == null
                    && candidate.getFragment() == null
                    && candidate.getRawPath() != null
                    && allowedPrefix.getRawPath() != null
                    && sameOrigin(candidate, allowedPrefix)
                    && candidate.getRawPath().startsWith(allowedPrefix.getRawPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Optional<String> extractArticleImageKey(String rawUrl) {
        if (!isArticleImageUrl(rawUrl)) {
            return Optional.empty();
        }

        URI candidate = URI.create(rawUrl);
        URI allowedPrefix = articleImagePrefixUri();
        String keySuffix = candidate.getRawPath()
                .substring(allowedPrefix.getRawPath().length());
        if (!StringUtils.hasText(keySuffix)) {
            return Optional.empty();
        }

        return Optional.of(normalizedKeyPrefix() + "/" + keySuffix);
    }

    public String normalizedKeyPrefix() {
        return removeTrailingSlash(removeLeadingSlash(properties.keyPrefix()));
    }

    private URI articleImagePrefixUri() {
        return URI.create(resolve(normalizedKeyPrefix() + "/"));
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
