package me.newtypeasuka.projectdublin.config.oauth;

import lombok.RequiredArgsConstructor;
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

}
