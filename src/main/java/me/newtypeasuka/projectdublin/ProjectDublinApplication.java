package me.newtypeasuka.projectdublin;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableJpaAuditing // created_at, updated_at 자동 저장을 위해 JPA Auditing 활성화
@EnableScheduling // 게시글에 연결되지 않은 오래된 S3 이미지 정리 작업 활성화
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
