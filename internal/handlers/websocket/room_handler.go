package websocket

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/services/room"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		// In production, implement proper origin checking
		return true
	},
}

// RoomWebSocketHandler handles WebSocket connections for trading rooms
type RoomWebSocketHandler struct {
	wsService room.WebSocketService
	logger    *logrus.Logger
}

// NewRoomWebSocketHandler creates a new WebSocket handler
func NewRoomWebSocketHandler(wsService room.WebSocketService, logger *logrus.Logger) *RoomWebSocketHandler {
	return &RoomWebSocketHandler{
		wsService: wsService,
		logger:    logger,
	}
}

// HandleRoomConnection handles WebSocket connection requests for rooms
func (h *RoomWebSocketHandler) HandleRoomConnection(c *gin.Context) {
	roomID := c.Param("roomId")
	walletAddress := c.Query("wallet")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	if walletAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "wallet address is required"})
		return
	}
	
	// Upgrade HTTP connection to WebSocket
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":   err,
			"room_id": roomID,
			"wallet":  walletAddress,
		}).Error("Failed to upgrade WebSocket connection")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to upgrade connection"})
		return
	}
	
	// Handle the WebSocket connection
	if err := h.wsService.HandleConnection(conn, roomID, walletAddress); err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":   err,
			"room_id": roomID,
			"wallet":  walletAddress,
		}).Error("Failed to handle WebSocket connection")
		
		conn.Close()
		return
	}
}

// GetRoomConnections returns active connections for a room
func (h *RoomWebSocketHandler) GetRoomConnections(c *gin.Context) {
	roomID := c.Param("roomId")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	connections := h.wsService.GetRoomConnections(roomID)
	
	// Convert to response format (remove sensitive data)
	var response []map[string]interface{}
	for _, client := range connections {
		response = append(response, map[string]interface{}{
			"wallet_address": client.WalletAddress,
			"last_ping":      client.LastPing,
		})
	}
	
	c.JSON(http.StatusOK, gin.H{
		"room_id":     roomID,
		"connections": response,
		"count":       len(response),
	})
}

// BroadcastMessage broadcasts a message to all clients in a room
func (h *RoomWebSocketHandler) BroadcastMessage(c *gin.Context) {
	roomID := c.Param("roomId")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	var req struct {
		Type string      `json:"type" binding:"required"`
		Data interface{} `json:"data" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	message := &room.Message{
		Type: room.MessageType(req.Type),
		Data: req.Data,
	}
	
	if err := h.wsService.BroadcastToRoom(roomID, message); err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":   err,
			"room_id": roomID,
		}).Error("Failed to broadcast message")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to broadcast message"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{"message": "Message broadcasted successfully"})
}

// RegisterRoutes registers WebSocket routes
func (h *RoomWebSocketHandler) RegisterRoutes(router *gin.RouterGroup) {
	ws := router.Group("/ws")
	{
		ws.GET("/rooms/:roomId", h.HandleRoomConnection)
		ws.GET("/rooms/:roomId/connections", h.GetRoomConnections)
		ws.POST("/rooms/:roomId/broadcast", h.BroadcastMessage)
	}
}