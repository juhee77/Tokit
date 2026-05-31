import { ethers } from "hardhat";

async function main() {
  const [deployer] = await ethers.getSigners();

  console.log("Deploying contract with the account:", deployer.address);

  // 1,000,000 토큰 공급량 (18 Decimals)
  const initialSupply = ethers.parseEther("1000000"); 

  const AssetToken = await ethers.getContractFactory("AssetToken");
  
  // 생성자 인자: Name, Symbol, InitialSupply, InitialOwner
  const token = await AssetToken.deploy(
    "TOKIT Apple STO",
    "APPL-STO",
    initialSupply,
    deployer.address
  );

  await token.waitForDeployment();

  console.log("AssetToken deployed to:", await token.getAddress());
  console.log("Token Name:", await token.name());
  console.log("Token Symbol:", await token.symbol());
  console.log("Total Supply:", (await token.totalSupply()).toString());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
