package services

import (
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
	"github.com/emiyaio/solana-wallet-service/internal/services/ai"
	"github.com/emiyaio/solana-wallet-service/internal/services/blockchain"
	"github.com/emiyaio/solana-wallet-service/internal/services/room"
	"github.com/emiyaio/solana-wallet-service/internal/services/token"
)

// Services holds all service instances
type Services struct {
	// Core room services
	Room                room.RoomService
	WebSocket           room.WebSocketService
	SubscriptionManager room.SubscriptionManager
	
	// Token services
	TokenMarket     token.MarketService
	SolanaTracker   token.SolanaTrackerService
	TokenAnalysis   token.AnalysisService
	
	// Blockchain services
	QuickNode           blockchain.QuickNodeService
	TransactionProcessor blockchain.TransactionProcessor
	
	// AI services
	LangChain ai.LangChainService
}

// NewServices creates and returns all service instances
func NewServices(repos *repositories.Repositories, cfg *config.Config, logger *logrus.Logger) *Services {
	// External services
	solanaTrackerService := token.NewSolanaTrackerService(&cfg.ExternalAPIs.SolanaTracker, logger)
	
	// Token services
	marketService := token.NewMarketService(
		repos.Token,
		solanaTrackerService,
		logger,
	)
	
	// Blockchain services
	transactionProcessor := blockchain.NewTransactionProcessor(
		&cfg.ExternalAPIs.QuickNode,
		repos.Token,
		logger,
	)
	quickNodeService := blockchain.NewQuickNodeService(
		&cfg.ExternalAPIs.QuickNode,
		logger,
	)
	
	// Room services
	roomService := room.NewRoomService(repos.Room, logger)
	wsService := room.NewWebSocketService(repos.Room, roomService, logger)
	subscriptionManager := room.NewSubscriptionManager(
		quickNodeService,
		transactionProcessor,
		repos.Room,
		wsService,
		logger,
	)
	
	// AI services
	langChainService := ai.NewLangChainService(
		&cfg.ExternalAPIs.OpenAI,
		repos.Token,
		marketService,
		solanaTrackerService,
		logger,
	)
	
	return &Services{
		Room:                 roomService,
		WebSocket:            wsService,
		SubscriptionManager:  subscriptionManager,
		TokenMarket:          marketService,
		SolanaTracker:        solanaTrackerService,
		QuickNode:            quickNodeService,
		TransactionProcessor: transactionProcessor,
		LangChain:            langChainService,
	}
}