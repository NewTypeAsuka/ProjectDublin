package me.newtypeasuka.projectdublin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockApiResponse {

    private StockApiResponse() {
    }

    public record StockListResponse(
            List<StockResponse> stocks,
            List<String> unavailableSymbols,
            Instant fetchedAt,
            boolean stale
    ) {
        public StockListResponse {
            stocks = stocks == null ? List.of() : List.copyOf(stocks);
            unavailableSymbols = unavailableSymbols == null
                    ? List.of()
                    : List.copyOf(unavailableSymbols);
        }

        public StockListResponse asStale() {
            return new StockListResponse(
                    stocks,
                    unavailableSymbols,
                    fetchedAt,
                    true
            );
        }
    }

    public record StockResponse(
            String symbol,
            String ticker,
            String name,
            String market,
            String marketName,
            String exchange,
            String currency,
            BigDecimal currentPrice,
            BigDecimal previousClose,
            BigDecimal priceChange,
            BigDecimal changePercent,
            Instant quotedAt,
            List<PricePointResponse> dailyPrices
    ) {
        public StockResponse {
            dailyPrices = dailyPrices == null
                    ? List.of()
                    : List.copyOf(dailyPrices);
        }
    }

    public record PricePointResponse(
            Instant time,
            BigDecimal price
    ) {
    }
}
