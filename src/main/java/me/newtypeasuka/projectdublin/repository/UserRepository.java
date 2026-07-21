package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email); // 이메일로 사용자 정보 가져오기 -> 구글 로그인 시 사용(이 이메일이 이미 존재하는지 확인)

}
