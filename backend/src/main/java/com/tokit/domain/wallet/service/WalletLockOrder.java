package com.tokit.domain.wallet.service;

import java.util.function.Function;

/**
 * 두 사용자의 지갑에 비관적 락을 잡을 때의 순서 규칙.
 *
 * <p>락 순서가 경로마다 다르면(정산은 매수자 → 매도자, 이전은 송신자 → 수신자) 서로 다른
 * 종목의 체결이 병렬로 진행될 때 두 트랜잭션이 서로의 락을 기다리는 교착이 생긴다.
 * 역할과 무관하게 항상 userId 오름차순으로 잡으면 대기 관계에 순환이 생길 수 없다. (#21)
 *
 * <p>같은 트랜잭션에서 KRW 지갑과 자산 지갑을 모두 잡는 경로는 KRW 지갑 쌍을 먼저,
 * 자산 지갑 쌍을 나중에 잡는다. 이 규칙도 모든 경로에서 같아야 한다.
 */
public final class WalletLockOrder {

    private WalletLockOrder() {
    }

    /** 호출자가 넘긴 순서(first, second) 그대로 돌려주는 결과. */
    public record Locked<T>(T first, T second) {
    }

    /**
     * {@code firstUserId}, {@code secondUserId}의 지갑을 userId 오름차순으로 잠근 뒤,
     * 호출자가 넘긴 순서대로 돌려준다.
     *
     * <p>두 사용자가 같으면(자기 체결) 한 번만 잠그고 같은 지갑을 양쪽에 돌려준다.
     *
     * @param locker userId를 받아 해당 지갑을 잠가 반환하는 함수
     */
    public static <T> Locked<T> inUserIdOrder(Long firstUserId, Long secondUserId, Function<Long, T> locker) {
        if (firstUserId.equals(secondUserId)) {
            T wallet = locker.apply(firstUserId);
            return new Locked<>(wallet, wallet);
        }
        if (firstUserId < secondUserId) {
            T first = locker.apply(firstUserId);
            T second = locker.apply(secondUserId);
            return new Locked<>(first, second);
        }
        T second = locker.apply(secondUserId);
        T first = locker.apply(firstUserId);
        return new Locked<>(first, second);
    }
}
