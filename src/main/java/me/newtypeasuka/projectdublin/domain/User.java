package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "users") // users 테이블과 매핑
@NoArgsConstructor(access = AccessLevel.PROTECTED) // lombok으로 기본 생성자
@Getter // lombok으로 getter
@Entity // 엔티티로 지정
public class User {

    private static final int ADMIN_ROLE = 1;
    private static final int DEFAULT_ROLE = 2;

    @Id // id 필드를 기본키로 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본키를 자동으로 1씩 증가
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "role", nullable = false)
    private int role;

    @Builder // 빌더 패턴으로 객체 생성
    public User(String email, String nickname, Integer role) {
        this.email = email;
        this.nickname = nickname;
        this.role = role == null ? DEFAULT_ROLE : role;
    }

    public User update(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public boolean isAdmin() {
        return role == ADMIN_ROLE;
    }

}
