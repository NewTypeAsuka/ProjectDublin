package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.config.StockConfig.StockProperties;
import me.newtypeasuka.projectdublin.dto.StockApiDto.StockListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StockServiceTest {

    private static final String SPARK_RESPONSE = """
            {
              "spark": {
                "result": [
                  {
                    "symbol": "8058.T",
                    "response": [{
                      "meta": {
                        "symbol": "8058.T",
                        "shortName": "MITSUBISHI CORP",
                        "currency": "JPY",
                        "exchangeName": "JPX",
                        "fullExchangeName": "Tokyo",
                        "regularMarketPrice": 3111,
                        "regularMarketTime": 1770000300
                      },
                      "timestamp": [1770000000, 1770000100, 1770000200],
                      "indicators": {"quote": [{"close": [3000, 3050, 3100]}]}
                    }]
                  },
                  {
                    "symbol": "VOO",
                    "response": [{
                      "meta": {
                        "symbol": "VOO",
                        "shortName": "Vanguard S&P 500 ETF",
                        "currency": "USD",
                        "exchangeName": "PCX",
                        "fullExchangeName": "NYSEArca",
                        "regularMarketPrice": 111,
                        "regularMarketTime": 1770000300
                      },
                      "timestamp": [1770000000, 1770000100, 1770000200],
                      "indicators": {"quote": [{"close": [90, 100, 110]}]}
                    }]
                  }
                ],
                "error": null
              }
            }
            """;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private MutableClock clock;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        clock = new MutableClock(
                Instant.parse("2026-08-09T00:00:00Z"),
                ZoneOffset.UTC
        );
        stockService = new StockService(
                restClientBuilder.build(),
                properties(List.of("VOO", "8058.T")),
                clock
        );
    }

    @DisplayName("관심 종목을 설정 순서대로 반환하고 지난 영업일 대비 변동을 계산한다")
    @Test
    void getWatchlistInConfiguredOrder() {
        expectSpark("VOO,8058.T", SPARK_RESPONSE);

        StockListResponse firstResponse = stockService.getWatchlist();
        StockListResponse cachedResponse = stockService.getWatchlist();

        assertThat(firstResponse.stocks())
                .extracting(stock -> stock.symbol())
                .containsExactly("VOO", "8058.T");
        assertThat(firstResponse.stocks().get(0).previousClose())
                .isEqualByComparingTo("100");
        assertThat(firstResponse.stocks().get(0).priceChange())
                .isEqualByComparingTo("11");
        assertThat(firstResponse.stocks().get(0).changePercent())
                .isEqualByComparingTo("11.00");
        assertThat(firstResponse.stocks().get(1).market()).isEqualTo("JP");
        assertThat(firstResponse.stocks().get(1).ticker()).isEqualTo("8058");
        assertThat(cachedResponse).isSameAs(firstResponse);
        server.verify();
    }

    @DisplayName("숫자 티커 검색은 선택한 시장의 정확한 종목만 반환한다")
    @Test
    void searchExactTickerByMarket() {
        String searchResponse = """
                {
                  "quotes": [
                    {"symbol":"0700.HK","quoteType":"EQUITY","exchange":"HKG"},
                    {"symbol":"0700.F","quoteType":"EQUITY","exchange":"FRA"},
                    {"symbol":"070021.TW","quoteType":"EQUITY","exchange":"TAI"}
                  ]
                }
                """;
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v1/finance/search");
                    assertThat(queryParameter(request.getURI().toString(), "q"))
                            .isEqualTo("700");
                })
                .andRespond(withSuccess(searchResponse, MediaType.APPLICATION_JSON));
        expectSpark("0700.HK", SPARK_RESPONSE.replace("8058.T", "0700.HK")
                .replace("MITSUBISHI CORP", "TENCENT")
                .replace("JPY", "HKD")
                .replace("JPX", "HKG")
                .replace("Tokyo", "HKSE"));

        StockListResponse response = stockService.search(" 700 ", "hk");

        assertThat(response.stocks())
                .extracting(stock -> stock.symbol())
                .containsExactly("0700.HK");
        assertThat(response.stocks().get(0).market()).isEqualTo("HK");
        server.verify();
    }

    @DisplayName("미국·일본·한국·홍콩·중국 시장 티커를 같은 검색 API로 지원한다")
    @ParameterizedTest
    @CsvSource({
            "VOO, VOO, PCX, USD, US",
            "8058, 8058.T, JPX, JPY, JP",
            "005930, 005930.KS, KSC, KRW, KR",
            "700, 0700.HK, HKG, HKD, HK",
            "600519, 600519.SS, SHH, CNY, CN"
    })
    void supportConfiguredMarkets(String query,
                                  String symbol,
                                  String exchange,
                                  String currency,
                                  String market) {
        String searchResponse = """
                {
                  "quotes": [
                    {"symbol":"%s","quoteType":"EQUITY","exchange":"%s"}
                  ]
                }
                """.formatted(symbol, exchange);
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v1/finance/search"))
                .andRespond(withSuccess(searchResponse, MediaType.APPLICATION_JSON));
        expectSpark(symbol, singleStockSparkResponse(symbol, exchange, currency));

        StockListResponse response = stockService.search(query, market);

        assertThat(response.stocks()).hasSize(1);
        assertThat(response.stocks().get(0).symbol()).isEqualTo(symbol);
        assertThat(response.stocks().get(0).market()).isEqualTo(market);
        assertThat(response.stocks().get(0).currency()).isEqualTo(currency);
        server.verify();
    }

    @DisplayName("지원하지 않는 티커 입력은 외부 시세를 호출하기 전에 거절한다")
    @Test
    void rejectInvalidTicker() {
        assertThatThrownBy(() -> stockService.search("VOO OR 1", "ALL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode()
                ).isEqualTo(HttpStatus.BAD_REQUEST));
        server.verify();
    }

    @DisplayName("캐시 만료 후 외부 장애가 발생하면 마지막 정상 시세를 반환한다")
    @Test
    void returnStaleCacheWhenProviderFails() {
        expectSpark("VOO,8058.T", SPARK_RESPONSE);
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v7/finance/spark"))
                .andRespond(withServerError());

        StockListResponse firstResponse = stockService.getWatchlist();
        clock.advance(Duration.ofMinutes(31));

        StockListResponse staleResponse = stockService.getWatchlist();

        assertThat(firstResponse.stale()).isFalse();
        assertThat(staleResponse.stale()).isTrue();
        assertThat(staleResponse.stocks()).isEqualTo(firstResponse.stocks());
        server.verify();
    }

    private void expectSpark(String symbols, String responseBody) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v7/finance/spark");
                    assertThat(queryParameter(
                            request.getURI().toString(),
                            "symbols"
                    )).isEqualTo(symbols);
                    assertThat(queryParameter(request.getURI().toString(), "range"))
                            .isEqualTo("1mo");
                    assertThat(queryParameter(request.getURI().toString(), "interval"))
                            .isEqualTo("1d");
                })
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private String queryParameter(String uri, String name) {
        return UriComponentsBuilder.fromUriString(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
    }

    private StockProperties properties(List<String> watchlist) {
        return new StockProperties(
                "https://query1.finance.yahoo.com",
                watchlist,
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                Duration.ofSeconds(3),
                Duration.ofSeconds(8)
        );
    }

    private String singleStockSparkResponse(String symbol,
                                            String exchange,
                                            String currency) {
        return """
                {
                  "spark": {
                    "result": [{
                      "symbol": "%s",
                      "response": [{
                        "meta": {
                          "symbol": "%s",
                          "shortName": "Test Stock",
                          "currency": "%s",
                          "exchangeName": "%s",
                          "fullExchangeName": "Test Exchange",
                          "regularMarketPrice": 111,
                          "regularMarketTime": 1770000300
                        },
                        "timestamp": [1770000000, 1770000100, 1770000200],
                        "indicators": {"quote": [{"close": [90, 100, 110]}]}
                      }]
                    }],
                    "error": null
                  }
                }
                """.formatted(symbol, symbol, currency, exchange);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
