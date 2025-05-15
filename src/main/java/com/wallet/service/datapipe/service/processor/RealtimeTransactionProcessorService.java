package com.wallet.service.datapipe.service.processor;

import com.wallet.service.datapipe.dto.quicknode.wss.LogsNotificationDto;
import java.util.List;
// import java.util.Set; // No longer needed here

public interface RealtimeTransactionProcessorService {

    void processLogNotification(
        LogsNotificationDto.ValueDto logValue,
        String walletAddress,
        List<RoomTarget> relevantRoomTargets
    );

    // Inner class/record to pass room-specific target information
    class RoomTarget {
        private final String roomId;
        private final String targetTokenAddress;
        // private final Set<String> targetProgramIds; // Removed

        public RoomTarget(String roomId, String targetTokenAddress /*, Set<String> targetProgramIds Removed */) {
            this.roomId = roomId;
            this.targetTokenAddress = targetTokenAddress;
            // this.targetProgramIds = targetProgramIds; // Removed
        }

        public String getRoomId() {
            return roomId;
        }

        public String getTargetTokenAddress() {
            return targetTokenAddress;
        }

        // public Set<String> getTargetProgramIds() { // Removed
        //     return targetProgramIds;
        // }
        
        // Consider adding equals, hashCode, and toString if needed elsewhere
    }
} 