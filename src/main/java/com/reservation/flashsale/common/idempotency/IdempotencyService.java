package com.reservation.flashsale.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 기반 멱등성 서비스.
 * 동일한 결제/예약 요청이 중복 처리되는 것을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final StringRedisTemplate redisTemplate;

    /**
     * 멱등성 키를 확인하고, 없으면 PROCESSING 상태로 세팅한다. (SETNX)
     * 
     * @return 멱등성 키 세팅에 성공했으면 true (첫 요청), 이미 존재하면 false (중복 요청)
     */
    public boolean checkAndSetProcessing(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        try {
            // 키가 없을 때만 PROCESSING 세팅. TTL은 30분으로 설정.
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, STATUS_PROCESSING, Duration.ofMinutes(30));
            
            if (Boolean.FALSE.equals(success)) {
                String currentStatus = redisTemplate.opsForValue().get(key);
                log.warn("[Idempotency] 중복 요청 감지: key={}, status={}", idempotencyKey, currentStatus);
                return false;
            }
            
            log.info("[Idempotency] 새 요청 처리 시작: key={}", idempotencyKey);
            return true;
        } catch (Exception e) {
            log.warn("[Redis Fallback] Redis 장애 발생. 멱등성 검증을 DB Unique Constraint로 우회합니다. key={}", idempotencyKey, e);
            return true; // 에러 무시하고 통과시킴 (DB 에러가 차단할 것)
        }
    }

    /**
     * 처리가 성공적으로 완료되면 상태를 COMPLETED로 변경한다.
     */
    public void markCompleted(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, STATUS_COMPLETED, Duration.ofDays(1)); // 1일 보관
        log.info("[Idempotency] 요청 처리 완료 상태로 변경: key={}", idempotencyKey);
    }

    /**
     * 결제 실패 등의 이유로 처리가 롤백되면 멱등성 키를 삭제하여 재시도가 가능하게 한다.
     */
    public void deleteKey(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(key);
        log.info("[Idempotency] 멱등성 키 삭제 (재시도 허용): key={}", idempotencyKey);
    }
}
