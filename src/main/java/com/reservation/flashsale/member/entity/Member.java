package com.reservation.flashsale.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "point_balance", nullable = false)
    private int pointBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Member(String name, int pointBalance) {
        this.name = name;
        this.pointBalance = pointBalance;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 포인트 차감. 잔액 부족 시 예외 발생.
     */
    public void deductPoints(int amount) {
        if (this.pointBalance < amount) {
            throw new IllegalStateException(
                    "포인트 잔액 부족: 보유=" + this.pointBalance + ", 요청=" + amount);
        }
        this.pointBalance -= amount;
    }

    /**
     * 포인트 복구 (결제 취소 시).
     */
    public void restorePoints(int amount) {
        this.pointBalance += amount;
    }
}
