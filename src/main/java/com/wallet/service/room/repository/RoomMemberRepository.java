package com.wallet.service.room.repository;

import com.wallet.service.room.model.RoomMember;
import com.wallet.service.room.model.TradeRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    
    List<RoomMember> findByRoomAndActiveTrue(TradeRoom room);
    
    Optional<RoomMember> findByRoomAndWalletAddress(TradeRoom room, String walletAddress);
    
    List<RoomMember> findByWalletAddressAndActiveTrue(String walletAddress);
}

