package me.newtypeasuka.projectdublin.repository;

import jakarta.persistence.LockModeType;
import me.newtypeasuka.projectdublin.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email); // 이메일로 사용자 정보 가져오기 -> 구글 로그인 시 사용(이 이메일이 이미 존재하는지 확인)

    // 같은 사용자의 채팅 메시지 중복 저장 검사를 다중 인스턴스에서도 직렬화
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.email = :email")
    Optional<User> findByEmailForChatMessage(@Param("email") String email);

    boolean existsByEmail(String email); // 최초 로그인 사용자의 내부 가입 완료 여부 확인

    boolean existsByNicknameIgnoreCase(String nickname); // 공개 닉네임 중복 확인

    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id); // 현재 사용자를 제외한 공개 닉네임 중복 확인

}
