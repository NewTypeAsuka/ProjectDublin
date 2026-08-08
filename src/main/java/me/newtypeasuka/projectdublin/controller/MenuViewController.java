package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Controller
public class MenuViewController {

    // 관심 종목과 티커 검색 기능을 제공하는 주식 화면 조회
    @GetMapping("/menu/stocks")
    public String getStocks() {
        return "stocks";
    }

}
