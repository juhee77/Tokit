package com.tokit.domain.order.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
class OrderMatchingConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private IssuerRepository issuerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @MockitoBean
    private ContractService contractService;

    private Asset testAsset;
    private List<User> buyers = new ArrayList<>();
    private List<User> sellers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. Clean up database
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
        assetRepository.deleteAll();
        issuerRepository.deleteAll();
        userRepository.deleteAll();

        // Mock blockchain operations to prevent actual RPC calls during concurrent matching test
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        // 2. Setup Issuer & Asset (status: "거래중")
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("Test Concurrency Issuer")
                .bizRegNo("123-CONCURRENCY")
                .build());

        testAsset = assetRepository.save(Asset.builder()
                .issuer(issuer)
                .name("Test Concurrency Asset")
                .symbol("TEST-CONCUR")
                .totalSupply(BigDecimal.valueOf(1000000))
                .issuePrice(BigDecimal.valueOf(10000))
                .status("거래중")
                .contractAddress("0xTestContractAddress")
                .build());

        // 3. Create 10 Buyers & 10 Sellers
        for (int i = 1; i <= 10; i++) {
            User buyer = userRepository.save(User.builder()
                    .name("Buyer " + i)
                    .email("buyer" + i + "@test.com")
                    .walletAddress("0xBuyerWalletAddress" + i)
                    .kycStatus(true)
                    .build());
            buyers.add(buyer);

            // Give buyer 100,000 KRW
            walletRepository.save(Wallet.builder()
                    .user(buyer)
                    .asset(null)
                    .balance(BigDecimal.valueOf(100000))
                    .lockedBalance(BigDecimal.ZERO)
                    .build());

            User seller = userRepository.save(User.builder()
                    .name("Seller " + i)
                    .email("seller" + i + "@test.com")
                    .walletAddress("0xSellerWalletAddress" + i)
                    .kycStatus(true)
                    .build());
            sellers.add(seller);

            // Give seller 10 tokens of TEST-CONCUR
            walletRepository.save(Wallet.builder()
                    .user(seller)
                    .asset(testAsset)
                    .balance(BigDecimal.valueOf(10))
                    .lockedBalance(BigDecimal.ZERO)
                    .build());

            // Give seller 0 KRW initial wallet
            walletRepository.save(Wallet.builder()
                    .user(seller)
                    .asset(null)
                    .balance(BigDecimal.ZERO)
                    .lockedBalance(BigDecimal.ZERO)
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletRepository.deleteAll();
        assetRepository.deleteAll();
        issuerRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("동시성 테스트: 10명의 매수자와 10명의 매도자가 동시에 주문을 제출할 때 정합성이 보장되어야 한다.")
    void concurrentOrderPlacementAndMatching() throws InterruptedException {
        int orderCount = 10; // 10 buyers and 10 sellers
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(orderCount * 2);

        // When: Submit orders with a tiny delay to ensure database transactions commit before opposite orders match.
        for (int i = 0; i < orderCount; i++) {
            final User buyer = buyers.get(i);
            final User seller = sellers.get(i);

            // Place BUY order: 5 tokens at 10,000 KRW (Cost = 50,000 KRW)
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(
                            buyer.getId(),
                            testAsset.getSymbol(),
                            OrderType.BUY,
                            BigDecimal.valueOf(10000),
                            BigDecimal.valueOf(5)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });

            Thread.sleep(50); // Give database transaction time to commit

            // Place SELL order: 5 tokens at 10,000 KRW
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(
                            seller.getId(),
                            testAsset.getSymbol(),
                            OrderType.SELL,
                            BigDecimal.valueOf(10000),
                            BigDecimal.valueOf(5)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });

            Thread.sleep(50); // Give database transaction time to commit
        }

        latch.await();

        // Wait for RabbitMQ asynchronous processing to complete matching and saving trades.
        // We expect exactly 10 trades (each seller matched with one buyer or similar combination).
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds timeout
        while (tradeRepository.count() < 10 && (System.currentTimeMillis() - startTime) < timeout) {
            Thread.sleep(200);
        }

        // Then:
        List<Trade> trades = tradeRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        System.out.println("DEBUG: Trades count = " + trades.size());
        System.out.println("DEBUG: Orders count = " + orders.size());

        // 1. Verify all 10 trades are saved successfully
        assertThat(trades).hasSize(10);

        // 2. Verify all orders are completely filled (status = FILLED, remain_qty = 0)
        assertThat(orders).hasSize(20);
        for (Order order : orders) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(order.getRemainingQuantity().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO.stripTrailingZeros());
        }

        // 3. Verify final wallet balances
        // Total KRW initial = 10 buyers * 100,000 = 1,000,000 KRW.
        // Total Token initial = 10 sellers * 10 = 100 tokens.
        // After trade:
        // Each buyer bought 5 tokens for 50,000 KRW.
        // Expected buyer balance: 50,000 KRW, 5 tokens.
        // Each seller sold 5 tokens for 50,000 KRW.
        // Expected seller balance: 50,000 KRW, 5 tokens.

        BigDecimal totalKrw = BigDecimal.ZERO;
        BigDecimal totalTokens = BigDecimal.ZERO;

        for (User buyer : buyers) {
            Wallet krwWallet = walletRepository.findKrwWalletByUserId(buyer.getId()).orElseThrow();
            Wallet tokenWallet = walletRepository.findByUserIdAndAssetId(buyer.getId(), testAsset.getId()).orElseThrow();

            assertThat(krwWallet.getBalance().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(50000).stripTrailingZeros());
            assertThat(krwWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO.stripTrailingZeros());
            assertThat(tokenWallet.getBalance().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(5).stripTrailingZeros());

            totalKrw = totalKrw.add(krwWallet.getBalance());
            totalTokens = totalTokens.add(tokenWallet.getBalance());
        }

        for (User seller : sellers) {
            Wallet krwWallet = walletRepository.findKrwWalletByUserId(seller.getId()).orElseThrow();
            Wallet tokenWallet = walletRepository.findByUserIdAndAssetId(seller.getId(), testAsset.getId()).orElseThrow();

            assertThat(krwWallet.getBalance().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(50000).stripTrailingZeros());
            assertThat(krwWallet.getLockedBalance().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO.stripTrailingZeros());
            assertThat(tokenWallet.getBalance().stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(5).stripTrailingZeros());

            totalKrw = totalKrw.add(krwWallet.getBalance());
            totalTokens = totalTokens.add(tokenWallet.getBalance());
        }

        // 4. Verify conservation of money and assets
        assertThat(totalKrw.stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(1000000).stripTrailingZeros());
        assertThat(totalTokens.stripTrailingZeros()).isEqualTo(BigDecimal.valueOf(100).stripTrailingZeros());
    }
}
