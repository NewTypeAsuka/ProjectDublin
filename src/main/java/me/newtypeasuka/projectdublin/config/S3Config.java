package me.newtypeasuka.projectdublin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3Config {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(S3StorageProperties properties) {
        boolean hasAccessKey = StringUtils.hasText(properties.accessKey());
        boolean hasSecretKey = StringUtils.hasText(properties.secretKey());
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                    "AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY는 함께 설정해야 합니다"
            );
        }

        if (hasAccessKey) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
            );
        }

        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3StorageProperties properties,
                             AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public S3Utilities s3Utilities(S3StorageProperties properties) {
        return S3Utilities.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
