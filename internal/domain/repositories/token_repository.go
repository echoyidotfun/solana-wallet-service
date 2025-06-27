package repositories

import (
	"context"
	"errors"

	"github.com/google/uuid"
	"github.com/wallet/service/internal/domain/models"
	"gorm.io/gorm"
)

type tokenRepository struct {
	db *gorm.DB
}

// NewTokenRepository creates a new token repository instance
func NewTokenRepository(db *gorm.DB) TokenRepository {
	return &tokenRepository{db: db}
}

// Token methods
func (r *tokenRepository) Create(ctx context.Context, token *models.Token) error {
	return r.db.WithContext(ctx).Create(token).Error
}

func (r *tokenRepository) GetByID(ctx context.Context, id uuid.UUID) (*models.Token, error) {
	var token models.Token
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&token).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &token, nil
}

func (r *tokenRepository) GetByMintAddress(ctx context.Context, mintAddress string) (*models.Token, error) {
	var token models.Token
	err := r.db.WithContext(ctx).Where("mint_address = ?", mintAddress).First(&token).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &token, nil
}

func (r *tokenRepository) List(ctx context.Context, limit, offset int) ([]*models.Token, error) {
	var tokens []*models.Token
	err := r.db.WithContext(ctx).
		Limit(limit).
		Offset(offset).
		Order("created_at DESC").
		Find(&tokens).Error
	return tokens, err
}

func (r *tokenRepository) Update(ctx context.Context, token *models.Token) error {
	return r.db.WithContext(ctx).Save(token).Error
}

func (r *tokenRepository) Delete(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.Token{}, id).Error
}

// Market data methods
func (r *tokenRepository) CreateMarketData(ctx context.Context, data *models.TokenMarketData) error {
	return r.db.WithContext(ctx).Create(data).Error
}

func (r *tokenRepository) GetLatestMarketData(ctx context.Context, tokenID uuid.UUID) (*models.TokenMarketData, error) {
	var data models.TokenMarketData
	err := r.db.WithContext(ctx).
		Where("token_id = ?", tokenID).
		Order("created_at DESC").
		First(&data).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &data, nil
}

func (r *tokenRepository) UpdateMarketData(ctx context.Context, data *models.TokenMarketData) error {
	return r.db.WithContext(ctx).Save(data).Error
}

// Trending methods
func (r *tokenRepository) CreateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error {
	return r.db.WithContext(ctx).Create(ranking).Error
}

func (r *tokenRepository) GetTrendingTokens(ctx context.Context, category, timeframe string, limit int) ([]*models.TokenTrendingRanking, error) {
	var rankings []*models.TokenTrendingRanking
	query := r.db.WithContext(ctx).
		Preload("Token").
		Where("category = ? AND timeframe = ?", category, timeframe).
		Order("rank ASC").
		Limit(limit)
	
	err := query.Find(&rankings).Error
	return rankings, err
}

func (r *tokenRepository) UpdateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error {
	return r.db.WithContext(ctx).Save(ranking).Error
}

// Top holders methods
func (r *tokenRepository) CreateTopHolder(ctx context.Context, holder *models.TokenTopHolders) error {
	return r.db.WithContext(ctx).Create(holder).Error
}

func (r *tokenRepository) GetTopHolders(ctx context.Context, tokenID uuid.UUID, limit int) ([]*models.TokenTopHolders, error) {
	var holders []*models.TokenTopHolders
	err := r.db.WithContext(ctx).
		Where("token_id = ?", tokenID).
		Order("rank ASC").
		Limit(limit).
		Find(&holders).Error
	return holders, err
}

func (r *tokenRepository) UpdateTopHolder(ctx context.Context, holder *models.TokenTopHolders) error {
	return r.db.WithContext(ctx).Save(holder).Error
}

// Transaction stats methods
func (r *tokenRepository) CreateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error {
	return r.db.WithContext(ctx).Create(stats).Error
}

func (r *tokenRepository) GetTransactionStats(ctx context.Context, tokenID uuid.UUID, timeframe string) (*models.TokenTransactionStats, error) {
	var stats models.TokenTransactionStats
	err := r.db.WithContext(ctx).
		Where("token_id = ? AND timeframe = ?", tokenID, timeframe).
		First(&stats).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &stats, nil
}

func (r *tokenRepository) UpdateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error {
	return r.db.WithContext(ctx).Save(stats).Error
}