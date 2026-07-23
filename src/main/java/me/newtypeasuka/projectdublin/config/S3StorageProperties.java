package me.newtypeasuka.projectdublin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.s3")
public record S3StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String accessKey,
        String secretKey,
        @NotBlank String keyPrefix,
        String publicBaseUrl,
        @NotNull DataSize maxFileSize
) {
}
