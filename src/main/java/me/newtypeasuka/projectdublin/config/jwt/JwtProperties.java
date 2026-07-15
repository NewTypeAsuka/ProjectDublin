package me.newtypeasuka.projectdublin.config.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties("jwt") // 자바 클래스에 properties.yml의 jwt 프로퍼티를 바인딩
public class JwtProperties {

    private String issuer;
    private String secretKey;

}
