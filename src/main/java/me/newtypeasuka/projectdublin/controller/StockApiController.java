package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.StockApiResponse.StockListResponse;
import me.newtypeasuka.projectdublin.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class StockApiController {

    private final StockService stockService;

    // 로그인 사용자가 DB에 등록한 관심 종목의 최근 시세와 일별 그래프 조회 API
    @GetMapping
    public ResponseEntity<StockListResponse> getWatchlist(Principal principal) {
        return ResponseEntity.ok(stockService.getWatchlist(principal.getName()));
    }

    // 선택한 시장에서 정확히 일치하는 티커 검색 API
    @GetMapping("/search")
    public ResponseEntity<StockListResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "ALL") String market
    ) {
        return ResponseEntity.ok(stockService.search(query, market));
    }
}
