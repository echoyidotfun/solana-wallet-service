package handlers

import (
	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/handlers/api"
	"github.com/wallet/service/internal/handlers/websocket"
	"github.com/wallet/service/internal/middleware"
	"github.com/wallet/service/internal/services"
)

// Router holds all route handlers
type Router struct {
	engine          *gin.Engine
	services        *services.Services
	logger          *logrus.Logger
	roomHandler     *api.RoomHandler
	tokenHandler    *api.TokenHandler
	wsRoomHandler   *websocket.RoomWebSocketHandler
}

// NewRouter creates a new router instance
func NewRouter(services *services.Services, logger *logrus.Logger) *Router {
	// Create Gin engine
	gin.SetMode(gin.ReleaseMode) // Set to release mode
	engine := gin.New()
	
	// Add global middleware
	engine.Use(gin.Recovery())
	engine.Use(middleware.Logger(logger))
	engine.Use(middleware.CORS())
	
	// Create handlers
	roomHandler := api.NewRoomHandler(services.Room, services.WebSocket, logger)
	tokenHandler := api.NewTokenHandler(services.TokenMarket, services.TokenAnalysis, logger)
	wsRoomHandler := websocket.NewRoomWebSocketHandler(services.WebSocket, logger)
	
	return &Router{
		engine:        engine,
		services:      services,
		logger:        logger,
		roomHandler:   roomHandler,
		tokenHandler:  tokenHandler,
		wsRoomHandler: wsRoomHandler,
	}
}

// SetupRoutes configures all API routes
func (r *Router) SetupRoutes() {
	// Health check endpoint
	r.engine.GET("/health", r.healthCheck)
	r.engine.GET("/", r.healthCheck)
	
	// API v1 routes
	v1 := r.engine.Group("/api/v1")
	{
		// Room API routes
		r.roomHandler.RegisterRoutes(v1)
		
		// Token API routes  
		r.tokenHandler.RegisterRoutes(v1)
		
		// WebSocket routes
		r.wsRoomHandler.RegisterRoutes(v1)
	}
	
	// API documentation endpoint
	r.engine.GET("/api/docs", r.apiDocs)
}

// GetEngine returns the Gin engine instance
func (r *Router) GetEngine() *gin.Engine {
	return r.engine
}

// healthCheck endpoint
func (r *Router) healthCheck(c *gin.Context) {
	c.JSON(200, gin.H{
		"status":    "healthy",
		"service":   "solana-wallet-service",
		"version":   "1.0.0",
		"timestamp": "2024-01-01T00:00:00Z",
	})
}

// apiDocs endpoint returns API documentation
func (r *Router) apiDocs(c *gin.Context) {
	docs := map[string]interface{}{
		"service": "Solana Wallet Service API",
		"version": "1.0.0",
		"endpoints": map[string]interface{}{
			"rooms": map[string]interface{}{
				"POST /api/v1/rooms":                    "Create a new trading room",
				"GET /api/v1/rooms":                     "List all rooms",
				"GET /api/v1/rooms/{roomId}":            "Get room details",
				"PUT /api/v1/rooms/{roomId}":            "Update room settings",
				"DELETE /api/v1/rooms/{roomId}":         "Delete room",
				"POST /api/v1/rooms/{roomId}/join":      "Join a room",
				"POST /api/v1/rooms/{roomId}/leave":     "Leave a room",
				"GET /api/v1/rooms/{roomId}/members":    "Get room members",
				"POST /api/v1/rooms/{roomId}/share":     "Share information in room",
				"GET /api/v1/rooms/{roomId}/shares":     "Get shared information",
				"POST /api/v1/rooms/{roomId}/events":    "Record trade event",
				"GET /api/v1/rooms/{roomId}/events":     "Get trade events",
				"GET /api/v1/users/{address}/rooms":     "Get user's rooms",
			},
			"tokens": map[string]interface{}{
				"POST /api/v1/tokens":                        "Create a new token",
				"GET /api/v1/tokens":                         "List all tokens",
				"GET /api/v1/tokens/mint/{mintAddress}":      "Get token by mint address",
				"GET /api/v1/tokens/{tokenId}/market":        "Get market data",
				"POST /api/v1/tokens/mint/{mintAddress}/sync": "Sync market data",
				"POST /api/v1/tokens/sync-all":               "Sync all tokens market data",
				"GET /api/v1/tokens/trending":                "Get trending tokens",
				"GET /api/v1/tokens/{tokenId}/holders":       "Get top holders",
				"GET /api/v1/tokens/{tokenId}/stats":         "Get transaction stats",
				"GET /api/v1/tokens/{tokenId}/analyze":       "Analyze token",
				"GET /api/v1/tokens/{tokenId}/trends":        "Analyze trends",
				"GET /api/v1/tokens/{tokenId}/sentiment":     "Analyze sentiment",
				"GET /api/v1/tokens/{tokenId}/risk":          "Assess risk",
				"GET /api/v1/tokens/{tokenId}/volatility":    "Get volatility metrics",
				"GET /api/v1/tokens/{tokenId}/recommendation": "Get AI recommendation",
				"POST /api/v1/tokens/batch/analyze":          "Batch analyze tokens",
			},
			"websockets": map[string]interface{}{
				"GET /api/v1/ws/rooms/{roomId}":              "WebSocket connection for room (query: wallet=address)",
				"GET /api/v1/ws/rooms/{roomId}/connections":  "Get active connections",
				"POST /api/v1/ws/rooms/{roomId}/broadcast":   "Broadcast message to room",
			},
		},
		"websocket_messages": map[string]interface{}{
			"client_to_server": []string{
				"join", "leave", "share_info", "ping",
			},
			"server_to_client": []string{
				"member_joined", "member_left", "shared_info", "trade_event", "room_update", "pong", "error",
			},
		},
	}
	
	c.JSON(200, docs)
}