package me.newtypeasuka.projectdublin.config.oauth;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Transactional
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest); // 요청을 바탕으로 유저 정보 객체 반환
        updateExistingUser(user);

        return new DefaultOAuth2User(user.getAuthorities(), user.getAttributes(), "email");
    }

    // 기존 사용자의 Google 이름만 갱신하고, 신규 사용자는 닉네임 입력 전까지 저장하지 않음
    void updateExistingUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email"); // 구글에서 받아온 OAuth2User 객체에서 이메일을 가져옴
        String name = (String) attributes.get("name"); // 구글에서 받아온 OAuth2User 객체에서 이름을 가져옴

        userRepository.findByEmail(email)
                .ifPresent(entity -> entity.updateName(name));
    }

    // Google 이메일에 대응하는 내부 가입 사용자가 있는지 확인
    @Transactional(readOnly = true)
    public boolean isRegistered(String email) {
        return email != null && userRepository.existsByEmail(email);
    }

    // MySQL 정렬 규칙과 동일하게 영문 대소문자를 구분하지 않고 닉네임 중복 확인
    @Transactional(readOnly = true)
    public boolean isNicknameTaken(String nickname) {
        return nickname != null && userRepository.existsByNicknameIgnoreCase(nickname);
    }

    // Google 인증을 마친 신규 사용자에게 필수 닉네임을 받아 가입을 완료
    @Transactional
    public User completeRegistration(String email, String name, String nickname) {
        User registeredUser = userRepository.findByEmail(email).orElse(null);
        if (registeredUser != null) {
            return registeredUser;
        }

        validateNickname(nickname);
        if (userRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new NicknameAlreadyExistsException();
        }

        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .name(name)
                .nickname(nickname)
                .build());
    }

    private void validateNickname(String nickname) {
        if (nickname == null
                || !nickname.equals(nickname.strip())
                || nickname.length() < 3
                || nickname.length() > 12) {
            throw new IllegalArgumentException("닉네임은 앞뒤 공백 없이 3자 이상 12자 이하여야 합니다.");
        }
    }

    public static class NicknameAlreadyExistsException extends RuntimeException {

        public NicknameAlreadyExistsException() {
            super("이미 사용 중인 닉네임입니다.");
        }
    }

}
