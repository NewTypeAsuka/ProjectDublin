package me.newtypeasuka.projectdublin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Table(
        name = "stocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stocks_user_symbol",
                columnNames = {"user_id", "symbol"}
        ),
        indexes = @Index(
                name = "idx_stocks_user_display_order",
                columnList = "user_id, display_order, id"
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class Stock {

    public static final int MAX_SYMBOL_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // 로그인 사용자마다 독립적인 관심 종목 목록을 소유
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @Column(
            name = "symbol",
            nullable = false,
            updatable = false,
            length = MAX_SYMBOL_LENGTH
    )
    private String symbol;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 사용자 관심 종목과 화면 표시 순서를 함께 등록
    public Stock(User owner, String symbol, int displayOrder) {
        this.owner = owner;
        this.symbol = symbol;
        this.displayOrder = displayOrder;
    }
}
