package com.tokit.infra.blockchain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetCode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("balanceOfByPartition: RPC 노드에 닿지 않으면 0을 반환하지 않고 BlockchainException을 던진다.")
    void balanceOfByPartition_UnreachableRpc_Throws() throws Exception {
        ContractService unreachable = new ContractService();
        setField(unreachable, "rpcUrl", "http://127.0.0.1:1"); // 열려 있을 수 없는 포트
        setField(unreachable, "contractAddress", "0x5FbDB2315678afecb367f032d93F642f64180aa3");
        setField(unreachable, "privateKey", "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        unreachable.init();

        assertThatThrownBy(() -> unreachable.balanceOfByPartition(
                "0x5FbDB2315678afecb367f032d93F642f64180aa3", "DEFAULT", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
                .isInstanceOf(BlockchainException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Web3j web3jReturningCode(String code) throws Exception {
        Web3j web3j = mock(Web3j.class);
        EthGetCode response = new EthGetCode();
        response.setResult(code);
        Request request = mock(Request.class);
        when(request.send()).thenReturn(response);
        when(web3j.ethGetCode(anyString(), any())).thenReturn(request);
        return web3j;
    }

    @Test
    @DisplayName("코드가 없는 주소로는 이체하지 않고 ContractNotDeployedException을 던진다.")
    void handleTransfer_NoContractCode_Throws() throws Exception {
        setField(contractService, "web3j", web3jReturningCode("0x"));

        assertThatThrownBy(() -> contractService.handleTransferByPartition(
                "0x00000000000000000000000000000000000003e9", "STO-001", "DEFAULT",
                "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                BigDecimal.ONE))
                .isInstanceOf(ContractNotDeployedException.class);
    }

    @Test
    @DisplayName("코드가 없는 주소의 잔고는 0이 아니라 ContractNotDeployedException으로 알린다.")
    void balanceOf_NoContractCode_Throws() throws Exception {
        setField(contractService, "web3j", web3jReturningCode("0x"));

        assertThatThrownBy(() -> contractService.balanceOfByPartition(
                "0x00000000000000000000000000000000000003e9", "DEFAULT", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"))
                .isInstanceOf(ContractNotDeployedException.class);
    }

    @Test
    @DisplayName("빈 주소는 RPC를 부르지 않고 거절한다.")
    void requireDeployedContract_EmptyAddress_Throws() throws Exception {
        Web3j web3j = mock(Web3j.class);
        setField(contractService, "web3j", web3j);

        assertThatThrownBy(() -> contractService.requireDeployedContract(null))
                .isInstanceOf(ContractNotDeployedException.class);
        verifyNoInteractions(web3j);
    }

    @Test
    @DisplayName("코드가 확인된 주소는 대소문자와 관계없이 다시 조회하지 않는다.")
    void requireDeployedContract_CachesDeployedAddress() throws Exception {
        Web3j web3j = web3jReturningCode("0x6080604052");
        setField(contractService, "web3j", web3j);

        contractService.requireDeployedContract("0x5FbDB2315678afecb367f032d93F642f64180aa3");
        contractService.requireDeployedContract("0x5fbdb2315678afecb367f032d93f642f64180aa3");

        verify(web3j, times(1)).ethGetCode(anyString(), any());
    }
}
