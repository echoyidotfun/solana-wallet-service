package com.wallet.service.room.service;

import com.wallet.service.room.model.SharedInfo;
import com.wallet.service.room.model.TradeRoom;
import com.wallet.service.room.repository.SharedInfoRepository;
import com.wallet.service.room.repository.TradeRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SharedInfoService {
    
    private final SharedInfoRepository sharedInfoRepository;
    private final TradeRoomRepository tradeRoomRepository;
    
    @Transactional
    public SharedInfo addSharedInfo(String roomId, String walletAddress, String contentUrl) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("房间不存在: " + roomId));
        
        SharedInfo sharedInfo = new SharedInfo();
        sharedInfo.setRoom(room);
        sharedInfo.setSharerWallet(walletAddress);
        sharedInfo.setContentUrl(contentUrl);
        
        // 简单URL分析
        if (contentUrl.contains("twitter.com") || contentUrl.contains("x.com")) {
            sharedInfo.setInfoType(SharedInfo.InfoType.TWITTER);
        } else if (contentUrl.contains("t.me")) {
            sharedInfo.setInfoType(SharedInfo.InfoType.TELEGRAM);
        } else {
            sharedInfo.setInfoType(SharedInfo.InfoType.OTHER);
        }
        
        // TODO: 解析URL内容，获取元数据
        // sharedInfo.setContentMetadata(...);
        
        return sharedInfoRepository.save(sharedInfo);
    }
    
    public List<SharedInfo> getRoomSharedInfos(String roomId) {
        TradeRoom room = tradeRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalStateException("房间不存在: " + roomId));
        
        return sharedInfoRepository.findByRoomOrderByShareTimeDesc(room);
    }
}