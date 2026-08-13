package me.newtypeasuka.projectdublin.repository;

import me.newtypeasuka.projectdublin.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long> {

    // 로그인 사용자의 관심 종목 심볼만 화면 표시 순서대로 조회
    @Query("SELECT stock.symbol FROM Stock stock "
            + "WHERE stock.owner.email = :email "
            + "ORDER BY stock.displayOrder ASC, stock.id ASC")
    List<String> findSymbolsByOwnerEmail(@Param("email") String email);
}
