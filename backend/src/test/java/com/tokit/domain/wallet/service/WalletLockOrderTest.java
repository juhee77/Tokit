package com.tokit.domain.wallet.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WalletLockOrderTest {

    @Test
    @DisplayName("넘긴 순서와 무관하게 항상 userId 오름차순으로 잠근다.")
    void locksInAscendingUserIdOrder() {
        List<Long> forward = new ArrayList<>();
        List<Long> reversed = new ArrayList<>();

        WalletLockOrder.inUserIdOrder(1L, 2L, id -> forward.add(id));
        WalletLockOrder.inUserIdOrder(2L, 1L, id -> reversed.add(id));

        assertThat(forward).containsExactly(1L, 2L);
        assertThat(reversed).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("잠근 순서와 무관하게 결과는 호출자가 넘긴 순서대로 돌려준다.")
    void returnsInCallerOrder() {
        WalletLockOrder.Locked<String> locked = WalletLockOrder.inUserIdOrder(2L, 1L, id -> "wallet-" + id);

        assertThat(locked.first()).isEqualTo("wallet-2");
        assertThat(locked.second()).isEqualTo("wallet-1");
    }

    @Test
    @DisplayName("같은 사용자면 한 번만 잠그고 같은 지갑을 양쪽에 돌려준다.")
    void sameUserLocksOnce() {
        List<Long> calls = new ArrayList<>();

        WalletLockOrder.Locked<String> locked = WalletLockOrder.inUserIdOrder(5L, 5L, id -> {
            calls.add(id);
            return "wallet-" + id;
        });

        assertThat(calls).containsExactly(5L);
        assertThat(locked.first()).isSameAs(locked.second());
    }
}
