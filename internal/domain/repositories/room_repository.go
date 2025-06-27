package repositories

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/wallet/service/internal/domain/models"
	"gorm.io/gorm"
)

type roomRepository struct {
	db *gorm.DB
}

// NewRoomRepository creates a new room repository instance
func NewRoomRepository(db *gorm.DB) RoomRepository {
	return &roomRepository{db: db}
}

// Room methods
func (r *roomRepository) Create(ctx context.Context, room *models.TradeRoom) error {
	return r.db.WithContext(ctx).Create(room).Error
}

func (r *roomRepository) GetByID(ctx context.Context, id uuid.UUID) (*models.TradeRoom, error) {
	var room models.TradeRoom
	err := r.db.WithContext(ctx).
		Preload("Token").
		Preload("Members").
		Where("id = ?", id).
		First(&room).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &room, nil
}

func (r *roomRepository) GetByRoomID(ctx context.Context, roomID string) (*models.TradeRoom, error) {
	var room models.TradeRoom
	err := r.db.WithContext(ctx).
		Preload("Token").
		Preload("Members").
		Where("room_id = ?", roomID).
		First(&room).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &room, nil
}

func (r *roomRepository) GetByCreator(ctx context.Context, creatorAddress string, limit, offset int) ([]*models.TradeRoom, error) {
	var rooms []*models.TradeRoom
	err := r.db.WithContext(ctx).
		Preload("Token").
		Where("creator_address = ?", creatorAddress).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&rooms).Error
	return rooms, err
}

func (r *roomRepository) List(ctx context.Context, status models.RoomStatus, limit, offset int) ([]*models.TradeRoom, error) {
	var rooms []*models.TradeRoom
	query := r.db.WithContext(ctx).
		Preload("Token").
		Order("created_at DESC").
		Limit(limit).
		Offset(offset)
	
	if status != "" {
		query = query.Where("status = ?", status)
	}
	
	err := query.Find(&rooms).Error
	return rooms, err
}

func (r *roomRepository) Update(ctx context.Context, room *models.TradeRoom) error {
	return r.db.WithContext(ctx).Save(room).Error
}

func (r *roomRepository) Delete(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.TradeRoom{}, id).Error
}

func (r *roomRepository) UpdateLastActivity(ctx context.Context, roomID uuid.UUID) error {
	return r.db.WithContext(ctx).
		Model(&models.TradeRoom{}).
		Where("id = ?", roomID).
		Update("last_activity", time.Now()).Error
}

func (r *roomRepository) GetExpiredRooms(ctx context.Context) ([]*models.TradeRoom, error) {
	var rooms []*models.TradeRoom
	err := r.db.WithContext(ctx).
		Where("expires_at < ? AND status = 'active'", time.Now()).
		Find(&rooms).Error
	return rooms, err
}

// Member methods
func (r *roomRepository) AddMember(ctx context.Context, member *models.RoomMember) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// Create member
		if err := tx.Create(member).Error; err != nil {
			return err
		}
		
		// Update room member count
		return tx.Model(&models.TradeRoom{}).
			Where("id = ?", member.RoomID).
			Update("current_members", gorm.Expr("current_members + 1")).Error
	})
}

func (r *roomRepository) RemoveMember(ctx context.Context, roomID uuid.UUID, walletAddress string) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// Delete member
		result := tx.Where("room_id = ? AND wallet_address = ?", roomID, walletAddress).
			Delete(&models.RoomMember{})
		if result.Error != nil {
			return result.Error
		}
		
		// Update room member count only if member was deleted
		if result.RowsAffected > 0 {
			return tx.Model(&models.TradeRoom{}).
				Where("id = ?", roomID).
				Update("current_members", gorm.Expr("current_members - 1")).Error
		}
		
		return nil
	})
}

func (r *roomRepository) GetMembers(ctx context.Context, roomID uuid.UUID) ([]*models.RoomMember, error) {
	var members []*models.RoomMember
	err := r.db.WithContext(ctx).
		Where("room_id = ?", roomID).
		Order("joined_at ASC").
		Find(&members).Error
	return members, err
}

func (r *roomRepository) GetMemberByAddress(ctx context.Context, roomID uuid.UUID, walletAddress string) (*models.RoomMember, error) {
	var member models.RoomMember
	err := r.db.WithContext(ctx).
		Where("room_id = ? AND wallet_address = ?", roomID, walletAddress).
		First(&member).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &member, nil
}

func (r *roomRepository) UpdateMemberStatus(ctx context.Context, roomID uuid.UUID, walletAddress string, isOnline bool) error {
	return r.db.WithContext(ctx).
		Model(&models.RoomMember{}).
		Where("room_id = ? AND wallet_address = ?", roomID, walletAddress).
		Update("is_online", isOnline).Error
}

func (r *roomRepository) UpdateMemberLastSeen(ctx context.Context, roomID uuid.UUID, walletAddress string) error {
	return r.db.WithContext(ctx).
		Model(&models.RoomMember{}).
		Where("room_id = ? AND wallet_address = ?", roomID, walletAddress).
		Updates(map[string]interface{}{
			"last_seen": time.Now(),
			"is_online": true,
		}).Error
}

// Shared info methods
func (r *roomRepository) CreateSharedInfo(ctx context.Context, info *models.SharedInfo) error {
	return r.db.WithContext(ctx).Create(info).Error
}

func (r *roomRepository) GetSharedInfos(ctx context.Context, roomID uuid.UUID, limit, offset int) ([]*models.SharedInfo, error) {
	var infos []*models.SharedInfo
	err := r.db.WithContext(ctx).
		Where("room_id = ?", roomID).
		Order("is_sticky DESC, created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&infos).Error
	return infos, err
}

func (r *roomRepository) GetSharedInfoByID(ctx context.Context, id uuid.UUID) (*models.SharedInfo, error) {
	var info models.SharedInfo
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&info).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &info, nil
}

func (r *roomRepository) UpdateSharedInfo(ctx context.Context, info *models.SharedInfo) error {
	return r.db.WithContext(ctx).Save(info).Error
}

func (r *roomRepository) DeleteSharedInfo(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.SharedInfo{}, id).Error
}

func (r *roomRepository) IncrementViewCount(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).
		Model(&models.SharedInfo{}).
		Where("id = ?", id).
		Update("view_count", gorm.Expr("view_count + 1")).Error
}

func (r *roomRepository) IncrementLikeCount(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).
		Model(&models.SharedInfo{}).
		Where("id = ?", id).
		Update("like_count", gorm.Expr("like_count + 1")).Error
}

// Trade event methods
func (r *roomRepository) CreateTradeEvent(ctx context.Context, event *models.TradeEvent) error {
	return r.db.WithContext(ctx).Create(event).Error
}

func (r *roomRepository) GetTradeEvents(ctx context.Context, roomID uuid.UUID, limit, offset int) ([]*models.TradeEvent, error) {
	var events []*models.TradeEvent
	err := r.db.WithContext(ctx).
		Where("room_id = ?", roomID).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&events).Error
	return events, err
}

func (r *roomRepository) GetTradeEventsByWallet(ctx context.Context, walletAddress string, limit, offset int) ([]*models.TradeEvent, error) {
	var events []*models.TradeEvent
	err := r.db.WithContext(ctx).
		Where("wallet_address = ?", walletAddress).
		Order("created_at DESC").
		Limit(limit).
		Offset(offset).
		Find(&events).Error
	return events, err
}