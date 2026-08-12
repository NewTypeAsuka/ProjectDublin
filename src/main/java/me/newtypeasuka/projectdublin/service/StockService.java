package me.newtypeasuka.projectdublin.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import me.newtypeasuka.projectdublin.config.StockConfig.StockProperties;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.PricePointResponse;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.StockListResponse;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.StockResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StockService {

    private static final int MAX_SEARCH_RESULTS = 5;
    private static final int MAX_CACHE_ENTRIES = 200;
    private static final Pattern TICKER_PATTERN =
            Pattern.compile("^[A-Z0-9.\\-]{1,15}$");
    private static final Pattern PROVIDER_SYMBOL_PATTERN =
            Pattern.compile("^[A-Z0-9.^\\-=]{1,30}$");
    private static final Set<String> US_EXCHANGES = Set.of(
            "ASE", "BTS", "NCM", "NGM", "NMS", "NYQ", "PCX"
    );

    private final RestClient restClient;
    private final StockProperties properties;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Object cacheMonitor = new Object();

    @Autowired
    public StockService(
            @Qualifier("stockRestClient") RestClient restClient,
            StockProperties properties
    ) {
        this(restClient, properties, Clock.systemUTC());
    }

    StockService(RestClient restClient,
                 StockProperties properties,
                 Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    // application.yml에 등록된 관심 종목을 설정 순서대로 조회
    public StockListResponse getWatchlist() {
        return getOrLoad("watchlist", () -> loadStocks(properties.watchlist()));
    }

    // Yahoo 티커 검색 결과 중 지원 시장의 정확한 종목만 조회
    public StockListResponse search(String query, String market) {
        String normalizedQuery = normalizeQuery(query);
        Market requestedMarket = parseMarket(market);
        String cacheKey = "search:" + requestedMarket.name() + ":" + normalizedQuery;

        return getOrLoad(cacheKey, () -> {
            List<String> symbols = findExactSymbols(
                    normalizedQuery,
                    requestedMarket
            );
            if (symbols.isEmpty()) {
                return new StockListResponse(
                        List.of(),
                        List.of(),
                        clock.instant(),
                        false
                );
            }
            return loadStocks(symbols);
        });
    }

    private StockListResponse getOrLoad(String cacheKey,
                                        Supplier<StockListResponse> loader) {
        Instant now = clock.instant();
        CacheEntry cached = cache.get(cacheKey);
        if (isWithin(cached, now, properties.cacheDuration())) {
            return cached.response();
        }

        synchronized (cacheMonitor) {
            now = clock.instant();
            cached = cache.get(cacheKey);
            if (isWithin(cached, now, properties.cacheDuration())) {
                return cached.response();
            }

            try {
                StockListResponse response = loader.get();
                putCache(cacheKey, response, now);
                return response;
            } catch (RuntimeException exception) {
                log.warn("주식 시세 조회에 실패했습니다. cacheKey={}", cacheKey, exception);
                if (isWithin(cached, now, properties.staleDuration())) {
                    return cached.response().asStale();
                }
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "현재 주식 시세를 불러올 수 없습니다",
                        exception
                );
            }
        }
    }

    private boolean isWithin(CacheEntry entry,
                             Instant now,
                             Duration duration) {
        if (entry == null || duration.isNegative()) {
            return false;
        }
        return Duration.between(entry.loadedAt(), now).compareTo(duration) <= 0;
    }

    private void putCache(String cacheKey,
                          StockListResponse response,
                          Instant loadedAt) {
        if (!cache.containsKey(cacheKey) && cache.size() >= MAX_CACHE_ENTRIES) {
            Instant staleLimit = loadedAt.minus(properties.staleDuration());
            cache.entrySet().removeIf(
                    entry -> entry.getValue().loadedAt().isBefore(staleLimit)
            );

            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.entrySet().stream()
                        .min(Comparator.comparing(entry -> entry.getValue().loadedAt()))
                        .map(Map.Entry::getKey)
                        .ifPresent(cache::remove);
            }
        }
        cache.put(cacheKey, new CacheEntry(response, loadedAt));
    }

    private List<String> findExactSymbols(String query, Market requestedMarket) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/finance/search")
                        .queryParam("q", query)
                        .queryParam("quotesCount", 12)
                        .queryParam("newsCount", 0)
                        .queryParam("listsCount", 0)
                        .queryParam("enableFuzzyQuery", false)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (root == null || !root.path("quotes").isArray()) {
            throw new IllegalStateException("Yahoo 티커 검색 응답 형식이 올바르지 않습니다");
        }

        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (JsonNode quote : root.path("quotes")) {
            String quoteType = quote.path("quoteType").asText("");
            if (!quoteType.equals("EQUITY") && !quoteType.equals("ETF")) {
                continue;
            }

            String symbol = quote.path("symbol").asText("")
                    .trim()
                    .toUpperCase(Locale.ROOT);
            String exchange = quote.path("exchange").asText("")
                    .trim()
                    .toUpperCase(Locale.ROOT);
            if (!PROVIDER_SYMBOL_PATTERN.matcher(symbol).matches()
                    || !matchesTicker(symbol, query)) {
                continue;
            }

            Market symbolMarket = resolveMarket(symbol, exchange);
            if (symbolMarket == null
                    || requestedMarket != Market.ALL
                    && requestedMarket != symbolMarket) {
                continue;
            }

            symbols.add(symbol);
            if (symbols.size() == MAX_SEARCH_RESULTS) {
                break;
            }
        }
        return List.copyOf(symbols);
    }

    private StockListResponse loadStocks(List<String> requestedSymbols) {
        List<String> symbols = requestedSymbols.stream()
                .filter(StringUtils::hasText)
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .filter(symbol -> PROVIDER_SYMBOL_PATTERN.matcher(symbol).matches())
                .distinct()
                .toList();
        if (symbols.isEmpty()) {
            throw new IllegalStateException("조회할 티커가 없습니다");
        }

        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v7/finance/spark")
                        .queryParam("symbols", String.join(",", symbols))
                        .queryParam("range", "1mo")
                        .queryParam("interval", "1d")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        JsonNode spark = root == null ? null : root.path("spark");
        if (spark == null
                || !spark.path("result").isArray()
                || hasProviderError(spark.path("error"))) {
            throw new IllegalStateException("Yahoo 시세 응답 형식이 올바르지 않습니다");
        }

        Map<String, JsonNode> responsesBySymbol = new LinkedHashMap<>();
        for (JsonNode result : spark.path("result")) {
            String symbol = result.path("symbol").asText("")
                    .toUpperCase(Locale.ROOT);
            JsonNode response = result.path("response").path(0);
            if (PROVIDER_SYMBOL_PATTERN.matcher(symbol).matches()
                    && !response.isMissingNode()) {
                responsesBySymbol.put(symbol, response);
            }
        }

        List<StockResponse> stocks = new ArrayList<>();
        List<String> unavailableSymbols = new ArrayList<>();
        for (String symbol : symbols) {
            JsonNode response = responsesBySymbol.get(symbol);
            if (response == null) {
                unavailableSymbols.add(symbol);
                continue;
            }
            try {
                stocks.add(toStockResponse(symbol, response));
            } catch (RuntimeException exception) {
                log.warn("주식 시세 응답을 변환하지 못했습니다. symbol={}", symbol, exception);
                unavailableSymbols.add(symbol);
            }
        }

        if (stocks.isEmpty()) {
            throw new IllegalStateException("조회 가능한 주식 시세가 없습니다");
        }
        return new StockListResponse(
                stocks,
                unavailableSymbols,
                clock.instant(),
                false
        );
    }

    private boolean hasProviderError(JsonNode error) {
        return error != null && !error.isMissingNode() && !error.isNull();
    }

    private StockResponse toStockResponse(String symbol, JsonNode response) {
        JsonNode meta = response.path("meta");
        String exchangeCode = meta.path("exchangeName").asText("")
                .toUpperCase(Locale.ROOT);
        Market market = resolveMarket(symbol, exchangeCode);
        if (market == null) {
            throw new IllegalArgumentException("지원하지 않는 주식 시장입니다");
        }

        List<PricePointResponse> pricePoints = extractPricePoints(response);
        BigDecimal currentPrice = readDecimal(meta.path("regularMarketPrice"));
        if (currentPrice == null && !pricePoints.isEmpty()) {
            currentPrice = pricePoints.get(pricePoints.size() - 1).price();
        }
        if (currentPrice == null) {
            throw new IllegalArgumentException("현재 주가가 없습니다");
        }

        BigDecimal previousClose = pricePoints.size() >= 2
                ? pricePoints.get(pricePoints.size() - 2).price()
                : readDecimal(meta.path("chartPreviousClose"));
        BigDecimal priceChange = null;
        BigDecimal changePercent = null;
        if (previousClose != null) {
            priceChange = currentPrice.subtract(previousClose);
            if (previousClose.signum() != 0) {
                changePercent = priceChange
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 2, RoundingMode.HALF_UP);
            }
        }

        long quotedAt = meta.path("regularMarketTime").asLong(0);
        String name = firstText(meta, "shortName", "longName");
        String exchange = firstText(meta, "fullExchangeName", "exchangeName");

        return new StockResponse(
                symbol,
                removeMarketSuffix(symbol),
                StringUtils.hasText(name) ? name : symbol,
                market.name(),
                market.displayName,
                StringUtils.hasText(exchange) ? exchange : exchangeCode,
                meta.path("currency").asText(""),
                currentPrice,
                previousClose,
                priceChange,
                changePercent,
                quotedAt > 0 ? Instant.ofEpochSecond(quotedAt) : null,
                pricePoints
        );
    }

    private List<PricePointResponse> extractPricePoints(JsonNode response) {
        JsonNode timestamps = response.path("timestamp");
        JsonNode closes = response.path("indicators")
                .path("quote")
                .path(0)
                .path("close");
        if (!timestamps.isArray() || !closes.isArray()) {
            return List.of();
        }

        List<PricePointResponse> points = new ArrayList<>();
        int length = Math.min(timestamps.size(), closes.size());
        for (int index = 0; index < length; index++) {
            long timestamp = timestamps.path(index).asLong(0);
            BigDecimal price = readDecimal(closes.path(index));
            if (timestamp > 0 && price != null) {
                points.add(new PricePointResponse(
                        Instant.ofEpochSecond(timestamp),
                        price
                ));
            }
        }
        return List.copyOf(points);
    }

    private BigDecimal readDecimal(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeQuery(String query) {
        String normalized = query == null
                ? ""
                : query.trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "티커는 영문, 숫자, 점, 하이픈을 포함해 15자 이하로 입력해주세요"
            );
        }
        return normalized;
    }

    private Market parseMarket(String market) {
        String normalized = market == null
                ? "ALL"
                : market.trim().toUpperCase(Locale.ROOT);
        try {
            return Market.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 주식 시장입니다"
            );
        }
    }

    private boolean matchesTicker(String symbol, String query) {
        if (symbol.equals(query)) {
            return true;
        }

        String symbolBase = removeMarketSuffix(symbol).replace('-', '.');
        String queryBase = removeMarketSuffix(query).replace('-', '.');
        if (symbolBase.equals(queryBase)) {
            return true;
        }

        return isNumeric(symbolBase)
                && isNumeric(queryBase)
                && stripLeadingZeros(symbolBase).equals(stripLeadingZeros(queryBase));
    }

    private boolean isNumeric(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private String stripLeadingZeros(String value) {
        String stripped = value.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private String removeMarketSuffix(String symbol) {
        String upperSymbol = symbol.toUpperCase(Locale.ROOT);
        for (String suffix : List.of(".KS", ".KQ", ".HK", ".SS", ".SZ", ".T")) {
            if (upperSymbol.endsWith(suffix)) {
                return upperSymbol.substring(0, upperSymbol.length() - suffix.length());
            }
        }
        return upperSymbol;
    }

    private Market resolveMarket(String symbol, String exchange) {
        String upperSymbol = symbol.toUpperCase(Locale.ROOT);
        if (upperSymbol.endsWith(".T")) {
            return Market.JP;
        }
        if (upperSymbol.endsWith(".KS") || upperSymbol.endsWith(".KQ")) {
            return Market.KR;
        }
        if (upperSymbol.endsWith(".HK")) {
            return Market.HK;
        }
        if (upperSymbol.endsWith(".SS") || upperSymbol.endsWith(".SZ")) {
            return Market.CN;
        }
        if (!upperSymbol.contains(".")
                && US_EXCHANGES.contains(exchange.toUpperCase(Locale.ROOT))) {
            return Market.US;
        }
        return null;
    }

    private enum Market {
        ALL("전체"),
        US("미국"),
        JP("일본"),
        KR("한국"),
        HK("홍콩"),
        CN("중국");

        private final String displayName;

        Market(String displayName) {
            this.displayName = displayName;
        }
    }

    private record CacheEntry(
            StockListResponse response,
            Instant loadedAt
    ) {
    }
}
