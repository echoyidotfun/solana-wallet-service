package com.wallet.service.datapipe.repository;

import com.wallet.service.datapipe.model.Trader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraderRepository extends JpaRepository<Trader, String> {
    
    /**
     * 检查指定钱包地址是否存在
     * 
     * @param walletAddress 钱包地址
     * @return 是否存在该钱包地址
     */
    boolean existsByWalletAddress(String walletAddress);

    /**
     * 根据钱包地址列表查询Trader信息
     * 
     * @param walletAddresses 钱包地址列表
     * @return 对应的Trader列表
     */
    List<Trader> findAllByWalletAddressIn(List<String> walletAddresses);
}
