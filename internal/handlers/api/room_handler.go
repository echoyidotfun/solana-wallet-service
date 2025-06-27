package api

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/domain/models"
	"github.com/emiyaio/solana-wallet-service/internal/services/room"
)

// RoomHandler handles HTTP requests for room management
type RoomHandler struct {
	roomService room.RoomService
	wsService   room.WebSocketService
	logger      *logrus.Logger
}

// NewRoomHandler creates a new room handler
func NewRoomHandler(roomService room.RoomService, wsService room.WebSocketService, logger *logrus.Logger) *RoomHandler {
	return &RoomHandler{
		roomService: roomService,
		wsService:   wsService,
		logger:      logger,
	}
}

// CreateRoom creates a new trading room
func (h *RoomHandler) CreateRoom(c *gin.Context) {
	var req room.CreateRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	room, err := h.roomService.CreateRoom(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":   err,
			"creator": req.CreatorAddress,
		}).Error("Failed to create room")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create room"})
		return
	}
	
	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"data":    room,
	})
}

// GetRoom gets room details by room ID
func (h *RoomHandler) GetRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	room, err := h.roomService.GetRoom(c.Request.Context(), roomID)
	if err != nil {
		if err == room.ErrRoomNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "Room not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    room,
	})
}

// ListRooms lists trading rooms with pagination
func (h *RoomHandler) ListRooms(c *gin.Context) {
	limitStr := c.DefaultQuery("limit", "20")
	offsetStr := c.DefaultQuery("offset", "0")
	statusStr := c.Query("status")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}
	
	var status models.RoomStatus
	if statusStr != "" {
		status = models.RoomStatus(statusStr)
	}
	
	rooms, err := h.roomService.ListRooms(c.Request.Context(), status, limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to list rooms"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    rooms,
		"pagination": gin.H{
			"limit":  limit,
			"offset": offset,
			"count":  len(rooms),
		},
	})
}

// GetUserRooms gets rooms created by a user
func (h *RoomHandler) GetUserRooms(c *gin.Context) {
	creatorAddress := c.Param("address")
	if creatorAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "creator address is required"})
		return
	}
	
	limitStr := c.DefaultQuery("limit", "20")
	offsetStr := c.DefaultQuery("offset", "0")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}
	
	rooms, err := h.roomService.GetUserRooms(c.Request.Context(), creatorAddress, limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get user rooms"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    rooms,
		"pagination": gin.H{
			"limit":  limit,
			"offset": offset,
			"count":  len(rooms),
		},
	})
}

// UpdateRoom updates room settings
func (h *RoomHandler) UpdateRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	var req room.UpdateRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	updatedRoom, err := h.roomService.UpdateRoom(c.Request.Context(), roomID, &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	// Notify WebSocket clients about room update
	h.wsService.NotifyRoomUpdate(roomID, updatedRoom)
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    updatedRoom,
	})
}

// CloseRoom closes a trading room
func (h *RoomHandler) CloseRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	creatorAddress := c.GetHeader("X-Creator-Address")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	if creatorAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "creator address is required"})
		return
	}
	
	if err := h.roomService.CloseRoom(c.Request.Context(), roomID, creatorAddress); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Room closed successfully",
	})
}

// DeleteRoom deletes a trading room
func (h *RoomHandler) DeleteRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	creatorAddress := c.GetHeader("X-Creator-Address")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	if creatorAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "creator address is required"})
		return
	}
	
	if err := h.roomService.DeleteRoom(c.Request.Context(), roomID, creatorAddress); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Room deleted successfully",
	})
}

