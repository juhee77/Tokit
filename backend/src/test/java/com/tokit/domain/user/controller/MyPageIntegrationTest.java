package com.tokit.domain.user.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MyPageIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

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

    private User testUser;
    private User counterParty;
    private Asset testAsset;
    private Issuer testIssuer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Setup User
        testUser = userRepository.save(User.builder()
                .name("Gildong Hong")
                .email("gildong@tokit.com")
                .walletAddress("0xGildongAddress")
                .kycStatus(true)
                .build());

        counterParty = userRepository.save(User.builder()
                .name("Cheolsu Kim")
                .email("cheolsu@tokit.com")
                .walletAddress("0xCheolsuAddress")
                .kycStatus(true)
                .build());

        // Setup Issuer
        testIssuer = issuerRepository.save(Issuer.builder()
                .companyName("Korea Land Trust")
                .bizRegNo("999-99-99999")
                .build());

        // Setup Asset
        testAsset = assetRepository.save(Asset.builder()
                .issuer(testIssuer)
                .name("Seoul Office STO")
                .symbol("SO-STO")
                .contractAddress("0x서울빌딩주소")
                .totalSupply(BigDecimal.valueOf(100000))
                .issuePrice(BigDecimal.valueOf(10000))
                .status("ACTIVE")
                .build());

        // Setup Wallets
        walletRepository.save(Wallet.builder()
                .user(testUser)
                .asset(null) // KRW
                .balance(BigDecimal.valueOf(5000000))
                .lockedBalance(BigDecimal.valueOf(1000000))
                .build());

        walletRepository.save(Wallet.builder()
                .user(testUser)
                .asset(testAsset) // SO-STO
                .balance(BigDecimal.valueOf(200))
                .lockedBalance(BigDecimal.valueOf(50))
                .build());

        // Setup Orders
        Order buyOrder = orderRepository.save(Order.builder()
                .user(testUser)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(BigDecimal.valueOf(10000))
                .quantity(BigDecimal.valueOf(10))
                .remainQty(BigDecimal.valueOf(5))
                .status(OrderStatus.PARTIAL)
                .createdAt(LocalDateTime.now())
                .build());

        Order sellOrder = orderRepository.save(Order.builder()
                .user(counterParty)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(BigDecimal.valueOf(10000))
                .quantity(BigDecimal.valueOf(10))
                .remainQty(BigDecimal.valueOf(5))
                .status(OrderStatus.PARTIAL)
                .createdAt(LocalDateTime.now())
                .build());

        // Setup Trades
        tradeRepository.save(Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(testAsset)
                .price(BigDecimal.valueOf(10000))
                .quantity(BigDecimal.valueOf(5))
                .tradedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("마이페이지 통합 조회: 사용자의 프로필, 지갑 목록, 주문 내역, 체결 내역을 일괄 반환한다.")
    void getMyPage() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getId() + "/mypage")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.message", is("SUCCESS")))
                .andExpect(jsonPath("$.data.user.name", is("Gildong Hong")))
                .andExpect(jsonPath("$.data.user.email", is("gildong@tokit.com")))
                .andExpect(jsonPath("$.data.user.kycStatus", is(true)))
                .andExpect(jsonPath("$.data.wallets", hasSize(2)))
                .andExpect(jsonPath("$.data.orders", hasSize(1)))
                .andExpect(jsonPath("$.data.orders[0].assetSymbol", is("SO-STO")))
                .andExpect(jsonPath("$.data.trades", hasSize(1)))
                .andExpect(jsonPath("$.data.trades[0].assetSymbol", is("SO-STO")))
                .andExpect(jsonPath("$.data.trades[0].price", is(10000)));
    }
}
