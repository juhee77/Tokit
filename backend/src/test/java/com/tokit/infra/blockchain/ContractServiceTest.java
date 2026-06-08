package com.tokit.infra.blockchain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @InjectMocks
    private ContractService contractService;

    @Mock
    private Web3j web3j;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contractService, "rpcUrl", "http://localhost:8545");
        ReflectionTestUtils.setField(contractService, "contractAddress", "0x5FbDB2315678afecb367f032d93F642f64180aa3");
        ReflectionTestUtils.setField(contractService, "privateKey", "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        
        contractService.init();
        ReflectionTestUtils.setField(contractService, "web3j", web3j);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyInvestorWhitelist_Success_True() throws IOException {
        // Given
        String walletAddress = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
        
        Request<?, EthCall> mockRequest = mock(Request.class);
        EthCall mockEthCall = mock(EthCall.class);
        
        when(mockEthCall.getValue()).thenReturn("0x0000000000000000000000000000000000000000000000000000000000000001");
        when(mockRequest.send()).thenReturn(mockEthCall);
        
        doReturn(mockRequest).when(web3j).ethCall(any(), any());

        // When
        boolean result = contractService.verifyInvestorWhitelist(walletAddress);

        // Then
        assertTrue(result);
        verify(web3j, times(1)).ethCall(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyInvestorWhitelist_Success_False() throws IOException {
        // Given
        String walletAddress = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
        
        Request<?, EthCall> mockRequest = mock(Request.class);
        EthCall mockEthCall = mock(EthCall.class);
        
        when(mockEthCall.getValue()).thenReturn("0x0000000000000000000000000000000000000000000000000000000000000000");
        when(mockRequest.send()).thenReturn(mockEthCall);
        
        doReturn(mockRequest).when(web3j).ethCall(any(), any());

        // When
        boolean result = contractService.verifyInvestorWhitelist(walletAddress);

        // Then
        assertFalse(result);
        verify(web3j, times(1)).ethCall(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void balanceOfByPartition_Success() throws IOException {
        // Given
        String walletAddress = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
        
        Request<?, EthCall> mockRequest = mock(Request.class);
        EthCall mockEthCall = mock(EthCall.class);
        
        // 100 tokens: 100 * 10^18 wei = 100000000000000000000 = 0x56BC75E2D63100000
        when(mockEthCall.getValue()).thenReturn("0x0000000000000000000000000000000000000000000000056bc75e2d63100000");
        when(mockRequest.send()).thenReturn(mockEthCall);
        
        doReturn(mockRequest).when(web3j).ethCall(any(), any());

        // When
        BigDecimal balance = contractService.balanceOfByPartition("APPL-STO", "DEFAULT", walletAddress);

        // Then
        assertEquals(0, balance.compareTo(BigDecimal.valueOf(100)));
        verify(web3j, times(1)).ethCall(any(), any());
    }
}
