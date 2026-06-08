package com.tokit.infra.blockchain;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * 블록체인 네트워크 상에서 STO 토큰 전송(Partition-based transfer) 이벤트를 처리
     * 여기서는 Admin(Owner) 권한으로 forceTransferByPartition을 직접 날려서 온체인 잔고 동기화를 수행
     */
    public void handleTransferByPartition(String symbol, String partition, String from, String to, BigDecimal amount) {
        log.info("Executing Admin Force Partition Transfer. Symbol: {}, Partition: {}, From: {}, To: {}, Amount: {}",
                symbol, partition, from, to, amount);
        
        try {
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
            
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(3_000_000L); // 넉넉히 가스 배정

            EthSendTransaction response = txManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (response.hasError()) {
                log.error("Blockchain force transfer TX failed: {}", response.getError().getMessage());
                throw new RuntimeException("Blockchain TX Error: " + response.getError().getMessage());
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
                throw new RuntimeException("Blockchain TX reverted");
            }

        } catch (Exception e) {
            log.error("Failed to execute forceTransferByPartition on blockchain", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 특정 주주(지갑)의 온체인 파티션 토큰 잔고 조회
     */
    public BigDecimal balanceOfByPartition(String symbol, String partition, String walletAddress) {
        log.info("Checking on-chain balance for address: {}, partition: {}", walletAddress, partition);
        try {
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
                            contractAddress,
                            encodedFunction
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (results.isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigInteger rawBalance = (BigInteger) results.get(0).getValue();
            // 18자리 온체인 단위를 4자리 소수점 단위로 나누어 리턴
            return new BigDecimal(rawBalance).divide(BigDecimal.valueOf(10).pow(18));
        } catch (Exception e) {
            log.error("Failed to check on-chain balance on blockchain for address: " + walletAddress, e);
            return BigDecimal.ZERO;
        }
    }
}
