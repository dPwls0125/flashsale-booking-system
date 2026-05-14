package com.reservation.flashsale.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInitializer {

    private final StockService stockService;

    /**
     * 애플리케이션이 완전히 준비된 후 Redis에 재고를 초기화한다.
     * SETNX를 사용하므로 이미 Redis에 값이 있으면 덮어쓰지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("재고 Redis 초기화 시작");
        stockService.initializeStock();
        log.info("재고 Redis 초기화 완료");
    }
}
