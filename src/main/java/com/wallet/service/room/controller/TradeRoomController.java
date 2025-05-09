package com.wallet.service.room.controller;

import com.wallet.service.common.dto.ApiResponse;
import com.wallet.service.room.dto.RoomStatusDTO;
import com.wallet.service.room.model.TradeRoom;
import com.wallet.service.room.service.TradeRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class TradeRoomController {
    
    private final TradeRoomService tradeRoomService;
    
    @GetMapping("/create")
    public ResponseEntity<?> createRoom(
            @RequestParam String walletAddress,
            @RequestParam(required = false) String tokenAddress,
            @RequestParam(required = false) Integer recycleHours,
            @RequestParam(required = false) String password) {
        TradeRoom room = tradeRoomService.createRoom(walletAddress, tokenAddress, recycleHours, password);
        return ResponseEntity.ok(new ApiResponse<>(true, "Successfully created room", room.getRoomId()));
    }
    
    @GetMapping("/join")
    public ResponseEntity<?> joinRoom(
            @RequestParam String walletAddress,
            @RequestParam String roomId) {
        tradeRoomService.joinRoom(roomId, walletAddress);
        return ResponseEntity.ok(new ApiResponse<>(true, "Successfully joined room", null));
    }
    
    @GetMapping("/leave")
    public ResponseEntity<?> leaveRoom(
            @RequestParam String walletAddress,
            @RequestParam String roomId) {
        tradeRoomService.leaveRoom(roomId, walletAddress);
        return ResponseEntity.ok(new ApiResponse<>(true, "Successfully left room", null));
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getRoomStatus(@RequestParam String roomId) {
        RoomStatusDTO status = tradeRoomService.getRoomStatus(roomId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Success", status));
    }
    
    @GetMapping("/close")
    public ResponseEntity<?> closeRoom(
            @RequestParam String walletAddress,
            @RequestParam String roomId) {
        tradeRoomService.closeRoom(roomId, walletAddress);
        return ResponseEntity.ok(new ApiResponse<>(true, "Successfully closed room", null));    
    }
    
    @GetMapping("/list")
    public ResponseEntity<?> getRoomsByWalletAddress(@RequestParam String walletAddress) {
        List<String> roomIds = tradeRoomService.getRoomsByWalletAddress(walletAddress);
        return ResponseEntity.ok(new ApiResponse<>(true, "Rooms retrieved successfully", roomIds));
    }
}