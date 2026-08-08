package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email); // 이메일로 사용자 정보 가져오기 -> 구글 로그인 시 사용(이 이메일이 이미 존재하는지 확인)

    boolean existsByEmail(String email); // 최초 로그인 사용자의 내부 가입 완료 여부 확인

    boolean existsByNicknameIgnoreCase(String nickname); // 공개 닉네임 중복 확인

}
