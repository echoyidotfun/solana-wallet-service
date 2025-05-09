package com.wallet.service.room.repository;

import com.wallet.service.room.model.SharedInfo;
import com.wallet.service.room.model.TradeRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedInfoRepository extends JpaRepository<SharedInfo, Long> {
    
    List<SharedInfo> findByRoomOrderByShareTimeDesc(TradeRoom room);
}
