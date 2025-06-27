package room

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/domain/models"
	"github.com/wallet/service/internal/domain/repositories"
)

// WebSocketService manages WebSocket connections for trading rooms
type WebSocketService interface {
	// Connection management
	HandleConnection(conn *websocket.Conn, roomID, walletAddress string) error
	DisconnectClient(roomID, walletAddress string)
	GetRoomConnections(roomID string) []*Client
	
	// Broadcasting
	BroadcastToRoom(roomID string, message *Message) error
	BroadcastToRoomExcept(roomID, excludeWallet string, message *Message) error
	SendToClient(roomID, walletAddress string, message *Message) error
	
	// Room events
	NotifyMemberJoined(roomID string, member *models.RoomMember) error
	NotifyMemberLeft(roomID, walletAddress string) error
	NotifySharedInfo(roomID string, info *models.SharedInfo) error
	NotifyTradeEvent(roomID string, event *models.TradeEvent) error
	NotifyRoomUpdate(roomID string, room *models.TradeRoom) error
	
	// Health monitoring
	StartHeartbeat()
	StopHeartbeat()
	CleanupInactiveConnections()
}

type webSocketService struct {
	rooms       map[string]*Room          // roomID -> Room
	clients     map[string]*Client        // connectionID -> Client
	roomRepo    repositories.RoomRepository
	roomService RoomService
	logger      *logrus.Logger
	mu          sync.RWMutex
	heartbeat   *time.Ticker
	stopChan    chan bool
}

// Room represents a WebSocket room with multiple clients
type Room struct {
	ID          string             `json:"id"`
	Clients     map[string]*Client `json:"clients"` // walletAddress -> Client
	mu          sync.RWMutex
}

// Client represents a WebSocket client connection
type Client struct {
	ID            string          `json:"id"`
	Conn          *websocket.Conn `json:"-"`
	RoomID        string          `json:"room_id"`
	WalletAddress string          `json:"wallet_address"`
	LastPing      time.Time       `json:"last_ping"`
	Send          chan *Message   `json:"-"`
	mu            sync.Mutex
}

// Message types for WebSocket communication
type MessageType string

const (
	// Client to server messages
	MessageTypeJoin      MessageType = "join"
	MessageTypeLeave     MessageType = "leave"
	MessageTypeShareInfo MessageType = "share_info"
	MessageTypePing      MessageType = "ping"
	
	// Server to client messages
	MessageTypeMemberJoined  MessageType = "member_joined"
	MessageTypeMemberLeft    MessageType = "member_left"
	MessageTypeSharedInfo    MessageType = "shared_info"
	MessageTypeTradeEvent    MessageType = "trade_event"
	MessageTypeRoomUpdate    MessageType = "room_update"
	MessageTypePong          MessageType = "pong"
	MessageTypeError         MessageType = "error"
)

// Message represents a WebSocket message
type Message struct {
	Type      MessageType     `json:"type"`
	Data      interface{}     `json:"data"`
	Timestamp time.Time       `json:"timestamp"`
	From      string          `json:"from,omitempty"`
}

// NewWebSocketService creates a new WebSocket service instance
func NewWebSocketService(roomRepo repositories.RoomRepository, roomService RoomService, logger *logrus.Logger) WebSocketService {
	return &webSocketService{
		rooms:       make(map[string]*Room),
		clients:     make(map[string]*Client),
		roomRepo:    roomRepo,
		roomService: roomService,
		logger:      logger,
		stopChan:    make(chan bool),
	}
}

