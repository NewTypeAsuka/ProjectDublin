package me.newtypeasuka.projectdublin.controller;

import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.PricePointResponse;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.StockListResponse;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.StockResponse;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import me.newtypeasuka.projectdublin.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class StockApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    StockService stockService;

    User member;

    @BeforeEach
    void setUp() {
        member = userRepository.save(User.builder()
                .email("stock-member@example.com")
                .name("Stock Member")
                .nickname("주식회원")
                .build());
    }

    @DisplayName("로그인한 사용자는 관심 종목 시세 API를 조회할 수 있다")
    @Test
    void getWatchlist() throws Exception {
        when(stockService.getWatchlist()).thenReturn(stockResponse("VOO", "VOO", "US"));

        mockMvc.perform(get("/api/stocks").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].symbol").value("VOO"))
                .andExpect(jsonPath("$.stocks[0].market").value("US"))
                .andExpect(jsonPath("$.stocks[0].currentPrice").value(111))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @DisplayName("로그인하지 않은 사용자의 주식 API 접근은 거절한다")
    @Test
    void rejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("티커와 시장을 주식 검색 서비스에 전달한다")
    @Test
    void searchTicker() throws Exception {
        when(stockService.search("8058", "JP"))
                .thenReturn(stockResponse("8058.T", "8058", "JP"));

        mockMvc.perform(get("/api/stocks/search")
                        .param("query", "8058")
                        .param("market", "JP")
                        .with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].symbol").value("8058.T"))
                .andExpect(jsonPath("$.stocks[0].ticker").value("8058"));

        verify(stockService).search("8058", "JP");
    }

    @DisplayName("주식 화면은 검색 UI와 전용 자바스크립트를 렌더링한다")
    @Test
    void renderStocksPage() throws Exception {
        mockMvc.perform(get("/menu/stocks").with(loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(view().name("stocks"))
                .andExpect(content().string(containsString("id=\"stock-search-form\"")))
                .andExpect(content().string(containsString("aria-expanded=\"false\"")))
                .andExpect(content().string(not(containsString("id=\"stock-market-select\""))))
                .andExpect(content().string(not(containsString(
                        "최근 가격과 지난 영업일 대비 주가 변동"
                ))))
                .andExpect(content().string(containsString("id=\"stock-list\"")))
                .andExpect(content().string(containsString("src=\"/js/stocks.js\"")))
                .andExpect(content().string(containsString(
                        "미국·한국·일본·홍콩·중국 주식 시장 지원 · 30분 캐시"
                )));

        mockMvc.perform(get("/js/stocks.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "loadCollection(activeRequest)"
                )));
    }

    private StockListResponse stockResponse(String symbol,
                                            String ticker,
                                            String market) {
        Instant quotedAt = Instant.parse("2026-08-09T00:00:00Z");
        StockResponse stock = new StockResponse(
                symbol,
                ticker,
                ticker + " test stock",
                market,
                market.equals("JP") ? "일본" : "미국",
                market.equals("JP") ? "Tokyo" : "NYSEArca",
                market.equals("JP") ? "JPY" : "USD",
                BigDecimal.valueOf(111),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(11),
                BigDecimal.valueOf(11),
                quotedAt,
                List.of(
                        new PricePointResponse(
                                Instant.parse("2026-08-08T00:00:00Z"),
                                BigDecimal.valueOf(100)
                        ),
                        new PricePointResponse(quotedAt, BigDecimal.valueOf(111))
                )
        );
        return new StockListResponse(
                List.of(stock),
                List.of(),
                quotedAt,
                false
        );
    }

    private RequestPostProcessor loginUser(User user) {
        DefaultOAuth2User oAuth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", user.getEmail(), "name", user.getName()),
                "email"
        );
        return oauth2Login().oauth2User(oAuth2User);
    }
}
