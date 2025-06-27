package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
	"github.com/emiyaio/solana-wallet-service/internal/domain/models"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
	"github.com/emiyaio/solana-wallet-service/internal/handlers"
	"github.com/emiyaio/solana-wallet-service/internal/services"
	"github.com/emiyaio/solana-wallet-service/pkg/database"
	"github.com/emiyaio/solana-wallet-service/pkg/logger"
	"github.com/emiyaio/solana-wallet-service/pkg/redis"
)

func main() {
	// Load configuration
	cfg, err := config.Load("configs/config.yaml")
	if err != nil {
		panic(fmt.Sprintf("Failed to load config: %v", err))
	}

	// Initialize logger
	log, err := logger.InitLogger(cfg.Log)
	if err != nil {
		panic(fmt.Sprintf("Failed to initialize logger: %v", err))
	}

	log.Info("Starting Solana Wallet Service...")

	// Initialize database
	dbConn, err := database.NewPostgresConnection(cfg.Database)
	if err != nil {
		log.WithError(err).Fatal("Failed to connect to database")
	}
	defer dbConn.Close()
	log.Info("Database connected successfully")

	// Initialize Redis
	redisClient, err := redis.NewRedisClient(cfg.Redis)
	if err != nil {
		log.WithError(err).Fatal("Failed to connect to Redis")
	}
	defer redisClient.Close()
	log.Info("Redis connected successfully")

	// Auto-migrate database schema
	if err := dbConn.AutoMigrate(
		&models.Token{},
		&models.TokenMarketData{},
		&models.TokenTrendingRanking{},
		&models.TokenTopHolders{},
		&models.TokenTransactionStats{},
		&models.TradeRoom{},
		&models.RoomMember{},
		&models.SharedInfo{},
		&models.TradeEvent{},
		&models.Trader{},
		&models.SmartMoneyTransaction{},
		&models.TransactionAnalysis{},
		&models.WalletFollowing{},
	); err != nil {
		log.WithError(err).Fatal("Failed to auto-migrate database")
	}
	log.Info("Database migration completed")

	// Initialize repositories
	repos := repositories.NewRepositories(dbConn.DB)
	log.Info("Repositories initialized")

	// Initialize services
	services := services.NewServices(repos, cfg, log)
	log.Info("Services initialized")

	// Start WebSocket heartbeat monitoring
	services.WebSocket.StartHeartbeat()
	defer services.WebSocket.StopHeartbeat()

	// Start QuickNode WebSocket connection
	go func() {
		if err := services.QuickNode.Connect(); err != nil {
			log.WithError(err).Error("Failed to connect to QuickNode WebSocket")
		}
	}()
	defer services.QuickNode.Disconnect()

	// Initialize router and setup routes
	router := handlers.NewRouter(services, log)
	router.SetupRoutes()
	log.Info("Routes configured")

	// Create HTTP server
	server := &http.Server{
		Addr:           cfg.Server.Port,
		Handler:        router.GetEngine(),
		ReadTimeout:    cfg.Server.ReadTimeout,
		WriteTimeout:   cfg.Server.WriteTimeout,
		MaxHeaderBytes: cfg.Server.MaxHeaderBytes,
	}

	// Start server in a goroutine
	go func() {
		log.WithField("port", cfg.Server.Port).Info("Server starting...")
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.WithError(err).Fatal("Failed to start server")
		}
	}()

	// Start background tasks
	go startBackgroundTasks(services, log, cfg)

	log.Info("Solana Wallet Service started successfully")

	// Wait for interrupt signal to gracefully shutdown the server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info("Shutting down server...")

	// Create a deadline for graceful shutdown
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Shutdown server
	if err := server.Shutdown(ctx); err != nil {
		log.WithError(err).Error("Server forced to shutdown")
	} else {
		log.Info("Server shutdown gracefully")
	}
}

// startBackgroundTasks starts various background tasks
func startBackgroundTasks(services *services.Services, log *logrus.Logger, cfg *config.Config) {
	// Room cleanup ticker
	roomCleanupTicker := time.NewTicker(cfg.Room.CleanupInterval)
	defer roomCleanupTicker.Stop()

	// Market data sync ticker - use unified sync interval for now
	marketSyncTicker := time.NewTicker(cfg.SyncScheduler.UnifiedSyncInterval)
	defer marketSyncTicker.Stop()

	// Trending tokens sync ticker
	trendingSyncTicker := time.NewTicker(cfg.SyncScheduler.TrendingTokensInterval)
	defer trendingSyncTicker.Stop()

	for {
		select {
		case <-roomCleanupTicker.C:
			// Clean up expired rooms
			if err := services.Room.CleanupExpiredRooms(context.Background()); err != nil {
				log.WithError(err).Error("Failed to cleanup expired rooms")
			}

		case <-marketSyncTicker.C:
			// Sync market data for all tokens
			go func() {
				if err := services.TokenMarket.SyncAllTokensMarketData(context.Background()); err != nil {
					log.WithError(err).Error("Failed to sync market data")
				}
			}()

		case <-trendingSyncTicker.C:
			// Sync trending tokens from SolanaTracker
			go func() {
				if _, err := services.SolanaTracker.GetTrendingTokens("24h"); err != nil {
					log.WithError(err).Warn("Failed to sync trending tokens")
				} else {
					log.Info("Trending tokens synced successfully")
				}
			}()
		}
	}
}