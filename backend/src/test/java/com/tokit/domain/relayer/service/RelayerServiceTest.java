package com.tokit.domain.relayer.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.relayer.entity.RelayerNonce;
import com.tokit.domain.relayer.repository.RelayerNonceRepository;
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
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelayerServiceTest {

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
    private Wallet senderWallet;
    private Wallet receiverWallet;
    private ECKeyPair senderKeyPair;
    private String senderWalletAddress;
    private String receiverWalletAddress = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC";
    private String senderPrivateKeyHex = "0x8f3b5153e3900000000000000000000000000000000000000000000000000000";

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String createEthereumSignature(String message, ECKeyPair keyPair) {
        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.getBytes(StandardCharsets.UTF_8).length).getBytes(StandardCharsets.UTF_8);
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] fullMessage = new byte[prefix.length + msgBytes.length];
        System.arraycopy(prefix, 0, fullMessage, 0, prefix.length);
        System.arraycopy(msgBytes, 0, fullMessage, prefix.length, msgBytes.length);

        byte[] messageHash = Hash.sha3(fullMessage);
        Sign.SignatureData sigData = Sign.signMessage(messageHash, keyPair, false);

        byte[] sigBytes = new byte[65];
        System.arraycopy(sigData.getR(), 0, sigBytes, 0, 32);
        System.arraycopy(sigData.getS(), 0, sigBytes, 32, 32);
        sigBytes[64] = sigData.getV()[0];

        return Numeric.toHexString(sigBytes);
    }

    @BeforeEach
    void setUp() throws Exception {
        senderKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(senderPrivateKeyHex.replace("0x", "")));
        senderWalletAddress = "0x" + Keys.getAddress(senderKeyPair);

        sender = User.builder()
                .name("Gasless Sender")
                .email("sender.gasless@tokit.com")
                .walletAddress(senderWalletAddress)
                .build();
        setField(sender, "id", 10L);

        receiver = User.builder()
                .name("Gasless Receiver")
                .email("receiver.gasless@tokit.com")
                .walletAddress(receiverWalletAddress)
                .build();
        setField(receiver, "id", 20L);

        testAsset = Asset.builder()
                .name("Samsung Gangnam Building STO")
                .symbol("SAMSUNG-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 100L);

        senderWallet = Wallet.builder()
                .user(sender)
                .asset(testAsset)
                .balance(new BigDecimal("100")) // 100 STO 보유
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(senderWallet, "id", 500L);

        receiverWallet = Wallet.builder()
                .user(receiver)
                .asset(testAsset)
                .balance(new BigDecimal("20"))
                .lockedBalance(BigDecimal.ZERO)
                .build();
        setField(receiverWallet, "id", 501L);
    }

    @Test
    @DisplayName("verifySignatureAndTransfer: 올바른 서명과 논스(0) 제출 시 토큰 잔고 차감 및 온체인 forceTransferByPartition 대납이 실행된다.")
    void verifySignatureAndTransfer_Success() throws Exception {
        // Given
        Long nonce = 0L;
        BigDecimal transferAmount = new BigDecimal("30");

        String plainMessage = senderWalletAddress.toLowerCase() + ":" +
                receiverWalletAddress.toLowerCase() + ":" +
                "SAMSUNG-STO:" +
                transferAmount.stripTrailingZeros().toPlainString() + ":" +
                nonce;

        String signature = createEthereumSignature(plainMessage, senderKeyPair);

        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress(senderWalletAddress.toLowerCase())
                .nextNonce(0L)
                .build();

        when(relayerNonceRepository.findById(senderWalletAddress.toLowerCase())).thenReturn(Optional.of(relayerNonce));
        when(assetRepository.findBySymbol("SAMSUNG-STO")).thenReturn(Optional.of(testAsset));
        when(userRepository.findByWalletAddressIgnoreCase(senderWalletAddress)).thenReturn(Optional.of(sender));
        when(userRepository.findByWalletAddressIgnoreCase(receiverWalletAddress)).thenReturn(Optional.of(receiver));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(10L, 100L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(20L, 100L)).thenReturn(Optional.of(receiverWallet));

        // When
        relayerService.verifySignatureAndTransfer(senderWalletAddress, receiverWalletAddress, "SAMSUNG-STO", transferAmount, nonce, signature);

        // Then
        // 1. 송신자 잔고 100 -> 70, 수신자 잔고 20 -> 50
        assertThat(senderWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("70").stripTrailingZeros());
        assertThat(receiverWallet.getBalance().stripTrailingZeros()).isEqualTo(new BigDecimal("50").stripTrailingZeros());

        // 2. 논스 +1 증가
        assertThat(relayerNonce.getNextNonce()).isEqualTo(1L);

        // 3. 온체인 대납 계약 실행
        verify(contractService, times(1)).handleTransferByPartition(
                eq("0x5FbDB2315678afecb367f032d93F642f64180aa3"),
                eq("SAMSUNG-STO"),
                eq("DEFAULT"),
                eq(senderWalletAddress),
                eq(receiverWalletAddress),
                eq(transferAmount)
        );
    }


    @Test
    @DisplayName("verifySignatureAndTransfer: 기대 논스(0)와 불일치 시 예외를 던진다.")
    void verifySignatureAndTransfer_InvalidNonce_ThrowsException() {
        // Given
        Long wrongNonce = 99L;
        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress(senderWalletAddress.toLowerCase())
                .nextNonce(0L)
                .build();

        when(relayerNonceRepository.findById(senderWalletAddress.toLowerCase())).thenReturn(Optional.of(relayerNonce));

        // When & Then
        assertThatThrownBy(() -> relayerService.verifySignatureAndTransfer(
                senderWalletAddress, receiverWalletAddress, "SAMSUNG-STO", new BigDecimal("10"), wrongNonce, "0xINVALID"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("유효하지 않은 논스 값입니다.");
    }
}
