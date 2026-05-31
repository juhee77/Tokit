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

        // 파티션 목록 관리 (수신자 목록에 파티션이 없으면 추가)
        bool hasPartition = false;
        bytes32[] memory toPartitions = _holderPartitions[to];
        for (uint256 i = 0; i < toPartitions.length; i++) {
            if (toPartitions[i] == partition) {
                hasPartition = true;
                break;
            }
        }
        if (!hasPartition) {
            _holderPartitions[to].push(partition);
        }

        // 표준 ERC20 전송을 병행하여 지갑 및 일반 탐색기 호환성 보장
        _transfer(msg.sender, to, value);

        emit TransferByPartition(partition, msg.sender, msg.sender, to, value, data, "");
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
        emit TransferByPartition("", msg.sender, address(0), tokenHolder, value, data, "");
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
