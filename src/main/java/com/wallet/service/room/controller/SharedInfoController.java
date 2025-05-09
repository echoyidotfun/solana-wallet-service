package com.wallet.service.room.controller;

import com.wallet.service.common.dto.ApiResponse;
import com.wallet.service.room.model.SharedInfo;
import com.wallet.service.room.service.SharedInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shared-info")
@RequiredArgsConstructor
public class SharedInfoController {
    
    private final SharedInfoService sharedInfoService;
    
    @PostMapping("/add")
    public ResponseEntity<?> addSharedInfo(
            @RequestParam String walletAddress,
            @RequestParam String roomId,
            @RequestParam String contentUrl) {
        
        try {
            SharedInfo sharedInfo = sharedInfoService.addSharedInfo(roomId, walletAddress, contentUrl);
            return ResponseEntity.ok(new ApiResponse<>(true, "添加共享信息成功", sharedInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "添加共享信息失败: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/list")
    public ResponseEntity<?> getSharedInfos(@RequestParam String roomId) {
        try {
            List<SharedInfo> sharedInfos = sharedInfoService.getRoomSharedInfos(roomId);
            return ResponseEntity.ok(new ApiResponse<>(true, "获取共享信息成功", sharedInfos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "获取共享信息失败: " + e.getMessage(), null));
        }
    }
}