package com.tokit.infra.blockchain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContractServiceTest {

    private ContractService contractService;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        contractService = new ContractService();
        setField(contractService, "rpcUrl", "http://127.0.0.1:8545");
        setField(contractService, "contractAddress", "0x5FbDB2315678afecb367f032d93F642f64180aa3");
        setField(contractService, "privateKey", "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        contractService.init();
    }

    @Test
    @DisplayName("verifyInvestorWhitelist: RPC 노드 비활성화 시 예외를 포착하여 안전하게 false를 반환한다.")
    void verifyInvestorWhitelist_UnreachableRpc_ReturnsFalseSafely() {
        // When
        boolean isWhitelisted = contractService.verifyInvestorWhitelist("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

        // Then
        assertThat(isWhitelisted).isFalse();
    }
}