// HandleConnection handles a new WebSocket connection
func (ws *webSocketService) HandleConnection(conn *websocket.Conn, roomID, walletAddress string) error {
	// Verify room exists and user is a member
	room, err := ws.roomService.GetRoom(context.Background(), roomID)
	if err != nil {
		return fmt.Errorf("failed to get room: %w", err)
	}
	
	members, err := ws.roomService.GetRoomMembers(context.Background(), roomID)
	if err != nil {
		return fmt.Errorf("failed to get room members: %w", err)
	}
	
	// Check if wallet is a member
	isMember := false
	for _, member := range members {
		if member.WalletAddress == walletAddress {
			isMember = true
			break
		}
	}
	
	if !isMember {
		return fmt.Errorf("wallet %s is not a member of room %s", walletAddress, roomID)
	}
	
	// Create client
	clientID := uuid.New().String()
	client := &Client{
		ID:            clientID,
		Conn:          conn,
		RoomID:        roomID,
		WalletAddress: walletAddress,
		LastPing:      time.Now(),
		Send:          make(chan *Message, 256),
	}
	
	// Add client to room
	ws.mu.Lock()
	if _, exists := ws.rooms[roomID]; !exists {
		ws.rooms[roomID] = &Room{
			ID:      roomID,
			Clients: make(map[string]*Client),
		}
	}
	ws.rooms[roomID].Clients[walletAddress] = client
	ws.clients[clientID] = client
	ws.mu.Unlock()
	
	// Update member status to online
	if err := ws.roomService.UpdateMemberStatus(context.Background(), roomID, walletAddress, true); err != nil {
		ws.logger.WithFields(logrus.Fields{
			"error":    err,
			"room_id":  roomID,
			"wallet":   walletAddress,
		}).Error("Failed to update member status to online")
	}
	
	// Start goroutines for this client
	go ws.writePump(client)
	go ws.readPump(client)
	
	// Notify other members that user joined
	ws.NotifyMemberJoined(roomID, &models.RoomMember{
		WalletAddress: walletAddress,
		IsOnline:      true,
	})
	
	ws.logger.WithFields(logrus.Fields{
		"client_id": clientID,
		"room_id":   roomID,
		"wallet":    walletAddress,
	}).Info("WebSocket client connected")
	
	return nil
}

// DisconnectClient disconnects a client from WebSocket
func (ws *webSocketService) DisconnectClient(roomID, walletAddress string) {
	ws.mu.Lock()
	defer ws.mu.Unlock()
	
	if room, exists := ws.rooms[roomID]; exists {
		if client, exists := room.Clients[walletAddress]; exists {
			close(client.Send)
			client.Conn.Close()
			delete(room.Clients, walletAddress)
			delete(ws.clients, client.ID)
			
			// Remove empty rooms
			if len(room.Clients) == 0 {
				delete(ws.rooms, roomID)
			}
			
			// Update member status to offline
			if err := ws.roomService.UpdateMemberStatus(context.Background(), roomID, walletAddress, false); err != nil {
				ws.logger.WithFields(logrus.Fields{
					"error":   err,
					"room_id": roomID,
					"wallet":  walletAddress,
				}).Error("Failed to update member status to offline")
			}
			
			// Notify other members that user left
			ws.NotifyMemberLeft(roomID, walletAddress)
			
			ws.logger.WithFields(logrus.Fields{
				"room_id": roomID,
				"wallet":  walletAddress,
			}).Info("WebSocket client disconnected")
		}
	}
}

// GetRoomConnections returns all active connections in a room
func (ws *webSocketService) GetRoomConnections(roomID string) []*Client {
	ws.mu.RLock()
	defer ws.mu.RUnlock()
	
	var clients []*Client
	if room, exists := ws.rooms[roomID]; exists {
		for _, client := range room.Clients {
			clients = append(clients, client)
		}
	}
	return clients
}

// BroadcastToRoom broadcasts a message to all clients in a room
func (ws *webSocketService) BroadcastToRoom(roomID string, message *Message) error {
	ws.mu.RLock()
	room, exists := ws.rooms[roomID]
	ws.mu.RUnlock()
	
	if !exists {
		return fmt.Errorf("room %s not found", roomID)
	}
	
	room.mu.RLock()
	defer room.mu.RUnlock()
	
	message.Timestamp = time.Now()
	
	for _, client := range room.Clients {
		select {
		case client.Send <- message:
		default:
			// Client channel is full, disconnect client
			ws.DisconnectClient(roomID, client.WalletAddress)
		}
	}
	
	return nil
}

