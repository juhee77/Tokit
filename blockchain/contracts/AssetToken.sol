// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "./interfaces/IERC1400.sol";

/**
 * @title AssetToken - ERC-1400 STO Security Token
 * @dev ERC-1400 규격을 따르는 토큰증권 기본 계약
 */
contract AssetToken is ERC20, IERC1400 {
    
    // --- State Variables ---
    address public owner;
    
    // 문서 관리 매핑: document name hash -> (uri, hash)
    struct Document {
        string docUri;
        bytes32 docHash;
    }
    mapping(bytes32 => Document) private _documents;

    // 파티션별 잔고 매핑: partition -> holder -> balance
    mapping(bytes32 => mapping(address => uint256)) private _partitionBalances;
    // 주주별 보유 파티션 목록
    mapping(address => bytes32[]) private _holderPartitions;
    
    // 화이트리스트 (투자자 자격 검증) 매핑
    mapping(address => bool) private _whitelist;

    // 기본 파티션 명칭 정의 (예: 일반주식)
    bytes32 public constant DEFAULT_PARTITION = "DEFAULT";

    // 파티션 경로(transferByPartition 등)가 이미 파티션 잔고를 옮겼는지 표시한다.
    // _update가 같은 이전을 한 번 더 반영해 이중 차감하는 것을 막는다.
    bool private _partitionTransferInProgress;

    // --- Modifiers ---
    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner allowed");
        _;
    }

    modifier onlyWhitelisted(address investor) {
        require(_whitelist[investor], "Investor not in whitelist (Compliance Check Failed)");
        _;
    }

    constructor(
        string memory name,
        string memory symbol,
        uint256 initialSupply,
        address initialOwner
    ) ERC20(name, symbol) {
        owner = initialOwner;
        _whitelist[initialOwner] = true;
        
        // 초기 발행 물량을 DEFAULT 파티션 및 ERC20 전체 잔고에 민팅
        _mint(initialOwner, initialSupply);
        _partitionBalances[DEFAULT_PARTITION][initialOwner] = initialSupply;
        _holderPartitions[initialOwner].push(DEFAULT_PARTITION);
    }


    // --- Internal Helpers ---

    /// @dev 보유 파티션 목록에 없으면 추가한다. (중복 등록 방지)
    function _registerPartition(address holder, bytes32 partition) private {
        bytes32[] storage held = _holderPartitions[holder];
        for (uint256 i = 0; i < held.length; i++) {
            if (held[i] == partition) {
                return;
            }
        }
        held.push(partition);
    }

    /**
     * @dev 모든 토큰 이동이 통과하는 단일 지점.
     *
     * transferByPartition만 막고 상속받은 ERC20.transfer를 열어두면 컴플라이언스 통제가
     * 통째로 무력화되므로, 화이트리스트 검사를 여기서 강제한다. 또한 표준 ERC20 경로로
     * 토큰이 움직여도 파티션 원장이 함께 갱신되게 한다. 백엔드 대사 배치는
     * balanceOfByPartition을 신뢰하므로, 두 원장이 어긋나면 대사 자체가 무의미해진다.
     *
     * 발행(from=0)과 상환(to=0)은 owner 권한으로 이미 통제되므로 검사 대상에서 제외한다.
     */
    function _update(address from, address to, uint256 value) internal virtual override {
        if (from != address(0) && to != address(0)) {
            require(_whitelist[from], "Investor not in whitelist (Compliance Check Failed)");
            require(_whitelist[to], "Investor not in whitelist (Compliance Check Failed)");

            if (!_partitionTransferInProgress) {
                require(
                    _partitionBalances[DEFAULT_PARTITION][from] >= value,
                    "Insufficient partition balance"
                );
                _partitionBalances[DEFAULT_PARTITION][from] -= value;
                _partitionBalances[DEFAULT_PARTITION][to] += value;
                _registerPartition(to, DEFAULT_PARTITION);
            }
        }
        super._update(from, to, value);
    }

    // --- Whitelist Management ---
    function isWhitelisted(address investor) public view override returns (bool) {
        return _whitelist[investor];
    }

    function addToWhitelist(address investor) public override onlyOwner {
        _whitelist[investor] = true;
        emit WhitelistUpdated(investor, true);
    }

    function removeFromWhitelist(address investor) public override onlyOwner {
        _whitelist[investor] = false;
        emit WhitelistUpdated(investor, false);
    }

    // --- Document Management ---
    function getDocument(bytes32 name) external view override returns (string memory, bytes32) {
        Document memory doc = _documents[name];
        return (doc.docUri, doc.docHash);
    }

    function setDocument(bytes32 name, string calldata uri, bytes32 documentHash) external override onlyOwner {
        _documents[name] = Document(uri, documentHash);
        emit DocumentUpdated(name, uri, documentHash);
    }

    // --- Partition Token Balances ---
    function balanceOfByPartition(bytes32 partition, address tokenHolder) external view override returns (uint256) {
        return _partitionBalances[partition][tokenHolder];
    }

    function partitionsOf(address tokenHolder) external view override returns (bytes32[] memory) {
        return _holderPartitions[tokenHolder];
    }

    // --- Partition Token Transfers (Compliance Whitelist Checked) ---
    function transferByPartition(
        bytes32 partition,
        address to,
        uint256 value,
        bytes calldata data
    ) external override onlyWhitelisted(msg.sender) onlyWhitelisted(to) returns (bytes32) {
        require(_partitionBalances[partition][msg.sender] >= value, "Insufficient partition balance");

        // 잔고 차감 및 가산
        _partitionBalances[partition][msg.sender] -= value;
        _partitionBalances[partition][to] += value;

        _registerPartition(to, partition);

        // 표준 ERC20 전송을 병행하여 지갑 및 일반 탐색기 호환성 보장.
        // 파티션 잔고는 위에서 이미 옮겼으므로 _update가 중복 반영하지 않도록 표시한다.
        _partitionTransferInProgress = true;
        _transfer(msg.sender, to, value);
        _partitionTransferInProgress = false;

        emit TransferByPartition(partition, msg.sender, msg.sender, to, value, data, "");
        return partition;
    }

    // --- Admin Force Partition Transfer for Off-Chain Settlement ---
    function forceTransferByPartition(
        bytes32 partition,
        address from,
        address to,
        uint256 value,
        bytes calldata data
    ) external onlyOwner onlyWhitelisted(from) onlyWhitelisted(to) returns (bytes32) {
        require(_partitionBalances[partition][from] >= value, "Insufficient partition balance for force transfer");

        // 잔고 차감 및 가산
        _partitionBalances[partition][from] -= value;
        _partitionBalances[partition][to] += value;

        _registerPartition(to, partition);

        // 표준 ERC20 전송 병행 (파티션 잔고 중복 반영 방지)
        _partitionTransferInProgress = true;
        _transfer(from, to, value);
        _partitionTransferInProgress = false;

        emit TransferByPartition(partition, msg.sender, from, to, value, data, "");
        return partition;
    }

    // --- Issuance & Redemption (발행 및 상환) ---
    function issue(
        address tokenHolder,
        uint256 value,
        bytes calldata data
    ) external onlyOwner onlyWhitelisted(tokenHolder) {
        _mint(tokenHolder, value);
        _partitionBalances[DEFAULT_PARTITION][tokenHolder] += value;
        // 파티션 목록에 등록하지 않으면 partitionsOf가 빈 배열을 반환해,
        // 보유 파티션을 순회하는 외부 조회가 이 주주를 통째로 놓친다.
        _registerPartition(tokenHolder, DEFAULT_PARTITION);
        emit TransferByPartition(DEFAULT_PARTITION, msg.sender, address(0), tokenHolder, value, data, "");
    }

    function redeem(
        address tokenHolder,
        uint256 value,
        bytes calldata data
    ) external onlyOwner {
        require(_partitionBalances[DEFAULT_PARTITION][tokenHolder] >= value, "Insufficient partition balance for redemption");
        _burn(tokenHolder, value);
        _partitionBalances[DEFAULT_PARTITION][tokenHolder] -= value;
        emit TransferByPartition(DEFAULT_PARTITION, msg.sender, tokenHolder, address(0), value, data, "");
    }
}
