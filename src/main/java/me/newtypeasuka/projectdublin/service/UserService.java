package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.UserDto.NicknameResponse;
import me.newtypeasuka.projectdublin.dto.UserDto.ProfileResponse;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.CommentRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CommentRepository commentRepository;

    // Google 이메일에 대응하는 가입 완료 사용자가 있는지 확인
    @Transactional(readOnly = true)
    public boolean isRegistered(String email) {
        return email != null && userRepository.existsByEmail(email);
    }

    // MySQL 정렬 규칙과 동일하게 영문 대소문자를 구분하지 않고 닉네임 중복 확인
    @Transactional(readOnly = true)
    public boolean isNicknameTaken(String nickname) {
        return nickname != null
                && userRepository.existsByNicknameIgnoreCase(nickname.strip());
    }

    // Google 인증을 마친 신규 사용자의 닉네임을 저장해 가입을 완료
    @Transactional
    public User completeRegistration(String email, String name, String nickname) {
        User registeredUser = userRepository.findByEmail(email).orElse(null);
        if (registeredUser != null) {
            return registeredUser;
        }

        String normalizedNickname = normalizeNickname(nickname);
        if (userRepository.existsByNicknameIgnoreCase(normalizedNickname)) {
            throw new NicknameAlreadyExistsException();
        }

        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .name(name)
                .nickname(normalizedNickname)
                .build());
    }

    // 마이페이지에 표시할 현재 사용자 정보와 작성 활동 수를 조회
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String email) {
        User user = findUser(email);
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                blogRepository.countByAuthorId(user.getId()),
                commentRepository.countByCommenterIdAndDeletedAtIsNull(user.getId())
        );
    }

    // 로그인 사용자의 공개 닉네임만 변경
    @Transactional
    public NicknameResponse updateNickname(String email, String nickname) {
        User user = findUser(email);
        String normalizedNickname = normalizeNickname(nickname);

        if (user.getNickname().equals(normalizedNickname)) {
            return new NicknameResponse(user.getId(), user.getNickname());
        }
        if (userRepository.existsByNicknameIgnoreCaseAndIdNot(
                normalizedNickname,
                user.getId()
        )) {
            throw new NicknameAlreadyExistsException();
        }

        user.updateNickname(normalizedNickname);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 닉네임으로 변경한 경우 DB 고유 제약조건 충돌을 사용자 오류로 변환
            throw new NicknameAlreadyExistsException(exception);
        }
        return new NicknameResponse(user.getId(), user.getNickname());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw new InvalidNicknameException();
        }

        String normalizedNickname = nickname.strip();
        int length = normalizedNickname.length();
        if (normalizedNickname.isBlank()
                || length < User.MIN_NICKNAME_LENGTH
                || length > User.MAX_NICKNAME_LENGTH
                || normalizedNickname.indexOf('\r') >= 0
                || normalizedNickname.indexOf('\n') >= 0
                || normalizedNickname.indexOf('\t') >= 0) {
            throw new InvalidNicknameException();
        }
        return normalizedNickname;
    }

    public static class InvalidNicknameException extends RuntimeException {

        public InvalidNicknameException() {
            super("닉네임은 3자 이상 12자 이하로 입력해주세요.");
        }
    }

    public static class NicknameAlreadyExistsException extends RuntimeException {

        public NicknameAlreadyExistsException() {
            super("이미 사용 중인 닉네임입니다.");
        }

        public NicknameAlreadyExistsException(Throwable cause) {
            super("이미 사용 중인 닉네임입니다.", cause);
        }
    }
}
