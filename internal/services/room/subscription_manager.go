package room

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
	"github.com/emiyaio/solana-wallet-service/internal/services/blockchain"
)

// SubscriptionManager manages wallet subscriptions for room members
type SubscriptionManager interface {
	HandleUserJoinedRoom(walletAddress, roomID string, targetTokenAddress *string) error
	HandleUserLeftRoom(walletAddress, roomID string) error
	HandleRoomClosed(roomID string) error
	OnWebSocketReconnected() error
	GetActiveSubscriptions() map[string][]string // wallet -> roomIDs
}

type subscriptionManager struct {
	quickNodeService        blockchain.QuickNodeService
	transactionProcessor    blockchain.TransactionProcessor
	roomRepo                repositories.RoomRepository
	wsService               WebSocketService
	logger                  *logrus.Logger
	
	// Subscription state management
	walletRoomSubscriptions map[string]map[string]*RoomSubscriptionContext // wallet -> roomID -> context
	walletNotificationConsumers map[string]blockchain.LogConsumer          // wallet -> consumer
	mu                      sync.RWMutex
}

// RoomSubscriptionContext holds context for room-specific subscriptions
type RoomSubscriptionContext struct {
	RoomID             string
	TargetTokenAddress *string
	JoinedAt           string
}

// NewSubscriptionManager creates a new subscription manager
func NewSubscriptionManager(
	quickNodeService blockchain.QuickNodeService,
	transactionProcessor blockchain.TransactionProcessor,
	roomRepo repositories.RoomRepository,
	wsService WebSocketService,
	logger *logrus.Logger,
) SubscriptionManager {
	return &subscriptionManager{
		quickNodeService:            quickNodeService,
		transactionProcessor:        transactionProcessor,
		roomRepo:                    roomRepo,
		wsService:                   wsService,
		logger:                      logger,
		walletRoomSubscriptions:     make(map[string]map[string]*RoomSubscriptionContext),
		walletNotificationConsumers: make(map[string]blockchain.LogConsumer),
	}
}

// HandleUserJoinedRoom handles user joining a room
func (sm *subscriptionManager) HandleUserJoinedRoom(walletAddress, roomID string, targetTokenAddress *string) error {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	
	// Initialize wallet subscriptions map if not exists
	if _, exists := sm.walletRoomSubscriptions[walletAddress]; !exists {
		sm.walletRoomSubscriptions[walletAddress] = make(map[string]*RoomSubscriptionContext)
	}
	
	// Add room context
	context := &RoomSubscriptionContext{
		RoomID:             roomID,
		TargetTokenAddress: targetTokenAddress,
		JoinedAt:           fmt.Sprintf("%d", getCurrentTimestamp()),
	}
	
	sm.walletRoomSubscriptions[walletAddress][roomID] = context
	
	// Create or update consumer for this wallet
	consumer := sm.createConsumerForWallet(walletAddress)
	sm.walletNotificationConsumers[walletAddress] = consumer
	
	// Subscribe to wallet logs if not already subscribed
	if err := sm.quickNodeService.SubscribeWalletLogs(walletAddress, consumer); err != nil {
		// Clean up on failure
		delete(sm.walletRoomSubscriptions[walletAddress], roomID)
		if len(sm.walletRoomSubscriptions[walletAddress]) == 0 {
			delete(sm.walletRoomSubscriptions, walletAddress)
			delete(sm.walletNotificationConsumers, walletAddress)
		}
		return fmt.Errorf("failed to subscribe to wallet logs: %w", err)
	}
	
	sm.logger.WithFields(logrus.Fields{
		"wallet":              walletAddress,
		"room_id":             roomID,
		"target_token":        targetTokenAddress,
		"total_rooms":         len(sm.walletRoomSubscriptions[walletAddress]),
	}).Info("User joined room, subscription updated")
	
	return nil
}

