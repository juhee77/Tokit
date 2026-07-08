package com.tokit.global.initializer;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.user.entity.InvestorType;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final WalletRepository walletRepository;
    private final IssuerRepository issuerRepository;
    private final ContractService contractService;
    private final EntityManager entityManager;

    private static final String[] SURNAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "신", "서"};
    private static final String[] NAMES = {"민준", "서준", "도윤", "예준", "시우", "하준", "주원", "지호", "지후", "준서", "지우", "지원", "수아", "채원"};
    
    private static final String[] REGIONS = {"서울 강남", "여의도", "부산 해운대", "제주 서귀포", "판교 테크노", "송도 국제", "인천 부평", "광주 상무", "대구 수성", "대전 둔산"};
    private static final String[] TYPES = {" 프라임 빌딩", " 신축 청년주택", " 친환경 태양광 발전소", " 오피스 콤플렉스", " 오션뷰 리조트", " 물류 가공 센터", " 스마트 데이터센터", " 미술품 소셜 펀드"};

    private static final String[] HARDHAT_ADDRESSES = {
        "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", 
        "0x70997970C51812dc3A010C7d01b50e0d17dc79C8", 
        "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC", 
        "0x90F79bf6EB2c4f870365E785982E1f101E93b906", 
        "0x15d34AAf54a67C681c20A7b1d023fA176B85994A", 
        "0x9965507D1a05cc24d47a04C7613736b1a294028e", 
        "0x976EA74026E726554dB657fa54763abd0C3a0aa9", 
        "0x14dC79964da2C08b23698B3D3cc7Ca32193d9955", 
        "0x23618e81E3f5cdF7f54C3d65f7FBc0aBf5B21E8f", 
        "0xa0Ee7A142d267C1f36714E4a8F75612F20a79720", 
        "0xBcd4042DE4c1257550358F81a6afE908959d9659", 
        "0xeA2D19D5184b2540D36c4664F96d091278C3D2c9", 
        "0x3CaE4f1a57C709719CCaE4f1a57C7097a8e2e92c", 
        "0xcd3B766CCDd6AE721141F452C550Ca635964ce71", 
        "0x2546BcD3c84621e976D8185a91A922aE77ECEc30", 
        "0xbDA5747bFD65F08deb54cb465eB87D40e51B197E", 
        "0xdD2FD4581271e230360230F9337D5c0430Bf44C0", 
        "0x8626f6940E2eb28930eFb4CeF49B2d1F2C9C1199"  
    };

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting Blockchain & Database Initialization...");

        // 0. Force insert default user (ID=1) '김토킷' via native query to prevent auto-increment offsets
        if (userRepository.findById(1L).isEmpty()) {
            log.info("Force inserting default user (ID=1) '김토킷' via native query...");
            entityManager.createNativeQuery(
                "INSERT INTO users (id, name, email, wallet_address, kyc_status, investor_type) " +
                "VALUES (1, '김토킷', 'test-investor@tokit.com', '0x70997970C51812dc3A010C7d01b50e0d17dc79C8', true, 'GENERAL')"
            ).executeUpdate();

            User defaultUser = userRepository.findById(1L).get();
            walletRepository.save(Wallet.builder()
                    .user(defaultUser)
                    .asset(null)
                    .balance(BigDecimal.valueOf(10000000.0))
                    .lockedBalance(BigDecimal.ZERO)
                    .build());

            // Check if HDYT asset exists to seed holding
            Asset hdytAsset = assetRepository.findBySymbol("HDYT").orElse(null);
            if (hdytAsset != null) {
                walletRepository.save(Wallet.builder()
                        .user(defaultUser)
                        .asset(hdytAsset)
                        .balance(BigDecimal.valueOf(1000.0))
                        .lockedBalance(BigDecimal.ZERO)
                        .build());
            }
        }
        
        Random rand = new Random(42); 

        List<Issuer> issuers = issuerRepository.findAll();
        if (issuers.isEmpty()) {
            issuers.add(issuerRepository.save(Issuer.builder().companyName("서울랜드트러스트").bizRegNo("123-45-67890").build()));
            issuers.add(issuerRepository.save(Issuer.builder().companyName("한국토지자산").bizRegNo("111-22-33333").build()));
            issuers.add(issuerRepository.save(Issuer.builder().companyName("성수소셜인베스트").bizRegNo("222-33-44444").build()));
            issuers.add(issuerRepository.save(Issuer.builder().companyName("부산해양홀딩스").bizRegNo("333-44-55555").build()));
        }

        List<Asset> assets = assetRepository.findAll();
        if (assets.size() < 10) {
            log.info("Asset database has less than 10 entries. Generating 100 bulk assets...");
            
            int currentSize = assets.size();
            for (int i = currentSize + 1; i <= 100; i++) {
                String region = REGIONS[rand.nextInt(REGIONS.length)];
                String type = TYPES[rand.nextInt(TYPES.length)];
                String assetName = region + type + " 제" + i + "호";
                String symbol = "STO-" + String.format("%03d", i);
                
                BigDecimal price = BigDecimal.valueOf((rand.nextInt(20) + 1) * 5000); 
                String status = rand.nextDouble() < 0.2 ? "청약중" : "거래중"; 
                String mockContractAddr = "0x" + String.format("%040x", i + 1000);

                Asset newAsset = assetRepository.save(Asset.builder()
                        .issuer(issuers.get(rand.nextInt(issuers.size())))
                        .name(assetName)
                        .symbol(symbol)
                        .contractAddress(mockContractAddr)
                        .totalSupply(BigDecimal.valueOf(1000000))
                        .issuePrice(price)
                        .status(status)
                        .build());
                assets.add(newAsset);
            }
            log.info("Successfully generated 100 assets in database.");
        }

        List<User> users = userRepository.findAll();
        if (users.size() < 10) {
            log.info("User database has less than 10 entries. Generating 100 bulk users...");
            int currentSize = users.size();
            
            for (int i = currentSize + 1; i <= 100; i++) {
                String name = SURNAMES[rand.nextInt(SURNAMES.length)] + NAMES[rand.nextInt(NAMES.length)] + i;
                String email = "user" + i + "@tokit.com";
                
                String walletAddr;
                if (i <= HARDHAT_ADDRESSES.length) {
                    walletAddr = HARDHAT_ADDRESSES[i - 1];
                } else {
                    walletAddr = "0x" + String.format("%040x", i + 5000);
                }

                double score = rand.nextDouble();
                InvestorType type = InvestorType.GENERAL;
                if (score > 0.9) {
                    type = InvestorType.PROFESSIONAL;
                } else if (score > 0.7) {
                    type = InvestorType.QUALIFIED;
                }

                User newUser = userRepository.save(User.builder()
                        .name(name)
                        .email(email)
                        .walletAddress(walletAddr)
                        .kycStatus(true)
                        .investorType(type)
                        .build());
                users.add(newUser);

                BigDecimal krwBalance = BigDecimal.valueOf((rand.nextInt(90) + 10) * 1000000L);
                walletRepository.save(Wallet.builder()
                        .user(newUser)
                        .asset(null)
                        .balance(krwBalance)
                        .lockedBalance(BigDecimal.ZERO)
                        .build());

                int holdingsCount = rand.nextInt(2) + 2; 
                for (int h = 0; h < holdingsCount; h++) {
                    Asset randomAsset = assets.get(rand.nextInt(assets.size()));
                    if ("거래중".equals(randomAsset.getStatus())) {
                        BigDecimal tokenBalance = BigDecimal.valueOf(rand.nextInt(49) * 10 + 10);
                        walletRepository.save(Wallet.builder()
                                .user(newUser)
                                .asset(randomAsset)
                                .balance(tokenBalance)
                                .lockedBalance(BigDecimal.ZERO)
                                .build());
                    }
                }
            }
            log.info("Successfully generated 100 users and portfolios in database.");
        }

        log.info("Starting on-chain whitelist synchronization...");
        for (User user : users) {
            String addr = user.getWalletAddress();
            boolean isHardhatAddr = false;
            for (String hAddr : HARDHAT_ADDRESSES) {
                if (hAddr.equalsIgnoreCase(addr)) {
                    isHardhatAddr = true;
                    break;
                }
            }
            if (isHardhatAddr) {
                try {
                    if (!contractService.verifyInvestorWhitelist(addr)) {
                        contractService.addToWhitelist(addr);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync whitelist for address: " + addr, e);
                }
            }
        }
        log.info("Whitelist synchronization completed.");
    }
}