// BroadcastToRoomExcept broadcasts a message to all clients in a room except one
func (ws *webSocketService) BroadcastToRoomExcept(roomID, excludeWallet string, message *Message) error {
	ws.mu.RLock()
	room, exists := ws.rooms[roomID]
	ws.mu.RUnlock()
	
	if !exists {
		return fmt.Errorf("room %s not found", roomID)
	}
	
	room.mu.RLock()
	defer room.mu.RUnlock()
	
	message.Timestamp = time.Now()
	
	for walletAddress, client := range room.Clients {
		if walletAddress == excludeWallet {
			continue
		}
		
		select {
		case client.Send <- message:
		default:
			// Client channel is full, disconnect client
			ws.DisconnectClient(roomID, client.WalletAddress)
		}
	}
	
	return nil
}

// SendToClient sends a message to a specific client
func (ws *webSocketService) SendToClient(roomID, walletAddress string, message *Message) error {
	ws.mu.RLock()
	room, exists := ws.rooms[roomID]
	ws.mu.RUnlock()
	
	if !exists {
		return fmt.Errorf("room %s not found", roomID)
	}
	
	room.mu.RLock()
	client, exists := room.Clients[walletAddress]
	room.mu.RUnlock()
	
	if !exists {
		return fmt.Errorf("client %s not found in room %s", walletAddress, roomID)
	}
	
	message.Timestamp = time.Now()
	
	select {
	case client.Send <- message:
		return nil
	default:
		// Client channel is full, disconnect client
		ws.DisconnectClient(roomID, walletAddress)
		return fmt.Errorf("client %s channel is full", walletAddress)
	}
}

// Notification methods
func (ws *webSocketService) NotifyMemberJoined(roomID string, member *models.RoomMember) error {
	message := &Message{
		Type: MessageTypeMemberJoined,
		Data: member,
	}
	return ws.BroadcastToRoom(roomID, message)
}

func (ws *webSocketService) NotifyMemberLeft(roomID, walletAddress string) error {
	message := &Message{
		Type: MessageTypeMemberLeft,
		Data: map[string]interface{}{
			"wallet_address": walletAddress,
		},
	}
	return ws.BroadcastToRoom(roomID, message)
}

func (ws *webSocketService) NotifySharedInfo(roomID string, info *models.SharedInfo) error {
	message := &Message{
		Type: MessageTypeSharedInfo,
		Data: info,
		From: info.SharerAddress,
	}
	return ws.BroadcastToRoom(roomID, message)
}

func (ws *webSocketService) NotifyTradeEvent(roomID string, event *models.TradeEvent) error {
	message := &Message{
		Type: MessageTypeTradeEvent,
		Data: event,
		From: event.WalletAddress,
	}
	return ws.BroadcastToRoom(roomID, message)
}

func (ws *webSocketService) NotifyRoomUpdate(roomID string, room *models.TradeRoom) error {
	message := &Message{
		Type: MessageTypeRoomUpdate,
		Data: room,
	}
	return ws.BroadcastToRoom(roomID, message)
}

// readPump handles reading messages from WebSocket connection
func (ws *webSocketService) readPump(client *Client) {
	defer func() {
		ws.DisconnectClient(client.RoomID, client.WalletAddress)
	}()
	
	// Set read deadline and pong handler
	client.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	client.Conn.SetPongHandler(func(string) error {
		client.mu.Lock()
		client.LastPing = time.Now()
		client.mu.Unlock()
		client.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})
	
	for {
		var message Message
		err := client.Conn.ReadJSON(&message)
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				ws.logger.WithFields(logrus.Fields{
					"error":  err,
					"client": client.WalletAddress,
					"room":   client.RoomID,
				}).Error("WebSocket read error")
			}
			break
		}
		
		// Handle different message types
		ws.handleMessage(client, &message)
	}
}