// HandleUserLeftRoom handles user leaving a room
func (sm *subscriptionManager) HandleUserLeftRoom(walletAddress, roomID string) error {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	
	// Remove room context
	if roomContexts, exists := sm.walletRoomSubscriptions[walletAddress]; exists {
		delete(roomContexts, roomID)
		
		// If no more rooms for this wallet, unsubscribe completely
		if len(roomContexts) == 0 {
			delete(sm.walletRoomSubscriptions, walletAddress)
			delete(sm.walletNotificationConsumers, walletAddress)
			
			if err := sm.quickNodeService.UnsubscribeWalletLogs(walletAddress); err != nil {
				sm.logger.WithFields(logrus.Fields{
					"wallet": walletAddress,
					"error":  err,
				}).Error("Failed to unsubscribe wallet logs")
				return fmt.Errorf("failed to unsubscribe wallet logs: %w", err)
			}
			
			sm.logger.WithField("wallet", walletAddress).Info("User left all rooms, unsubscribed from wallet logs")
		} else {
			sm.logger.WithFields(logrus.Fields{
				"wallet":        walletAddress,
				"room_id":       roomID,
				"remaining_rooms": len(roomContexts),
			}).Info("User left room, subscription maintained for other rooms")
		}
	}
	
	return nil
}

// HandleRoomClosed handles room closure
func (sm *subscriptionManager) HandleRoomClosed(roomID string) error {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	
	var walletsToUpdate []string
	
	// Find all wallets subscribed to this room
	for walletAddress, roomContexts := range sm.walletRoomSubscriptions {
		if _, exists := roomContexts[roomID]; exists {
			delete(roomContexts, roomID)
			walletsToUpdate = append(walletsToUpdate, walletAddress)
			
			// If no more rooms for this wallet, clean up
			if len(roomContexts) == 0 {
				delete(sm.walletRoomSubscriptions, walletAddress)
				delete(sm.walletNotificationConsumers, walletAddress)
			}
		}
	}
	
	// Unsubscribe wallets that no longer have any rooms
	for _, walletAddress := range walletsToUpdate {
		if _, stillHasRooms := sm.walletRoomSubscriptions[walletAddress]; !stillHasRooms {
			if err := sm.quickNodeService.UnsubscribeWalletLogs(walletAddress); err != nil {
				sm.logger.WithFields(logrus.Fields{
					"wallet": walletAddress,
					"error":  err,
				}).Error("Failed to unsubscribe wallet after room closure")
			}
		}
	}
	
	sm.logger.WithFields(logrus.Fields{
		"room_id":         roomID,
		"affected_wallets": len(walletsToUpdate),
	}).Info("Room closed, updated subscriptions")
	
	return nil
}

// OnWebSocketReconnected handles WebSocket reconnection
func (sm *subscriptionManager) OnWebSocketReconnected() error {
	sm.mu.RLock()
	consumersToRestore := make(map[string]blockchain.LogConsumer)
	for wallet, consumer := range sm.walletNotificationConsumers {
		consumersToRestore[wallet] = consumer
	}
	sm.mu.RUnlock()
	
	// Restore all subscriptions
	for walletAddress, consumer := range consumersToRestore {
		if err := sm.quickNodeService.SubscribeWalletLogs(walletAddress, consumer); err != nil {
			sm.logger.WithFields(logrus.Fields{
				"wallet": walletAddress,
				"error":  err,
			}).Error("Failed to restore wallet subscription after reconnection")
		}
	}
	
	sm.logger.WithField("restored_subscriptions", len(consumersToRestore)).Info("Restored wallet subscriptions after WebSocket reconnection")
	return nil
}

// GetActiveSubscriptions returns active subscriptions
func (sm *subscriptionManager) GetActiveSubscriptions() map[string][]string {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	
	result := make(map[string][]string)
	for walletAddress, roomContexts := range sm.walletRoomSubscriptions {
		var roomIDs []string
		for roomID := range roomContexts {
			roomIDs = append(roomIDs, roomID)
		}
		result[walletAddress] = roomIDs
	}
	
	return result
}

