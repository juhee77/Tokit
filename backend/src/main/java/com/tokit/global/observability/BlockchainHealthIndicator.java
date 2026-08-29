package com.tokit.global.observability;

import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 블록체인 노드 연결 상태. 온체인 결제와 대사 배치가 이 연결에 의존하므로
 * RPC가 끊긴 상태를 장애 시점에 바로 드러내기 위한 헬스체크입니다.
 */
@Component
@RequiredArgsConstructor
public class BlockchainHealthIndicator implements HealthIndicator {

    private final ContractService contractService;

    @Override
    public Health health() {
        ContractService.NodeStatus status = contractService.checkNodeStatus();

        if (!status.reachable()) {
            return Health.down().withDetail("reason", status.error()).build();
        }

        return Health.up()
                .withDetail("clientVersion", status.clientVersion())
                .withDetail("blockNumber", status.blockNumber())
                .build();
    }
}
