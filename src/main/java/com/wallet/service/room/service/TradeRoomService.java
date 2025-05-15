package com.wallet.service.room.service;

import com.wallet.service.datapipe.repository.TokenRepository;
import com.wallet.service.datapipe.service.subscription.SubscriptionManagerService;
import com.wallet.service.room.dto.RoomStatusDTO;
import com.wallet.service.room.model.RoomMember;
import com.wallet.service.room.model.TradeRoom;
import com.wallet.service.room.repository.RoomMemberRepository;
import com.wallet.service.room.repository.TradeRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TradeRoomService {

    private final TokenRepository tokenRepository;
    
    private final TradeRoomRepository tradeRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SubscriptionManagerService subscriptionManagerService;
    
    @Autowired
    public TradeRoomService(TradeRoomRepository tradeRoomRepository, 
                            RoomMemberRepository roomMemberRepository, 
                            @Lazy SubscriptionManagerService subscriptionManagerService, TokenRepository tokenRepository) {
        this.tradeRoomRepository = tradeRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.subscriptionManagerService = subscriptionManagerService;
        this.tokenRepository = tokenRepository;
    }
    
    @Transactional
    public TradeRoom createRoom(String creatorWallet, String tokenAddress, Integer recycleHours, String password) {
        TradeRoom room = new TradeRoom();
        room.setRoomId(UUID.randomUUID().toString());
        room.setCreatorWallet(creatorWallet);
        room.setTokenAddress(tokenAddress);
        tokenRepository.findByMintAddress(tokenAddress)
            .ifPresent(token -> {
                room.setTokenName(token.getName());
                room.setTokenSymbol(token.getSymbol());
            });
        
        if (recycleHours != null) {
            room.setRecycleHours(recycleHours);
        }
        
        if (password != null && !password.trim().isEmpty()) {
            room.setPassword(password);
        }
        
        room.setStatus(TradeRoom.RoomStatus.OPEN);
        room.setLastActiveTime(LocalDateTime.now());
        
        // 创建者自动加入房间
        TradeRoom savedRoom = tradeRoomRepository.save(room);
        addMember(savedRoom, creatorWallet);
        // 处理用户钱包地址订阅
        subscriptionManagerService.handleUserJoinedRoom(creatorWallet, savedRoom.getRoomId());
        
        return savedRoom;
    }
    
    private void addMember(TradeRoom room, String walletAddress) {
        RoomMember member = new RoomMember();
        member.setRoom(room);
        member.setWalletAddress(walletAddress);
        member.setLastActiveTime(LocalDateTime.now());
        roomMemberRepository.save(member);
    }
    
    @Transactional
    public void joinRoom(String roomId, String walletAddress) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("Room is not existed: " + roomId));
                
        if (room.getStatus() != TradeRoom.RoomStatus.OPEN) {
            throw new IllegalStateException("Room is closed or being creating");
        }
        
        roomMemberRepository.findByRoomAndWalletAddress(room, walletAddress)
                .ifPresentOrElse(
                    member -> {
                        if (!member.isActive()) {
                            member.setActive(true);
                            member.setLastActiveTime(LocalDateTime.now());
                            roomMemberRepository.save(member);
                        } else {
                            throw new IllegalStateException("Account is already in the room");
                        }
                    },
                    () -> addMember(room, walletAddress)
                );
        
        // Notify SubscriptionManagerService
        subscriptionManagerService.handleUserJoinedRoom(walletAddress, roomId);

        // 更新房间活跃时间
        room.setLastActiveTime(LocalDateTime.now());
        tradeRoomRepository.save(room);
    }
    
    @Transactional
    public void leaveRoom(String roomId, String walletAddress) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("Room not found: " + roomId));
        
        roomMemberRepository.findByRoomAndWalletAddress(room, walletAddress)
                .ifPresent(member -> {
                    member.setActive(false);
                    member.setLeaveTime(LocalDateTime.now());
                    roomMemberRepository.save(member);
                    // Notify SubscriptionManagerService
                    subscriptionManagerService.handleUserLeftRoom(walletAddress, roomId);
                });
    }
    
    @Transactional
    public void closeRoom(String roomId, String creatorWallet) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("Room not found: " + roomId));
        
        if (!room.getCreatorWallet().equals(creatorWallet)) {
            throw new IllegalStateException("Only creator can close the room");
        }
        
        closeRoomInternal(room);
    }
    
    public RoomStatusDTO getRoomStatus(String roomId) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("Room not found: " + roomId));
        
        RoomStatusDTO statusDTO = new RoomStatusDTO();
        statusDTO.setRoomId(room.getRoomId());
        statusDTO.setStatus(room.getStatus().name());
        
        if (room.getStatus() == TradeRoom.RoomStatus.OPEN) {
            List<String> members = roomMemberRepository.findByRoomAndActiveTrue(room).stream()
                    .map(RoomMember::getWalletAddress)
                    .collect(Collectors.toList());
            
            statusDTO.setMembers(members);
            statusDTO.setTokenAddress(room.getTokenAddress());
            statusDTO.setTokenName(room.getTokenName());
            statusDTO.setTokenSymbol(room.getTokenSymbol());
        }
        
        return statusDTO;
    }

    // Added to allow SubscriptionManagerService to fetch full room details
    public Optional<TradeRoom> getTradeRoomByRoomId(String roomId) {
        return tradeRoomRepository.findByRoomId(roomId);
    }

    /**
     * Get all rooms associated with a wallet address, including both active and inactive rooms
     * 
     * @param walletAddress the wallet address to query
     * @return List of room IDs the wallet has joined
     */
    public List<String> getRoomsByWalletAddress(String walletAddress) {
        List<RoomMember> memberships = roomMemberRepository.findByWalletAddressAndActiveTrue(walletAddress);
        return memberships.stream()
                .map(member -> member.getRoom().getRoomId())
                .collect(Collectors.toList());
    }
    
    @Scheduled(fixedRate = 3600000) // Run every 1 hour (for testing)
    @Transactional
    public void cleanupInactiveRooms() {
        List<TradeRoom> inactiveRooms = tradeRoomRepository.findByStatusAndLastActiveTimeBefore(
                TradeRoom.RoomStatus.OPEN,
                LocalDateTime.now().minusHours(12) // 默认12小时
        );
        
        for (TradeRoom room : inactiveRooms) {
            if (LocalDateTime.now().isAfter(room.getLastActiveTime().plusHours(room.getRecycleHours()))) {
                closeRoomInternal(room);
            }
        }
    }

    private void closeRoomInternal(TradeRoom room) {
        room.setStatus(TradeRoom.RoomStatus.CLOSED);
        tradeRoomRepository.save(room);

        subscriptionManagerService.handleRoomClosed(room.getRoomId());

        roomMemberRepository.findByRoomAndActiveTrue(room)
            .forEach(member -> {
                member.setActive(false);
                member.setLeaveTime(LocalDateTime.now());
                roomMemberRepository.save(member);
            });
    }

}