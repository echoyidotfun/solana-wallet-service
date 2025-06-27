package repositories

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/wallet/service/internal/domain/models"
	"gorm.io/gorm"
)

type traderRepository struct {
	db *gorm.DB
}

// NewTraderRepository creates a new trader repository instance
func NewTraderRepository(db *gorm.DB) TraderRepository {
	return &traderRepository{db: db}
}

// Trader methods
func (r *traderRepository) Create(ctx context.Context, trader *models.Trader) error {
	return r.db.WithContext(ctx).Create(trader).Error
}

func (r *traderRepository) GetByID(ctx context.Context, id uuid.UUID) (*models.Trader, error) {
	var trader models.Trader
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&trader).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &trader, nil
}

func (r *traderRepository) GetByWalletAddress(ctx context.Context, walletAddress string) (*models.Trader, error) {
	var trader models.Trader
	err := r.db.WithContext(ctx).Where("wallet_address = ?", walletAddress).First(&trader).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &trader, nil
}

func (r *traderRepository) List(ctx context.Context, limit, offset int) ([]*models.Trader, error) {
	var traders []*models.Trader
	err := r.db.WithContext(ctx).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&traders).Error
	return traders, err
}

func (r *traderRepository) Update(ctx context.Context, trader *models.Trader) error {
	return r.db.WithContext(ctx).Save(trader).Error
}

func (r *traderRepository) Delete(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.Trader{}, id).Error
}

func (r *traderRepository) GetTopTraders(ctx context.Context, orderBy string, limit int) ([]*models.Trader, error) {
	var traders []*models.Trader
	var orderClause string
	
	switch orderBy {
	case "win_rate":
		orderClause = "win_rate DESC"
	case "total_pnl":
		orderClause = "total_pnl DESC"
	case "reputation":
		orderClause = "reputation DESC"
	default:
		orderClause = "reputation DESC"
	}
	
	err := r.db.WithContext(ctx).
		Where("is_verified = true").
		Order(orderClause).
		Limit(limit).
		Find(&traders).Error
	return traders, err
}

func (r *traderRepository) GetTrackedTraders(ctx context.Context, limit, offset int) ([]*models.Trader, error) {
	var traders []*models.Trader
	err := r.db.WithContext(ctx).
		Where("is_tracked = true").
		Order("last_active_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&traders).Error
	return traders, err
}

func (r *traderRepository) UpdateLastActive(ctx context.Context, walletAddress string) error {
	return r.db.WithContext(ctx).
		Model(&models.Trader{}).
		Where("wallet_address = ?", walletAddress).
		Update("last_active_at", time.Now()).Error
}

// Following methods
func (r *traderRepository) FollowWallet(ctx context.Context, followerAddress, followingAddress string) error {
	following := &models.WalletFollowing{
		FollowerAddress:  followerAddress,
		FollowingAddress: followingAddress,
	}
	
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// Create following relationship
		if err := tx.Create(following).Error; err != nil {
			return err
		}
		
		// Update follower count for the trader being followed
		return tx.Model(&models.Trader{}).
			Where("wallet_address = ?", followingAddress).
			Update("follower_count", gorm.Expr("follower_count + 1")).Error
	})
}

func (r *traderRepository) UnfollowWallet(ctx context.Context, followerAddress, followingAddress string) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// Delete following relationship
		result := tx.Where("follower_address = ? AND following_address = ?", 
			followerAddress, followingAddress).Delete(&models.WalletFollowing{})
		if result.Error != nil {
			return result.Error
		}
		
		// Update follower count only if following was deleted
		if result.RowsAffected > 0 {
			return tx.Model(&models.Trader{}).
				Where("wallet_address = ?", followingAddress).
				Update("follower_count", gorm.Expr("follower_count - 1")).Error
		}
		
		return nil
	})
}

func (r *traderRepository) GetFollowing(ctx context.Context, followerAddress string, limit, offset int) ([]*models.WalletFollowing, error) {
	var followings []*models.WalletFollowing
	err := r.db.WithContext(ctx).
		Where("follower_address = ?", followerAddress).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&followings).Error
	return followings, err
}

func (r *traderRepository) GetFollowers(ctx context.Context, followingAddress string, limit, offset int) ([]*models.WalletFollowing, error) {
	var followers []*models.WalletFollowing
	err := r.db.WithContext(ctx).
		Where("following_address = ?", followingAddress).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&followers).Error
	return followers, err
}

func (r *traderRepository) IsFollowing(ctx context.Context, followerAddress, followingAddress string) (bool, error) {
	var count int64
	err := r.db.WithContext(ctx).
		Model(&models.WalletFollowing{}).
		Where("follower_address = ? AND following_address = ?", followerAddress, followingAddress).
		Count(&count).Error
	return count > 0, err
}