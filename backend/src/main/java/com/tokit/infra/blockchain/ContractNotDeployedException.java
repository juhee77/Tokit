package com.tokit.infra.blockchain;

/**
 * 대상 주소에 컨트랙트 코드가 없다.
 *
 * <p>코드가 없는 주소로 보낸 트랜잭션은 아무 일도 하지 않고 성공으로 끝나고, 조회는 빈 응답을 돌려준다.
 * 설정이나 데이터가 잘못된 것이므로 다시 시도해도 결과가 같다. 호출부는 재시도하지 않는다.
 */
public class ContractNotDeployedException extends BlockchainException {

    public ContractNotDeployedException(String message) {
        super(message);
    }
}
