package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Controller
public class MenuViewController {

    @GetMapping("/menu/stocks")
    public String getStocks() {
        return "stocks";
    }

}