// createConsumerForWallet creates a log consumer for a specific wallet
func (sm *subscriptionManager) createConsumerForWallet(walletAddress string) blockchain.LogConsumer {
	return func(notification *blockchain.LogsNotification) error {
		// Process the log notification
		action, err := sm.transactionProcessor.ProcessLogNotification(notification)
		if err != nil {
			sm.logger.WithFields(logrus.Fields{
				"wallet": walletAddress,
				"error":  err,
			}).Error("Failed to process log notification")
			return err
		}
		
		// If no relevant action was found, skip
		if action == nil {
			return nil
		}
		
		// Get current room contexts for this wallet
		sm.mu.RLock()
		roomContexts, exists := sm.walletRoomSubscriptions[walletAddress]
		if !exists {
			sm.mu.RUnlock()
			return nil
		}
		
		// Create a copy to avoid holding the lock too long
		roomIDsToNotify := make([]string, 0, len(roomContexts))
		for roomID := range roomContexts {
			roomIDsToNotify = append(roomIDsToNotify, roomID)
		}
		sm.mu.RUnlock()
		
		// Notify all rooms where this wallet is a member
		for _, roomID := range roomIDsToNotify {
			// Check if the room still exists and wallet is still a member
			if err := sm.validateRoomMembership(walletAddress, roomID); err != nil {
				sm.logger.WithFields(logrus.Fields{
					"wallet":  walletAddress,
					"room_id": roomID,
					"error":   err,
				}).Warn("Wallet no longer member of room, skipping notification")
				continue
			}
			
			// Create trade event message for WebSocket
			tradeEventMessage := &Message{
				Type: MessageTypeTradeEvent,
				Data: map[string]interface{}{
					"wallet_address":    action.WalletAddress,
					"platform":          action.Platform,
					"transaction_type":  action.TransactionType,
					"input_token":       action.InputToken,
					"output_token":      action.OutputToken,
					"signature":         action.Signature,
					"block_time":        action.BlockTime,
					"success":           action.Success,
					"fee":               action.Fee,
				},
				From: action.WalletAddress,
			}
			
			// Broadcast to room via WebSocket
			if err := sm.wsService.BroadcastToRoom(roomID, tradeEventMessage); err != nil {
				sm.logger.WithFields(logrus.Fields{
					"room_id": roomID,
					"wallet":  walletAddress,
					"error":   err,
				}).Error("Failed to broadcast trade event to room")
			} else {
				sm.logger.WithFields(logrus.Fields{
					"room_id":          roomID,
					"wallet":           walletAddress,
					"transaction_type": action.TransactionType,
					"platform":         action.Platform,
				}).Info("Broadcasted trade event to room")
			}
		}
		
		return nil
	}
}

// validateRoomMembership validates that a wallet is still a member of a room
func (sm *subscriptionManager) validateRoomMembership(walletAddress, roomID string) error {
	// Parse room ID to UUID
	roomUUID, err := uuid.Parse(roomID)
	if err != nil {
		// Try to get room by room_id string field
		room, err := sm.roomRepo.GetByRoomID(context.Background(), roomID)
		if err != nil {
			return fmt.Errorf("failed to get room: %w", err)
		}
		if room == nil {
			return fmt.Errorf("room not found")
		}
		roomUUID = room.ID
	}
	
	// Check if member exists
	member, err := sm.roomRepo.GetMemberByAddress(context.Background(), roomUUID, walletAddress)
	if err != nil {
		return fmt.Errorf("failed to get member: %w", err)
	}
	if member == nil {
		return fmt.Errorf("wallet is not a member of room")
	}
	
	return nil
}

// getCurrentTimestamp returns current timestamp as int64
func getCurrentTimestamp() int64 {
	return time.Now().Unix()
}