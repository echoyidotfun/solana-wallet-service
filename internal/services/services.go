package services

import (
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/config"
	"github.com/wallet/service/internal/domain/repositories"
	"github.com/wallet/service/internal/services/room"
	"github.com/wallet/service/internal/services/token"
)

// Services holds all service instances
type Services struct {
	Room         room.RoomService
	WebSocket    room.WebSocketService
	TokenMarket  token.MarketService
	TokenAnalysis token.AnalysisService
}

// NewServices creates and returns all service instances
func NewServices(repos *repositories.Repositories, cfg *config.Config, logger *logrus.Logger) *Services {
	// Room services
	roomService := room.NewRoomService(repos.Room, logger)
	wsService := room.NewWebSocketService(repos.Room, roomService, logger)
	
	// Token services
	marketService := token.NewMarketService(
		repos.Token,
		logger,
		cfg.ExternalAPIs.SolanaTracker.BaseURL,
		cfg.ExternalAPIs.SolanaTracker.APIKey,
	)
	analysisService := token.NewAnalysisService(
		repos.Token,
		repos.Transaction,
		marketService,
		logger,
	)
	
	return &Services{
		Room:          roomService,
		WebSocket:     wsService,
		TokenMarket:   marketService,
		TokenAnalysis: analysisService,
	}
}