package com.tokit.domain.relayer.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.relayer.entity.RelayerNonce;
import com.tokit.domain.relayer.repository.RelayerNonceRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 같은 메타 트랜잭션 서명이 동시에 여러 번 들어와도 논스 행 잠금 덕분에 한 번만 실행되는지 확인한다.
 * 잠금이 없으면 모든 요청이 nextNonce == 0 을 동시에 읽고 통과해 이체가 여러 번 일어난다.
 */
@SpringBootTest
class RelayerNonceConcurrencyTest {

    private static final int THREADS = 10;
    private static final BigDecimal AMOUNT = BigDecimal.TEN;
    private static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(1_000);

    @Autowired private RelayerService relayerService;
    @Autowired private RelayerNonceRepository relayerNonceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;

    @MockitoBean private ContractService contractService;

    private ECKeyPair senderKeyPair;
    private User sender;
    private User receiver;
    private Asset asset;
    private Issuer issuer;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        senderKeyPair = Keys.createEcKeyPair();
        ECKeyPair receiverKeyPair = Keys.createEcKeyPair();

        issuer = issuerRepository.save(Issuer.builder()
                .companyName("Relayer Race Issuer")
                .bizRegNo("relay-" + uid)
                .build());
        asset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Relayer Race Asset " + uid)
                .symbol("RLY-" + uid.substring(0, 5))
                .totalSupply(INITIAL_BALANCE)
                .issuePrice(BigDecimal.valueOf(100))
                .status("거래중")
                .contractAddress("0xRelayerRaceTestContract")
                .build());

        sender = newUser("sender-" + uid, "0x" + Keys.getAddress(senderKeyPair));
        receiver = newUser("receiver-" + uid, "0x" + Keys.getAddress(receiverKeyPair));
        senderWallet = walletRepository.save(Wallet.builder().user(sender).asset(asset)
                .balance(INITIAL_BALANCE).lockedBalance(BigDecimal.ZERO).build());
        receiverWallet = walletRepository.save(Wallet.builder().user(receiver).asset(asset)
                .balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO).build());

        relayerNonceRepository.save(RelayerNonce.builder()
                .walletAddress(sender.getWalletAddress())
                .nextNonce(0L)
                .build());
    }

    @AfterEach
    void tearDown() {
        relayerNonceRepository.deleteById(sender.getWalletAddress().toLowerCase());
        walletRepository.deleteAll(walletRepository.findAllById(
                List.of(senderWallet.getId(), receiverWallet.getId())));
        userRepository.deleteAll(List.of(sender, receiver));
        assetRepository.delete(asset);
        issuerRepository.delete(issuer);
    }

    @Test
    @DisplayName("리플레이: 같은 서명으로 동시에 요청해도 한 건만 이체되고 논스는 1만 증가해야 한다.")
    void sameSignatureConcurrentRequests_OnlyOneSucceeds() throws InterruptedException {
        // Given
        long nonce = 0L;
        String from = sender.getWalletAddress();
        String to = receiver.getWalletAddress();
        String signature = sign(from.toLowerCase() + ":" + to.toLowerCase() + ":" + asset.getSymbol() + ":"
                + AMOUNT.stripTrailingZeros().toPlainString() + ":" + nonce);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        // When
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    relayerService.verifySignatureAndTransfer(from, to, asset.getSymbol(), AMOUNT, nonce, signature);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Then
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(THREADS - 1);

        RelayerNonce relayerNonce = relayerNonceRepository.findById(from.toLowerCase()).orElseThrow();
        assertThat(relayerNonce.getNextNonce()).isEqualTo(1L);
        assertThat(relayerNonce.getDailyTxCount()).isEqualTo(1);

        assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(INITIAL_BALANCE.subtract(AMOUNT));
        assertThat(walletRepository.findById(receiverWallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(AMOUNT);
        verify(contractService, times(1)).handleTransferByPartition(any(), any(), any(), any(), any(), any());
    }

    private User newUser(String tag, String walletAddress) {
        return userRepository.save(User.builder()
                .name(tag)
                .email(tag + "@test.com")
                .password("{noop}test-password")
                .walletAddress(walletAddress)
                .kycStatus(true)
                .build());
    }

    private String sign(String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + msgBytes.length).getBytes(StandardCharsets.UTF_8);
        byte[] fullMessage = new byte[prefix.length + msgBytes.length];
        System.arraycopy(prefix, 0, fullMessage, 0, prefix.length);
        System.arraycopy(msgBytes, 0, fullMessage, prefix.length, msgBytes.length);

        Sign.SignatureData sigData = Sign.signMessage(Hash.sha3(fullMessage), senderKeyPair, false);
        byte[] sigBytes = new byte[65];
        System.arraycopy(sigData.getR(), 0, sigBytes, 0, 32);
        System.arraycopy(sigData.getS(), 0, sigBytes, 32, 32);
        sigBytes[64] = sigData.getV()[0];
        return Numeric.toHexString(sigBytes);
    }
}
