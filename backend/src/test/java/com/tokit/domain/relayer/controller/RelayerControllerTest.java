package com.tokit.domain.relayer.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.relayer.entity.RelayerNonce;
import com.tokit.domain.relayer.repository.RelayerNonceRepository;
import com.tokit.domain.relayer.service.RelayerService;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelayerControllerTest {

    @InjectMocks
    private RelayerService relayerService;

    @Mock
    private RelayerNonceRepository relayerNonceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ContractService contractService;

    private User sender;
    private User receiver;
    private Asset testAsset;
    private ECKeyPair senderKeyPair;
    private String senderPrivateKeyHex = "0x8f3b5153e3900000000000000000000000000000000000000000000000000000";

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        senderKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(senderPrivateKeyHex.replace("0x", "")));
        String senderWalletAddress = "0x" + Keys.getAddress(senderKeyPair);

        sender = User.builder()
                .name("Sender User")
                .email("sender@test.com")
                .walletAddress(senderWalletAddress)
                .build();
        setField(sender, "id", 1L);

        receiver = User.builder()
                .name("Receiver User")
                .email("receiver@test.com")
                .walletAddress("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC")
                .build();
        setField(receiver, "id", 2L);

        testAsset = Asset.builder()
                .name("테스트 빌딩 STO")
                .symbol("TEST-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(BigDecimal.valueOf(100000))
                .issuePrice(BigDecimal.valueOf(1000))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 100L);
    }

    @Test
    @DisplayName("논스가 존재하지 않는 지갑일 시 0으로 정상 반환 및 저장되어야 한다.")
    void getOrCreateNonce_NewAddress_ReturnsZero() {
        // Given
        String address = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
        when(relayerNonceRepository.findById(address.toLowerCase())).thenReturn(Optional.empty());

        // When
        Long nonce = relayerService.getOrCreateNonce(address);

        // Then
        assertEquals(0L, nonce);
        verify(relayerNonceRepository, times(1)).save(any(RelayerNonce.class));
    }

    @Test
    @DisplayName("올바른 서명과 논스 데이터로 가스비 대납 이체 요청 시 정상 수행되어야 한다.")
    void verifySignatureAndTransfer_Success() throws Exception {
        // Given
        Long nonce = 0L;
        BigDecimal amount = BigDecimal.valueOf(100);

        String plainMessage = sender.getWalletAddress().toLowerCase() + ":" +
                receiver.getWalletAddress().toLowerCase() + ":" +
                testAsset.getSymbol() + ":" +
                amount.stripTrailingZeros().toPlainString() + ":" +
                nonce;

        byte[] msgBytes = plainMessage.getBytes(StandardCharsets.UTF_8);
        String prefix = "\u0019Ethereum Signed Message:\n" + msgBytes.length;
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] totalMsg = new byte[prefixBytes.length + msgBytes.length];
        System.arraycopy(prefixBytes, 0, totalMsg, 0, prefixBytes.length);
        System.arraycopy(msgBytes, 0, totalMsg, prefixBytes.length, msgBytes.length);
        byte[] messageHash = Hash.sha3(totalMsg);

        Sign.SignatureData signatureData = Sign.signMessage(messageHash, senderKeyPair, false);
        byte[] retval = new byte[65];
        System.arraycopy(signatureData.getR(), 0, retval, 0, 32);
        System.arraycopy(signatureData.getS(), 0, retval, 32, 32);
        retval[64] = signatureData.getV()[0];
        String hexSignature = Numeric.toHexString(retval);

        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress(sender.getWalletAddress())
                .nextNonce(0L)
                .build();

        Wallet senderWallet = Wallet.builder().user(sender).asset(testAsset).balance(BigDecimal.valueOf(500)).lockedBalance(BigDecimal.ZERO).build();
        Wallet receiverWallet = Wallet.builder().user(receiver).asset(testAsset).balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build();

        when(relayerNonceRepository.findById(sender.getWalletAddress().toLowerCase())).thenReturn(Optional.of(relayerNonce));
        when(assetRepository.findBySymbol(testAsset.getSymbol())).thenReturn(Optional.of(testAsset));
        when(userRepository.findByWalletAddressIgnoreCase(sender.getWalletAddress())).thenReturn(Optional.of(sender));
        when(userRepository.findByWalletAddressIgnoreCase(receiver.getWalletAddress())).thenReturn(Optional.of(receiver));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(sender.getId(), testAsset.getId())).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(receiver.getId(), testAsset.getId())).thenReturn(Optional.of(receiverWallet));

        // When
        assertDoesNotThrow(() -> {
            relayerService.verifySignatureAndTransfer(
                    sender.getWalletAddress(),
                    receiver.getWalletAddress(),
                    testAsset.getSymbol(),
                    amount,
                    nonce,
                    hexSignature
            );
        });

        // Then
        assertEquals(1L, relayerNonce.getNextNonce()); 
        assertEquals(BigDecimal.valueOf(400), senderWallet.getBalance()); 
        assertEquals(BigDecimal.valueOf(100), receiverWallet.getBalance()); 
        verify(contractService, times(1)).handleTransferByPartition(
                testAsset.getContractAddress(),
                testAsset.getSymbol(),
                "DEFAULT",
                sender.getWalletAddress(),
                receiver.getWalletAddress(),
                amount
        );
    }

    @Test
    @DisplayName("일일 릴레이 한도(5회)를 이미 소진한 지갑의 요청은 에러를 던져야 한다.")
    void verifySignatureAndTransfer_RateLimitExceeded() throws Exception {
        // Given
        Long nonce = 0L;
        BigDecimal amount = BigDecimal.valueOf(100);

        String plainMessage = sender.getWalletAddress().toLowerCase() + ":" +
                receiver.getWalletAddress().toLowerCase() + ":" +
                testAsset.getSymbol() + ":" +
                amount.stripTrailingZeros().toPlainString() + ":" +
                nonce;

        byte[] msgBytes = plainMessage.getBytes(StandardCharsets.UTF_8);
        String prefix = "\u0019Ethereum Signed Message:\n" + msgBytes.length;
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] totalMsg = new byte[prefixBytes.length + msgBytes.length];
        System.arraycopy(prefixBytes, 0, totalMsg, 0, prefixBytes.length);
        System.arraycopy(msgBytes, 0, totalMsg, prefixBytes.length, msgBytes.length);
        byte[] messageHash = Hash.sha3(totalMsg);

        Sign.SignatureData signatureData = Sign.signMessage(messageHash, senderKeyPair, false);
        byte[] retval = new byte[65];
        System.arraycopy(signatureData.getR(), 0, retval, 0, 32);
        System.arraycopy(signatureData.getS(), 0, retval, 32, 32);
        retval[64] = signatureData.getV()[0];
        String hexSignature = Numeric.toHexString(retval);

        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress(sender.getWalletAddress())
                .nextNonce(0L)
                .lastTxDate(java.time.LocalDate.now())
                .dailyTxCount(5)
                .build();

        Wallet senderWallet = Wallet.builder().user(sender).asset(testAsset).balance(BigDecimal.valueOf(500)).lockedBalance(BigDecimal.ZERO).build();

        when(relayerNonceRepository.findById(sender.getWalletAddress().toLowerCase())).thenReturn(Optional.of(relayerNonce));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            relayerService.verifySignatureAndTransfer(
                    sender.getWalletAddress(),
                    receiver.getWalletAddress(),
                    testAsset.getSymbol(),
                    amount,
                    nonce,
                    hexSignature
            );
        });

        assertEquals("일일 대납 이체 한도(5회)를 초과했습니다.", exception.getMessage());
    }
}
