package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Stock;
import me.newtypeasuka.projectdublin.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StockRepositoryTest {

    @Autowired
    StockRepository stockRepository;

    @Autowired
    UserRepository userRepository;

    @DisplayName("사용자별 관심 종목 심볼만 표시 순서와 ID 순으로 조회한다")
    @Test
    void findSymbolsByOwnerEmail() {
        User member = userRepository.save(User.builder()
                .email("stock-owner@example.com")
                .name("Stock Owner")
                .nickname("종목소유자")
                .build());
        User otherMember = userRepository.save(User.builder()
                .email("other-stock-owner@example.com")
                .name("Other Stock Owner")
                .nickname("다른소유자")
                .build());
        stockRepository.saveAllAndFlush(List.of(
                new Stock(member, "TLT", 2),
                new Stock(member, "VOO", 1),
                new Stock(member, "QQQM", 2),
                new Stock(otherMember, "8058.T", 1)
        ));

        List<String> symbols = stockRepository.findSymbolsByOwnerEmail(
                member.getEmail()
        );

        assertThat(symbols).containsExactly("VOO", "TLT", "QQQM");
    }
}
