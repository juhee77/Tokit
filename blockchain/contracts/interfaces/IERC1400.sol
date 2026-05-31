// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/**
 * @title IERC1400 Security Token Standard
 * @dev ERC-1400 규격을 간소화한 핵심 인터페이스 정의
 */
interface IERC1400 {
    // --- Document Management ---
    function getDocument(bytes32 name) external view returns (string memory, bytes32);
    function setDocument(bytes32 name, string calldata uri, bytes32 documentHash) external;
    event DocumentUpdated(bytes32 indexed name, string uri, bytes32 documentHash);

    // --- Partition Token Balances ---
    function balanceOfByPartition(bytes32 partition, address tokenHolder) external view returns (uint256);
    function partitionsOf(address tokenHolder) external view returns (bytes32[] memory);

    // --- Partition Token Transfers ---
    function transferByPartition(
        bytes32 partition,
        address to,
        uint256 value,
        bytes calldata data
    ) external returns (bytes32);

    event TransferByPartition(
        bytes32 indexed fromPartition,
        address operator,
        address indexed from,
        address indexed to,
        uint256 value,
        bytes data,
        bytes operatorData
    );

    // --- Whitelist Management ---
    function isWhitelisted(address investor) external view returns (bool);
    function addToWhitelist(address investor) external;
    function removeFromWhitelist(address investor) external;
    event WhitelistUpdated(address indexed investor, bool status);
}