// writePump handles writing messages to WebSocket connection
func (ws *webSocketService) writePump(client *Client) {
	ticker := time.NewTicker(54 * time.Second)
	defer func() {
		ticker.Stop()
		client.Conn.Close()
	}()
	
	for {
		select {
		case message, ok := <-client.Send:
			client.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if !ok {
				client.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			
			if err := client.Conn.WriteJSON(message); err != nil {
				ws.logger.WithFields(logrus.Fields{
					"error":  err,
					"client": client.WalletAddress,
					"room":   client.RoomID,
				}).Error("WebSocket write error")
				return
			}
			
		case <-ticker.C:
			client.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := client.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// handleMessage processes incoming WebSocket messages
func (ws *webSocketService) handleMessage(client *Client, message *Message) {
	switch message.Type {
	case MessageTypePing:
		// Respond with pong
		pongMessage := &Message{
			Type:      MessageTypePong,
			Timestamp: time.Now(),
		}
		client.Send <- pongMessage
		
	case MessageTypeShareInfo:
		// Handle share info message
		if data, ok := message.Data.(map[string]interface{}); ok {
			ws.handleShareInfoMessage(client, data)
		}
		
	default:
		ws.logger.WithFields(logrus.Fields{
			"type":   message.Type,
			"client": client.WalletAddress,
			"room":   client.RoomID,
		}).Warn("Unknown message type received")
	}
}

// handleShareInfoMessage handles share info messages from clients
func (ws *webSocketService) handleShareInfoMessage(client *Client, data map[string]interface{}) {
	// Convert data to ShareInfoRequest
	dataBytes, err := json.Marshal(data)
	if err != nil {
		ws.sendErrorMessage(client, "Invalid share info data")
		return
	}
	
	var req ShareInfoRequest
	if err := json.Unmarshal(dataBytes, &req); err != nil {
		ws.sendErrorMessage(client, "Invalid share info format")
		return
	}
	
	// Set room ID and sharer address from client
	req.RoomID = client.RoomID
	req.SharerAddress = client.WalletAddress
	
	// Create shared info through service
	info, err := ws.roomService.ShareInfo(context.Background(), &req)
	if err != nil {
		ws.sendErrorMessage(client, fmt.Sprintf("Failed to share info: %v", err))
		return
	}
	
	// Broadcast to all room members
	ws.NotifySharedInfo(client.RoomID, info)
}

// sendErrorMessage sends an error message to a client
func (ws *webSocketService) sendErrorMessage(client *Client, errorMsg string) {
	message := &Message{
		Type: MessageTypeError,
		Data: map[string]interface{}{
			"error": errorMsg,
		},
		Timestamp: time.Now(),
	}
	
	select {
	case client.Send <- message:
	default:
		// Channel is full, disconnect client
		ws.DisconnectClient(client.RoomID, client.WalletAddress)
	}
}

// StartHeartbeat starts the heartbeat monitoring
func (ws *webSocketService) StartHeartbeat() {
	ws.heartbeat = time.NewTicker(30 * time.Second)
	go func() {
		for {
			select {
			case <-ws.heartbeat.C:
				ws.CleanupInactiveConnections()
			case <-ws.stopChan:
				return
			}
		}
	}()
}

// StopHeartbeat stops the heartbeat monitoring
func (ws *webSocketService) StopHeartbeat() {
	if ws.heartbeat != nil {
		ws.heartbeat.Stop()
	}
	close(ws.stopChan)
}

// CleanupInactiveConnections removes inactive connections
func (ws *webSocketService) CleanupInactiveConnections() {
	ws.mu.Lock()
	defer ws.mu.Unlock()
	
	threshold := time.Now().Add(-90 * time.Second)
	
	for roomID, room := range ws.rooms {
		room.mu.Lock()
		for walletAddress, client := range room.Clients {
			client.mu.Lock()
			if client.LastPing.Before(threshold) {
				// Client is inactive, disconnect
				close(client.Send)
				client.Conn.Close()
				delete(room.Clients, walletAddress)
				delete(ws.clients, client.ID)
				
				ws.logger.WithFields(logrus.Fields{
					"room_id": roomID,
					"wallet":  walletAddress,
				}).Info("Disconnected inactive WebSocket client")
			}
			client.mu.Unlock()
		}
		
		// Remove empty rooms
		if len(room.Clients) == 0 {
			delete(ws.rooms, roomID)
		}
		room.mu.Unlock()
	}
}