package me.newtypeasuka.projectdublin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(StockConfig.StockProperties.class)
public class StockConfig {

    // Yahoo 시세 조회에만 사용하는 HTTP 클라이언트 설정
    @Bean("stockRestClient")
    public RestClient stockRestClient(StockProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.yahooBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; ProjectDublin/1.0)"
                )
                .build();
    }

    @Validated
    @ConfigurationProperties(prefix = "stock")
    public record StockProperties(
            @NotBlank String yahooBaseUrl,
            @NotEmpty List<@NotBlank @Pattern(
                    regexp = "^[A-Za-z0-9.^\\-=]{1,30}$"
            ) String> watchlist,
            @NotNull Duration cacheDuration,
            @NotNull Duration staleDuration,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout
    ) {
        public StockProperties {
            watchlist = watchlist == null ? List.of() : List.copyOf(watchlist);
        }
    }
}
