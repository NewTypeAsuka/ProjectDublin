package me.newtypeasuka.projectdublin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing // created_at, updated_at 자동 저장을 위해 JPA Auditing 활성화
@SpringBootApplication // 스프링부트 시작점
public class ProjectDublinApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectDublinApplication.class, args);

    }

}