package com.tokit.infra.blockchain;

/**
 * 온체인 호출 실패. 전송 오류와 컨트랙트 revert를 모두 포함합니다.
 * 오프체인 거래는 이 예외로 중단되지 않아야 하며, 호출부가 재시도 또는 경보로 처리합니다.
 */
public class BlockchainException extends RuntimeException {

    public BlockchainException(String message) {
        super(message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}
