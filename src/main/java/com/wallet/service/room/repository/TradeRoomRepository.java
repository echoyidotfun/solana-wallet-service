package com.wallet.service.room.repository;

import com.wallet.service.room.model.TradeRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRoomRepository extends JpaRepository<TradeRoom, Long> {
    
    Optional<TradeRoom> findByRoomId(String roomId);
    
    List<TradeRoom> findByCreatorWallet(String creatorWallet);
    
    List<TradeRoom> findByStatusAndLastActiveTimeBefore(TradeRoom.RoomStatus status, LocalDateTime time);
    
    @Query("SELECT r FROM TradeRoom r JOIN r.members m WHERE m.walletAddress = :walletAddress AND m.active = true")
    List<TradeRoom> findRoomsByMemberWallet(String walletAddress);
} 
