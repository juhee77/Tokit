import { expect } from "chai";
import { ethers } from "hardhat";
import { AssetToken } from "../typechain-types";
import { HardhatEthersSigner } from "@nomicfoundation/hardhat-ethers/signers";

/**
 * ERC-1400 컴플라이언스 검증.
 *
 * 이 프로젝트의 핵심 주장은 "KYC 인증을 마친 지갑만 토큰을 전송할 수 있다"는 것이다.
 * 그 주장이 실제로 성립하는지, 그리고 온체인 파티션 원장이 백엔드가 신뢰할 만한
 * 상태로 유지되는지를 검증한다.
 */
describe("AssetToken (ERC-1400 컴플라이언스)", () => {
  const DEFAULT_PARTITION = ethers.encodeBytes32String("DEFAULT");
  const SUPPLY = ethers.parseEther("1000");

  let token: AssetToken;
  let owner: HardhatEthersSigner;      // 발행사 (화이트리스트 등록됨)
  let investor: HardhatEthersSigner;   // KYC 통과 투자자
  let outsider: HardhatEthersSigner;   // KYC 미통과 (화이트리스트 밖)

  beforeEach(async () => {
    [owner, investor, outsider] = await ethers.getSigners();
    const Factory = await ethers.getContractFactory("AssetToken");
    token = await Factory.deploy("Tokit Test Asset", "TTA", SUPPLY, owner.address);
    await token.waitForDeployment();

    await token.addToWhitelist(investor.address);
  });

  describe("화이트리스트 통제", () => {
    it("화이트리스트에 등록된 지갑끼리는 파티션 전송이 성공한다", async () => {
      const amount = ethers.parseEther("100");
      await token.transferByPartition(DEFAULT_PARTITION, investor.address, amount, "0x");

      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address)).to.equal(amount);
    });

    it("화이트리스트에 없는 지갑으로는 파티션 전송이 거부된다", async () => {
      await expect(
        token.transferByPartition(DEFAULT_PARTITION, outsider.address, ethers.parseEther("1"), "0x")
      ).to.be.revertedWith("Investor not in whitelist (Compliance Check Failed)");
    });

    it("화이트리스트에서 제거되면 이후 전송이 거부된다", async () => {
      await token.removeFromWhitelist(investor.address);

      await expect(
        token.transferByPartition(DEFAULT_PARTITION, investor.address, ethers.parseEther("1"), "0x")
      ).to.be.revertedWith("Investor not in whitelist (Compliance Check Failed)");
    });
  });

  describe("컴플라이언스 우회 차단", () => {
    it("표준 ERC20 transfer로도 화이트리스트 밖으로 토큰을 보낼 수 없어야 한다", async () => {
      // transferByPartition은 막지만 상속받은 ERC20.transfer가 열려 있으면
      // 규제 통제 전체가 무의미해진다.
      await expect(
        token.transfer(outsider.address, ethers.parseEther("10"))
      ).to.be.reverted;
    });

    it("ERC20 전송 후에도 파티션 잔고와 ERC20 잔고가 일치해야 한다", async () => {
      // 백엔드 대사(Reconciliation) 배치는 balanceOfByPartition을 신뢰한다.
      // 두 원장이 어긋나면 대사 결과 자체가 무의미해진다.
      const amount = ethers.parseEther("10");
      await token.transfer(investor.address, amount).catch(() => { /* 차단되면 통과 */ });

      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address))
        .to.equal(await token.balanceOf(investor.address));
    });
  });

  describe("파티션 잔고 이중 반영 방지", () => {
    it("DEFAULT 파티션 전송 후 송신자 잔고가 정확히 전송액만큼만 줄어든다", async () => {
      // transferByPartition은 파티션 잔고를 직접 옮긴 뒤 _transfer를 호출한다.
      // _update가 같은 이전을 또 반영하면 두 배로 차감된다.
      const amount = ethers.parseEther("100");
      const before = await token.balanceOfByPartition(DEFAULT_PARTITION, owner.address);

      await token.transferByPartition(DEFAULT_PARTITION, investor.address, amount, "0x");

      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, owner.address))
        .to.equal(before - amount);
      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address))
        .to.equal(amount);
    });

    it("파티션 전송 후에도 파티션 잔고와 ERC20 잔고가 일치한다", async () => {
      const amount = ethers.parseEther("100");
      await token.transferByPartition(DEFAULT_PARTITION, investor.address, amount, "0x");

      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, owner.address))
        .to.equal(await token.balanceOf(owner.address));
      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address))
        .to.equal(await token.balanceOf(investor.address));
    });

    it("연속 전송에도 총량이 보존된다", async () => {
      const total = await token.totalSupply();
      await token.transferByPartition(DEFAULT_PARTITION, investor.address, ethers.parseEther("100"), "0x");
      await token.transfer(investor.address, ethers.parseEther("50"));
      await token.connect(investor).transfer(owner.address, ethers.parseEther("30"));

      const sum = (await token.balanceOfByPartition(DEFAULT_PARTITION, owner.address))
                + (await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address));
      expect(sum).to.equal(total);
    });
  });

  describe("발행(issue) 후 상태 정합성", () => {
    it("issue로 토큰을 받은 주주는 partitionsOf에 DEFAULT 파티션이 나타나야 한다", async () => {
      await token.issue(investor.address, ethers.parseEther("50"), "0x");

      expect(await token.partitionsOf(investor.address)).to.include(DEFAULT_PARTITION);
    });

    it("issue된 수량만큼 파티션 잔고와 ERC20 총발행량이 함께 증가한다", async () => {
      const amount = ethers.parseEther("50");
      const beforeSupply = await token.totalSupply();

      await token.issue(investor.address, amount, "0x");

      expect(await token.balanceOfByPartition(DEFAULT_PARTITION, investor.address)).to.equal(amount);
      expect(await token.totalSupply()).to.equal(beforeSupply + amount);
    });
  });
});
