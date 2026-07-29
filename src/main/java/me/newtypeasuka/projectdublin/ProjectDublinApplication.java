package me.newtypeasuka.projectdublin;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@EnableJpaAuditing // created_at, updated_at 자동 저장을 위해 JPA Auditing 활성화
@SpringBootApplication // 스프링부트 시작점
public class ProjectDublinApplication {

    // 애플리케이션이 실행될 때 JVM의 기본 타임존을 한국 시간으로 강제 설정
    @PostConstruct
    public void setTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ProjectDublinApplication.class, args);

    }

}