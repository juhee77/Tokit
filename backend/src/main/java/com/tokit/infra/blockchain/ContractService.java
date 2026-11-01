package com.tokit.infra.blockchain;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ContractService {

    @Value("${tokit.blockchain.rpc-url}")
    private String rpcUrl;

    @Value("${tokit.blockchain.contract-address}")
    private String contractAddress;

    @Value("${tokit.blockchain.private-key}")
    private String privateKey;

    private Web3j web3j;
    private Credentials credentials;

    /** 코드가 있는 것으로 확인된 컨트랙트 주소(소문자). 배포된 코드는 사라지지 않으므로 확인은 주소당 한 번이면 된다. */
    private final Set<String> deployedContracts = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        log.info("Initializing Web3j connection to: {}", rpcUrl);
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.credentials = Credentials.create(privateKey);
        log.info("Contract Service initialized with owner wallet address: {}", credentials.getAddress());
    }

    /**
     * STO 토큰증권 배포 후 백엔드 데이터베이스 동기화 및 유효성 검사
     * 실제 블록체인 상의 isWhitelisted(address) 호출
     */
    public boolean verifyInvestorWhitelist(String walletAddress) {
        log.info("Checking investor whitelist on blockchain for address: {}", walletAddress);
        try {
            Function function = new Function(
                    "isWhitelisted",
                    Arrays.asList(new Address(walletAddress)),
                    Arrays.asList(new TypeReference<Bool>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            contractAddress,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (results.isEmpty()) {
                log.warn("Whitelist check response is empty for address: {}", walletAddress);
                return false;
            }
            return (boolean) results.get(0).getValue();
        } catch (Exception e) {
            log.error("Failed to check whitelist on blockchain for address: " + walletAddress, e);
            return false;
        }
    }

    /**
     * 화이트리스트 주소 추가 (온체인 트랜잭션 전송)
     */
    public void addToWhitelist(String walletAddress) {
        log.info("Adding address to blockchain whitelist: {}", walletAddress);
        try {
            Function function = new Function(
                    "addToWhitelist",
                    Arrays.asList(new Address(walletAddress)),
                    Arrays.asList()
            );

            String encodedFunction = FunctionEncoder.encode(function);
            TransactionManager txManager = new RawTransactionManager(web3j, credentials);
            
            BigInteger gasPrice = estimateGasPriceWithBuffer();
            BigInteger gasLimit = BigInteger.valueOf(500_000L);

            EthSendTransaction response = txManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (response.hasError()) {
                log.error("Blockchain addToWhitelist TX failed: {}", response.getError().getMessage());
                throw new RuntimeException("Blockchain TX Error: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("Sent addToWhitelist TX. TxHash: {}", txHash);

            TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 1000, 15);
            TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(txHash);
            
            if (receipt.isStatusOK()) {
                log.info("Address successfully whitelisted on-chain.");
            } else {
                throw new RuntimeException("Blockchain TX reverted");
            }
        } catch (Exception e) {
            log.error("Failed to whitelist address on blockchain: " + walletAddress, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 화이트리스트 주소 제거 (온체인 트랜잭션 전송)
     */
    public void removeFromWhitelist(String walletAddress) {
        log.info("Removing address from blockchain whitelist: {}", walletAddress);
        try {
            Function function = new Function(
                    "removeFromWhitelist",
                    Arrays.asList(new Address(walletAddress)),
                    Arrays.asList()
            );

            String encodedFunction = FunctionEncoder.encode(function);
            TransactionManager txManager = new RawTransactionManager(web3j, credentials);
            
            BigInteger gasPrice = estimateGasPriceWithBuffer();
            BigInteger gasLimit = BigInteger.valueOf(500_000L);

            EthSendTransaction response = txManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (response.hasError()) {
                log.error("Blockchain removeFromWhitelist TX failed: {}", response.getError().getMessage());
                throw new RuntimeException("Blockchain TX Error: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("Sent removeFromWhitelist TX. TxHash: {}", txHash);

            TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 1000, 15);
            TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(txHash);
            
            if (receipt.isStatusOK()) {
                log.info("Address successfully removed from whitelist on-chain.");
            } else {
                throw new RuntimeException("Blockchain TX reverted");
            }
        } catch (Exception e) {
            log.error("Failed to remove address from whitelist on blockchain: " + walletAddress, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 블록체인 네트워크 상에서 STO 토큰 전송(Partition-based transfer) 이벤트를 처리
     * 여기서는 Admin(Owner) 권한으로 forceTransferByPartition을 직접 날려서 온체인 잔고 동기화를 수행
     *
     * <p>종목마다 컨트랙트가 다르므로 대상 주소를 반드시 받는다. 설정의 기본 주소로 보내면
     * 다른 종목의 토큰이 이체된다. (#29)
     */
    @Retryable(
            retryFor = BlockchainException.class,
            noRetryFor = ContractNotDeployedException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void handleTransferByPartition(String targetContractAddress, String symbol, String partition, String from, String to, BigDecimal amount) {
        log.info("Executing Admin Force Partition Transfer. TargetContract: {}, Symbol: {}, Partition: {}, From: {}, To: {}, Amount: {}",
                targetContractAddress, symbol, partition, from, to, amount);
        
        try {
            requireDeployedContract(targetContractAddress);

            // partition string -> bytes32
            byte[] partitionBytes = new byte[32];
            byte[] sourceBytes = partition.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(sourceBytes, 0, partitionBytes, 0, Math.min(sourceBytes.length, 32));

            // amount를 18자리 decimals uint256으로 변환
            BigInteger value = amount.multiply(BigDecimal.valueOf(10).pow(18)).toBigInteger();

            // forceTransferByPartition(bytes32 partition, address from, address to, uint256 value, bytes calldata data)
            Function function = new Function(
                    "forceTransferByPartition",
                    Arrays.asList(
                            new Bytes32(partitionBytes),
                            new Address(from),
                            new Address(to),
                            new Uint256(value),
                            new DynamicBytes(new byte[0])
                    ),
                    Arrays.asList(new TypeReference<Bytes32>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);

            TransactionManager txManager = new RawTransactionManager(web3j, credentials);
            
            BigInteger gasPrice = estimateGasPriceWithBuffer();
            BigInteger gasLimit = BigInteger.valueOf(3_000_000L); // 넉넉히 가스 배정

            EthSendTransaction response = txManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    targetContractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (response.hasError()) {
                log.error("Blockchain force transfer TX failed: {}", response.getError().getMessage());
                throw new BlockchainException("Blockchain TX Error: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();
            log.info("Sent forceTransferByPartition TX. TxHash: {}", txHash);

            // 트랜잭션 수신 완료 대기 (최대 15초)
            TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 1000, 15);
            TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(txHash);
            
            if (receipt.isStatusOK()) {
                log.info("Force Partition Transfer completed on blockchain. Block: {}, Gas used: {}", 
                        receipt.getBlockNumber(), receipt.getGasUsed());
            } else {
                log.error("Force Partition Transfer transaction status is failed (reverted). Receipt: {}", receipt);
                throw new BlockchainException("Blockchain TX reverted");
            }

        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute forceTransferByPartition on blockchain", e);
            throw new BlockchainException("Failed to execute forceTransferByPartition", e);
        }
    }

    /**
     * 블록체인 노드 연결 상태 점검. 헬스체크 용도이며 트랜잭션을 발생시키지 않습니다.
     */
    public NodeStatus checkNodeStatus() {
        try {
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            return new NodeStatus(true, clientVersion, blockNumber, null);
        } catch (Exception e) {
            log.warn("Blockchain node status check failed: {}", e.getMessage());
            return new NodeStatus(false, null, null, e.getMessage());
        }
    }

    public record NodeStatus(boolean reachable, String clientVersion, BigInteger blockNumber, String error) {}

    /**
     * 특정 주주(지갑)의 온체인 파티션 토큰 잔고 조회
     *
     * <p>조회에 실패하면 0이 아니라 {@link BlockchainException}을 던진다. 0을 돌려주면 노드 순단 시
     * 모든 주주의 온체인 잔고가 0으로 읽혀, 대사 배치가 전 주주를 불일치로 경보한다. (#29)
     */
    public BigDecimal balanceOfByPartition(String targetContractAddress, String partition, String walletAddress) {
        log.info("Checking on-chain balance. Contract: {}, address: {}, partition: {}",
                targetContractAddress, walletAddress, partition);
        try {
            requireDeployedContract(targetContractAddress);

            byte[] partitionBytes = new byte[32];
            byte[] sourceBytes = partition.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(sourceBytes, 0, partitionBytes, 0, Math.min(sourceBytes.length, 32));

            Function function = new Function(
                    "balanceOfByPartition",
                    Arrays.asList(
                            new org.web3j.abi.datatypes.generated.Bytes32(partitionBytes),
                            new org.web3j.abi.datatypes.Address(walletAddress)
                    ),
                    Arrays.asList(new TypeReference<Uint256>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            targetContractAddress,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            if (response.hasError()) {
                throw new BlockchainException("balanceOfByPartition call failed: " + response.getError().getMessage());
            }
            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (results.isEmpty()) {
                // 컨트랙트가 없는 주소이거나 응답이 비어 있다. 잔고 0과 구분해야 한다.
                throw new BlockchainException("balanceOfByPartition returned no value from contract " + targetContractAddress);
            }
            BigInteger rawBalance = (BigInteger) results.get(0).getValue();
            // 18자리 온체인 단위를 4자리 소수점 단위로 나누어 리턴
            return new BigDecimal(rawBalance).divide(BigDecimal.valueOf(10).pow(18));
        } catch (BlockchainException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainException("Failed to check on-chain balance for address: " + walletAddress, e);
        }
    }

    /** 설정의 기본 컨트랙트 주소. 로컬 시드 종목이 실제로 배포된 컨트랙트를 가리키게 할 때 쓴다. */
    public String getDefaultContractAddress() {
        return contractAddress;
    }

    /**
     * 대상 주소에 컨트랙트 코드가 있는지 확인한다.
     *
     * <p>코드가 없는 주소로 보낸 트랜잭션은 아무 일도 하지 않고 성공으로 끝나, 이체가 됐다고 착각하게 된다.
     * 조회도 빈 응답이 와서 잔고 0과 구분되지 않는다. 그래서 이체와 조회 전에 먼저 막는다.
     */
    void requireDeployedContract(String address) {
        if (address == null || address.isBlank()) {
            throw new ContractNotDeployedException("Contract address is empty");
        }
        String key = address.toLowerCase();
        if (deployedContracts.contains(key)) {
            return;
        }

        EthGetCode response;
        try {
            response = web3j.ethGetCode(address, DefaultBlockParameterName.LATEST).send();
        } catch (Exception e) {
            throw new BlockchainException("Failed to read contract code at " + address, e);
        }
        if (response.hasError()) {
            throw new BlockchainException("eth_getCode failed for " + address + ": " + response.getError().getMessage());
        }
        String code = response.getCode();
        if (code == null || code.isBlank() || "0x".equals(code)) {
            throw new ContractNotDeployedException("No contract code at " + address);
        }
        deployedContracts.add(key);
    }

    /**
     * 공개 네트워크(Sepolia 등)의 변동성에 대비한 가스비 안전 버퍼 가산 기능 (15% 할증)
     */
    private BigInteger estimateGasPriceWithBuffer() {
        try {
            BigInteger rawPrice = web3j.ethGasPrice().send().getGasPrice();
            return rawPrice.multiply(BigInteger.valueOf(115)).divide(BigInteger.valueOf(100));
        } catch (Exception e) {
            log.warn("Failed to fetch dynamic gas price, falling back to 20 Gwei legacy fee", e);
            return BigInteger.valueOf(20_000_000_000L); 
        }
    }
}
