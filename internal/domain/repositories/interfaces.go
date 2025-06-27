package repositories

import (
	"context"

	"github.com/google/uuid"
	"github.com/wallet/service/internal/domain/models"
)

// TokenRepository defines the interface for token data access
type TokenRepository interface {
	Create(ctx context.Context, token *models.Token) error
	GetByID(ctx context.Context, id uuid.UUID) (*models.Token, error)
	GetByMintAddress(ctx context.Context, mintAddress string) (*models.Token, error)
	List(ctx context.Context, limit, offset int) ([]*models.Token, error)
	Update(ctx context.Context, token *models.Token) error
	Delete(ctx context.Context, id uuid.UUID) error
	
	// Market data methods
	CreateMarketData(ctx context.Context, data *models.TokenMarketData) error
	GetLatestMarketData(ctx context.Context, tokenID uuid.UUID) (*models.TokenMarketData, error)
	UpdateMarketData(ctx context.Context, data *models.TokenMarketData) error
	
	// Trending methods
	CreateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error
	GetTrendingTokens(ctx context.Context, category, timeframe string, limit int) ([]*models.TokenTrendingRanking, error)
	UpdateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error
	
	// Top holders methods
	CreateTopHolder(ctx context.Context, holder *models.TokenTopHolders) error
	GetTopHolders(ctx context.Context, tokenID uuid.UUID, limit int) ([]*models.TokenTopHolders, error)
	UpdateTopHolder(ctx context.Context, holder *models.TokenTopHolders) error
	
	// Transaction stats methods
	CreateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error
	GetTransactionStats(ctx context.Context, tokenID uuid.UUID, timeframe string) (*models.TokenTransactionStats, error)
	UpdateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error
}

// RoomRepository defines the interface for room data access
type RoomRepository interface {
	Create(ctx context.Context, room *models.TradeRoom) error
	GetByID(ctx context.Context, id uuid.UUID) (*models.TradeRoom, error)
	GetByRoomID(ctx context.Context, roomID string) (*models.TradeRoom, error)
	GetByCreator(ctx context.Context, creatorAddress string, limit, offset int) ([]*models.TradeRoom, error)
	List(ctx context.Context, status models.RoomStatus, limit, offset int) ([]*models.TradeRoom, error)
	Update(ctx context.Context, room *models.TradeRoom) error
	Delete(ctx context.Context, id uuid.UUID) error
	UpdateLastActivity(ctx context.Context, roomID uuid.UUID) error
	GetExpiredRooms(ctx context.Context) ([]*models.TradeRoom, error)
	
	// Member methods
	AddMember(ctx context.Context, member *models.RoomMember) error
	RemoveMember(ctx context.Context, roomID uuid.UUID, walletAddress string) error
	GetMembers(ctx context.Context, roomID uuid.UUID) ([]*models.RoomMember, error)
	GetMemberByAddress(ctx context.Context, roomID uuid.UUID, walletAddress string) (*models.RoomMember, error)
	UpdateMemberStatus(ctx context.Context, roomID uuid.UUID, walletAddress string, isOnline bool) error
	UpdateMemberLastSeen(ctx context.Context, roomID uuid.UUID, walletAddress string) error
	
	// Shared info methods
	CreateSharedInfo(ctx context.Context, info *models.SharedInfo) error
	GetSharedInfos(ctx context.Context, roomID uuid.UUID, limit, offset int) ([]*models.SharedInfo, error)
	GetSharedInfoByID(ctx context.Context, id uuid.UUID) (*models.SharedInfo, error)
	UpdateSharedInfo(ctx context.Context, info *models.SharedInfo) error
	DeleteSharedInfo(ctx context.Context, id uuid.UUID) error
	IncrementViewCount(ctx context.Context, id uuid.UUID) error
	IncrementLikeCount(ctx context.Context, id uuid.UUID) error
	
	// Trade event methods
	CreateTradeEvent(ctx context.Context, event *models.TradeEvent) error
	GetTradeEvents(ctx context.Context, roomID uuid.UUID, limit, offset int) ([]*models.TradeEvent, error)
	GetTradeEventsByWallet(ctx context.Context, walletAddress string, limit, offset int) ([]*models.TradeEvent, error)
}

// TransactionRepository defines the interface for transaction data access
type TransactionRepository interface {
	Create(ctx context.Context, tx *models.SmartMoneyTransaction) error
	GetByID(ctx context.Context, id uuid.UUID) (*models.SmartMoneyTransaction, error)
	GetBySignature(ctx context.Context, signature string) (*models.SmartMoneyTransaction, error)
	GetByWallet(ctx context.Context, walletAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error)
	GetByToken(ctx context.Context, tokenAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error)
	GetByWalletAndToken(ctx context.Context, walletAddress, tokenAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error)
	List(ctx context.Context, limit, offset int) ([]*models.SmartMoneyTransaction, error)
	Update(ctx context.Context, tx *models.SmartMoneyTransaction) error
	Delete(ctx context.Context, id uuid.UUID) error
	GetRecentTransactions(ctx context.Context, hours int, limit int) ([]*models.SmartMoneyTransaction, error)
	
	// Analysis methods
	CreateAnalysis(ctx context.Context, analysis *models.TransactionAnalysis) error
	GetAnalysisByTransactionID(ctx context.Context, transactionID uuid.UUID) ([]*models.TransactionAnalysis, error)
	UpdateAnalysis(ctx context.Context, analysis *models.TransactionAnalysis) error
	DeleteAnalysis(ctx context.Context, id uuid.UUID) error
}

// TraderRepository defines the interface for trader data access
type TraderRepository interface {
	Create(ctx context.Context, trader *models.Trader) error
	GetByID(ctx context.Context, id uuid.UUID) (*models.Trader, error)
	GetByWalletAddress(ctx context.Context, walletAddress string) (*models.Trader, error)
	List(ctx context.Context, limit, offset int) ([]*models.Trader, error)
	Update(ctx context.Context, trader *models.Trader) error
	Delete(ctx context.Context, id uuid.UUID) error
	GetTopTraders(ctx context.Context, orderBy string, limit int) ([]*models.Trader, error) // orderBy: win_rate, total_pnl, reputation
	GetTrackedTraders(ctx context.Context, limit, offset int) ([]*models.Trader, error)
	UpdateLastActive(ctx context.Context, walletAddress string) error
	
	// Following methods
	FollowWallet(ctx context.Context, followerAddress, followingAddress string) error
	UnfollowWallet(ctx context.Context, followerAddress, followingAddress string) error
	GetFollowing(ctx context.Context, followerAddress string, limit, offset int) ([]*models.WalletFollowing, error)
	GetFollowers(ctx context.Context, followingAddress string, limit, offset int) ([]*models.WalletFollowing, error)
	IsFollowing(ctx context.Context, followerAddress, followingAddress string) (bool, error)
}