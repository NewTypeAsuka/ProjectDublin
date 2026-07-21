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

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest); // 요청을 바탕으로 유저 정보 객체 반환
        saveOrUpdate(user);

        return new DefaultOAuth2User(user.getAuthorities(), user.getAttributes(), "email");
    }

    // 유저가 존재하면 업데이트, 없으면 새로 생성
    private User saveOrUpdate(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email"); // 구글에서 받아온 OAuth2User 객체에서 이메일을 가져옴
        String name = (String) attributes.get("name"); // 구글에서 받아온 OAuth2User 객체에서 이름을 가져옴

        User user = userRepository.findByEmail(email)
                .map(entity -> entity.update(name)) // 이메일을 확인해보니 사용자가 있으면 이름 갱신
                .orElse(User.builder() // 이메일을 확인해보니 사용자가 없으면 새로 저장
                        .email(email)
                        .nickname(name)
                        .build());

        return userRepository.save(user);
    }

}
