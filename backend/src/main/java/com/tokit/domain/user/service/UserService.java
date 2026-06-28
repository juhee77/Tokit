package com.tokit.domain.user.service;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ContractService contractService;

    @Transactional
    public User signUp(String email, String name, String walletAddress) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(name)
                        .walletAddress(walletAddress)
                        .build()));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public User updateKycStatus(Long id, boolean kycStatus) {
        User user = getUserById(id);
        user.updateKycStatus(kycStatus);
        
        // 온체인 화이트리스트 동기화
        if (kycStatus) {
            contractService.addToWhitelist(user.getWalletAddress());
        } else {
            contractService.removeFromWhitelist(user.getWalletAddress());
        }
        
        return user;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