// JoinRoom joins a trading room
func (h *RoomHandler) JoinRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	
	var req struct {
		WalletAddress string `json:"wallet_address" binding:"required"`
		Password      string `json:"password"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	member, err := h.roomService.JoinRoom(c.Request.Context(), roomID, req.WalletAddress, req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    member,
	})
}

// LeaveRoom leaves a trading room
func (h *RoomHandler) LeaveRoom(c *gin.Context) {
	roomID := c.Param("roomId")
	walletAddress := c.GetHeader("X-Wallet-Address")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	if walletAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "wallet address is required"})
		return
	}
	
	if err := h.roomService.LeaveRoom(c.Request.Context(), roomID, walletAddress); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Left room successfully",
	})
}

// GetRoomMembers gets all members of a room
func (h *RoomHandler) GetRoomMembers(c *gin.Context) {
	roomID := c.Param("roomId")
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	members, err := h.roomService.GetRoomMembers(c.Request.Context(), roomID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get room members"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    members,
		"count":   len(members),
	})
}

// KickMember kicks a member from the room
func (h *RoomHandler) KickMember(c *gin.Context) {
	roomID := c.Param("roomId")
	targetAddress := c.Param("address")
	creatorAddress := c.GetHeader("X-Creator-Address")
	
	if roomID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "room_id is required"})
		return
	}
	
	if targetAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "target address is required"})
		return
	}
	
	if creatorAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "creator address is required"})
		return
	}
	
	if err := h.roomService.KickMember(c.Request.Context(), roomID, creatorAddress, targetAddress); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Member kicked successfully",
	})
}

// ShareInfo shares information in a room
func (h *RoomHandler) ShareInfo(c *gin.Context) {
	roomID := c.Param("roomId")
	
	var req room.ShareInfoRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	req.RoomID = roomID
	
	info, err := h.roomService.ShareInfo(c.Request.Context(), &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	// Notify WebSocket clients
	h.wsService.NotifySharedInfo(roomID, info)
	
	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"data":    info,
	})
}

// GetSharedInfos gets shared information from a room
func (h *RoomHandler) GetSharedInfos(c *gin.Context) {
	roomID := c.Param("roomId")
	
	limitStr := c.DefaultQuery("limit", "20")
	offsetStr := c.DefaultQuery("offset", "0")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}
	
	infos, err := h.roomService.GetSharedInfos(c.Request.Context(), roomID, limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get shared information"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    infos,
		"pagination": gin.H{
			"limit":  limit,
			"offset": offset,
			"count":  len(infos),
		},
	})
}

// UpdateSharedInfo updates shared information
func (h *RoomHandler) UpdateSharedInfo(c *gin.Context) {
	infoIDStr := c.Param("infoId")
	
	infoID, err := uuid.Parse(infoIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid info ID"})
		return
	}
	
	var req room.UpdateSharedInfoRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	info, err := h.roomService.UpdateSharedInfo(c.Request.Context(), infoID, &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    info,
	})
}

// DeleteSharedInfo deletes shared information
func (h *RoomHandler) DeleteSharedInfo(c *gin.Context) {
	infoIDStr := c.Param("infoId")
	sharerAddress := c.GetHeader("X-Sharer-Address")
	
	infoID, err := uuid.Parse(infoIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid info ID"})
		return
	}
	
	if sharerAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "sharer address is required"})
		return
	}
	
	if err := h.roomService.DeleteSharedInfo(c.Request.Context(), infoID, sharerAddress); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Shared info deleted successfully",
	})
}

// LikeSharedInfo likes shared information
func (h *RoomHandler) LikeSharedInfo(c *gin.Context) {
	infoIDStr := c.Param("infoId")
	
	infoID, err := uuid.Parse(infoIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid info ID"})
		return
	}
	
	if err := h.roomService.LikeSharedInfo(c.Request.Context(), infoID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to like shared info"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Liked successfully",
	})
}

// RecordTradeEvent records a trade event
func (h *RoomHandler) RecordTradeEvent(c *gin.Context) {
	roomID := c.Param("roomId")
	
	var req room.TradeEventRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	req.RoomID = roomID
	
	event, err := h.roomService.RecordTradeEvent(c.Request.Context(), &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	
	// Notify WebSocket clients
	h.wsService.NotifyTradeEvent(roomID, event)
	
	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"data":    event,
	})
}

// GetTradeEvents gets trade events from a room
func (h *RoomHandler) GetTradeEvents(c *gin.Context) {
	roomID := c.Param("roomId")
	
	limitStr := c.DefaultQuery("limit", "20")
	offsetStr := c.DefaultQuery("offset", "0")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}
	
	events, err := h.roomService.GetTradeEvents(c.Request.Context(), roomID, limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get trade events"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    events,
		"pagination": gin.H{
			"limit":  limit,
			"offset": offset,
			"count":  len(events),
		},
	})
}

// RegisterRoutes registers room API routes
func (h *RoomHandler) RegisterRoutes(router *gin.RouterGroup) {
	rooms := router.Group("/rooms")
	{
		// Room management
		rooms.POST("", h.CreateRoom)
		rooms.GET("", h.ListRooms)
		rooms.GET("/:roomId", h.GetRoom)
		rooms.PUT("/:roomId", h.UpdateRoom)
		rooms.DELETE("/:roomId", h.DeleteRoom)
		rooms.POST("/:roomId/close", h.CloseRoom)
		
		// Member management
		rooms.POST("/:roomId/join", h.JoinRoom)
		rooms.POST("/:roomId/leave", h.LeaveRoom)
		rooms.GET("/:roomId/members", h.GetRoomMembers)
		rooms.DELETE("/:roomId/members/:address", h.KickMember)
		
		// Content management
		rooms.POST("/:roomId/share", h.ShareInfo)
		rooms.GET("/:roomId/shares", h.GetSharedInfos)
		rooms.PUT("/shares/:infoId", h.UpdateSharedInfo)
		rooms.DELETE("/shares/:infoId", h.DeleteSharedInfo)
		rooms.POST("/shares/:infoId/like", h.LikeSharedInfo)
		
		// Trade events
		rooms.POST("/:roomId/events", h.RecordTradeEvent)
		rooms.GET("/:roomId/events", h.GetTradeEvents)
	}
	
	// User-specific routes
	users := router.Group("/users")
	{
		users.GET("/:address/rooms", h.GetUserRooms)
	}
}