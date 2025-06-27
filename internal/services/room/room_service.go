package room

import (
	"context"
	"crypto/md5"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/domain/models"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
)

var (
	ErrRoomNotFound        = errors.New("room not found")
	ErrRoomFull           = errors.New("room is full")
	ErrRoomClosed         = errors.New("room is closed")
	ErrRoomExpired        = errors.New("room is expired")
	ErrInvalidPassword    = errors.New("invalid room password")
	ErrAlreadyMember      = errors.New("already a member of this room")
	ErrNotMember          = errors.New("not a member of this room")
	ErrInsufficientPermission = errors.New("insufficient permission")
)

// RoomService defines the interface for room management
type RoomService interface {
	// Room operations
	CreateRoom(ctx context.Context, req *CreateRoomRequest) (*models.TradeRoom, error)
	GetRoom(ctx context.Context, roomID string) (*models.TradeRoom, error)
	GetRoomByID(ctx context.Context, id uuid.UUID) (*models.TradeRoom, error)
	ListRooms(ctx context.Context, status models.RoomStatus, limit, offset int) ([]*models.TradeRoom, error)
	GetUserRooms(ctx context.Context, creatorAddress string, limit, offset int) ([]*models.TradeRoom, error)
	UpdateRoom(ctx context.Context, roomID string, req *UpdateRoomRequest) (*models.TradeRoom, error)
	CloseRoom(ctx context.Context, roomID, creatorAddress string) error
	DeleteRoom(ctx context.Context, roomID, creatorAddress string) error
	
	// Member operations
	JoinRoom(ctx context.Context, roomID, walletAddress, password string) (*models.RoomMember, error)
	LeaveRoom(ctx context.Context, roomID, walletAddress string) error
	GetRoomMembers(ctx context.Context, roomID string) ([]*models.RoomMember, error)
	UpdateMemberStatus(ctx context.Context, roomID, walletAddress string, isOnline bool) error
	KickMember(ctx context.Context, roomID, creatorAddress, targetAddress string) error
	
	// Content operations
	ShareInfo(ctx context.Context, req *ShareInfoRequest) (*models.SharedInfo, error)
	GetSharedInfos(ctx context.Context, roomID string, limit, offset int) ([]*models.SharedInfo, error)
	UpdateSharedInfo(ctx context.Context, infoID uuid.UUID, req *UpdateSharedInfoRequest) (*models.SharedInfo, error)
	DeleteSharedInfo(ctx context.Context, infoID uuid.UUID, sharerAddress string) error
	LikeSharedInfo(ctx context.Context, infoID uuid.UUID) error
	ViewSharedInfo(ctx context.Context, infoID uuid.UUID) error
	
	// Trade event operations
	RecordTradeEvent(ctx context.Context, req *TradeEventRequest) (*models.TradeEvent, error)
	GetTradeEvents(ctx context.Context, roomID string, limit, offset int) ([]*models.TradeEvent, error)
	
	// Maintenance operations
	CleanupExpiredRooms(ctx context.Context) error
	UpdateRoomActivity(ctx context.Context, roomID string) error
}

type roomService struct {
	roomRepo repositories.RoomRepository
	logger   *logrus.Logger
}

// NewRoomService creates a new room service instance
func NewRoomService(roomRepo repositories.RoomRepository, logger *logrus.Logger) RoomService {
	return &roomService{
		roomRepo: roomRepo,
		logger:   logger,
	}
}

// Request/Response structs
type CreateRoomRequest struct {
	CreatorAddress string    `json:"creator_address" validate:"required"`
	TokenID        *uuid.UUID `json:"token_id,omitempty"`
	TokenAddress   *string   `json:"token_address,omitempty"`
	Password       *string   `json:"password,omitempty"`
	RecycleHours   int       `json:"recycle_hours" validate:"min=1,max=168"` // max 7 days
	MaxMembers     int       `json:"max_members" validate:"min=2,max=1000"`
}

type UpdateRoomRequest struct {
	Password     *string `json:"password,omitempty"`
	RecycleHours *int    `json:"recycle_hours,omitempty" validate:"omitempty,min=1,max=168"`
	MaxMembers   *int    `json:"max_members,omitempty" validate:"omitempty,min=2,max=1000"`
}

type ShareInfoRequest struct {
	RoomID        string                 `json:"room_id" validate:"required"`
	SharerAddress string                 `json:"sharer_address" validate:"required"`
	Type          models.SharedInfoType  `json:"type" validate:"required"`
	Title         string                 `json:"title" validate:"required,max=255"`
	Content       string                 `json:"content" validate:"required"`
	Metadata      map[string]interface{} `json:"metadata,omitempty"`
	IsSticky      bool                   `json:"is_sticky"`
}

type UpdateSharedInfoRequest struct {
	Title    *string                `json:"title,omitempty" validate:"omitempty,max=255"`
	Content  *string                `json:"content,omitempty"`
	Metadata map[string]interface{} `json:"metadata,omitempty"`
	IsSticky *bool                  `json:"is_sticky,omitempty"`
}

type TradeEventRequest struct {
	RoomID        string                 `json:"room_id" validate:"required"`
	WalletAddress string                 `json:"wallet_address" validate:"required"`
	TokenAddress  string                 `json:"token_address" validate:"required"`
	EventType     models.TradeEventType  `json:"event_type" validate:"required"`
	Amount        float64                `json:"amount" validate:"required,min=0"`
	Price         float64                `json:"price" validate:"required,min=0"`
	ValueUSD      float64                `json:"value_usd" validate:"required,min=0"`
	TxSignature   string                 `json:"tx_signature" validate:"required"`
	BlockTime     time.Time              `json:"block_time" validate:"required"`
}

// Room operations
func (s *roomService) CreateRoom(ctx context.Context, req *CreateRoomRequest) (*models.TradeRoom, error) {
	// Set defaults
	if req.RecycleHours == 0 {
		req.RecycleHours = 24
	}
	if req.MaxMembers == 0 {
		req.MaxMembers = 100
	}
	
	// Hash password if provided
	var hashedPassword *string
	if req.Password != nil && *req.Password != "" {
		hash := fmt.Sprintf("%x", md5.Sum([]byte(*req.Password)))
		hashedPassword = &hash
	}
	
	room := &models.TradeRoom{
		CreatorAddress: req.CreatorAddress,
		TokenID:        req.TokenID,
		TokenAddress:   req.TokenAddress,
		Password:       hashedPassword,
		RecycleHours:   req.RecycleHours,
		MaxMembers:     req.MaxMembers,
		Status:         models.RoomStatusActive,
		CurrentMembers: 1,
	}
	
	if err := s.roomRepo.Create(ctx, room); err != nil {
		s.logger.WithFields(logrus.Fields{"error": err}).Error("Failed to create room")
		return nil, err
	}
	
	// Add creator as member
	member := &models.RoomMember{
		RoomID:        room.ID,
		WalletAddress: req.CreatorAddress,
		Role:          models.MemberRoleCreator,
		IsOnline:      true,
	}
	
	if err := s.roomRepo.AddMember(ctx, member); err != nil {
		s.logger.WithFields(logrus.Fields{"error": err, "room_id": room.RoomID}).Error("Failed to add creator as member")
		return nil, err
	}
	
	s.logger.WithFields(logrus.Fields{"room_id": room.RoomID, "creator": req.CreatorAddress}).Info("Room created successfully")
	return room, nil
}

func (s *roomService) GetRoom(ctx context.Context, roomID string) (*models.TradeRoom, error) {
	room, err := s.roomRepo.GetByRoomID(ctx, roomID)
	if err != nil {
		return nil, err
	}
	if room == nil {
		return nil, ErrRoomNotFound
	}
	
	// Check if room is expired
	if room.Status == models.RoomStatusActive && time.Now().After(room.ExpiresAt) {
		room.Status = models.RoomStatusExpired
		if updateErr := s.roomRepo.Update(ctx, room); updateErr != nil {
			s.logger.WithFields(logrus.Fields{"error": updateErr, "room_id": roomID}).Error("Failed to update expired room status")
		}
		return nil, ErrRoomExpired
	}
	
	return room, nil
}

func (s *roomService) GetRoomByID(ctx context.Context, id uuid.UUID) (*models.TradeRoom, error) {
	return s.roomRepo.GetByID(ctx, id)
}

func (s *roomService) ListRooms(ctx context.Context, status models.RoomStatus, limit, offset int) ([]*models.TradeRoom, error) {
	return s.roomRepo.List(ctx, status, limit, offset)
}

func (s *roomService) GetUserRooms(ctx context.Context, creatorAddress string, limit, offset int) ([]*models.TradeRoom, error) {
	return s.roomRepo.GetByCreator(ctx, creatorAddress, limit, offset)
}

func (s *roomService) UpdateRoom(ctx context.Context, roomID string, req *UpdateRoomRequest) (*models.TradeRoom, error) {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	
	if room.Status != models.RoomStatusActive {
		return nil, ErrRoomClosed
	}
	
	// Update fields
	if req.Password != nil {
		if *req.Password == "" {
			room.Password = nil
		} else {
			hash := fmt.Sprintf("%x", md5.Sum([]byte(*req.Password)))
			room.Password = &hash
		}
	}
	
	if req.RecycleHours != nil {
		room.RecycleHours = *req.RecycleHours
		room.ExpiresAt = time.Now().Add(time.Duration(*req.RecycleHours) * time.Hour)
	}
	
	if req.MaxMembers != nil {
		if *req.MaxMembers < room.CurrentMembers {
			return nil, fmt.Errorf("max members cannot be less than current members (%d)", room.CurrentMembers)
		}
		room.MaxMembers = *req.MaxMembers
	}
	
	if err := s.roomRepo.Update(ctx, room); err != nil {
		return nil, err
	}
	
	return room, nil
}

func (s *roomService) CloseRoom(ctx context.Context, roomID, creatorAddress string) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	if room.CreatorAddress != creatorAddress {
		return ErrInsufficientPermission
	}
	
	room.Status = models.RoomStatusClosed
	return s.roomRepo.Update(ctx, room)
}

func (s *roomService) DeleteRoom(ctx context.Context, roomID, creatorAddress string) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	if room.CreatorAddress != creatorAddress {
		return ErrInsufficientPermission
	}
	
	return s.roomRepo.Delete(ctx, room.ID)
}

// Member operations
func (s *roomService) JoinRoom(ctx context.Context, roomID, walletAddress, password string) (*models.RoomMember, error) {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	
	if room.Status != models.RoomStatusActive {
		return nil, ErrRoomClosed
	}
	
	if room.CurrentMembers >= room.MaxMembers {
		return nil, ErrRoomFull
	}
	
	// Check password
	if room.Password != nil {
		if password == "" {
			return nil, ErrInvalidPassword
		}
		hashedPassword := fmt.Sprintf("%x", md5.Sum([]byte(password)))
		if hashedPassword != *room.Password {
			return nil, ErrInvalidPassword
		}
	}
	
	// Check if already a member
	existingMember, err := s.roomRepo.GetMemberByAddress(ctx, room.ID, walletAddress)
	if err != nil {
		return nil, err
	}
	if existingMember != nil {
		return nil, ErrAlreadyMember
	}
	
	member := &models.RoomMember{
		RoomID:        room.ID,
		WalletAddress: walletAddress,
		Role:          models.MemberRoleMember,
		IsOnline:      true,
	}
	
	if err := s.roomRepo.AddMember(ctx, member); err != nil {
		return nil, err
	}
	
	// Update room activity
	s.roomRepo.UpdateLastActivity(ctx, room.ID)
	
	s.logger.WithFields(logrus.Fields{"room_id": roomID, "wallet": walletAddress}).Info("User joined room")
	return member, nil
}

func (s *roomService) LeaveRoom(ctx context.Context, roomID, walletAddress string) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	// Check if member exists
	member, err := s.roomRepo.GetMemberByAddress(ctx, room.ID, walletAddress)
	if err != nil {
		return err
	}
	if member == nil {
		return ErrNotMember
	}
	
	// Creator cannot leave their own room
	if member.Role == models.MemberRoleCreator {
		return ErrInsufficientPermission
	}
	
	if err := s.roomRepo.RemoveMember(ctx, room.ID, walletAddress); err != nil {
		return err
	}
	
	s.logger.WithFields(logrus.Fields{"room_id": roomID, "wallet": walletAddress}).Info("User left room")
	return nil
}

func (s *roomService) GetRoomMembers(ctx context.Context, roomID string) ([]*models.RoomMember, error) {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	
	return s.roomRepo.GetMembers(ctx, room.ID)
}

func (s *roomService) UpdateMemberStatus(ctx context.Context, roomID, walletAddress string, isOnline bool) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	if isOnline {
		return s.roomRepo.UpdateMemberLastSeen(ctx, room.ID, walletAddress)
	}
	return s.roomRepo.UpdateMemberStatus(ctx, room.ID, walletAddress, isOnline)
}

func (s *roomService) KickMember(ctx context.Context, roomID, creatorAddress, targetAddress string) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	if room.CreatorAddress != creatorAddress {
		return ErrInsufficientPermission
	}
	
	// Cannot kick the creator
	if targetAddress == creatorAddress {
		return ErrInsufficientPermission
	}
	
	return s.roomRepo.RemoveMember(ctx, room.ID, targetAddress)
}

// Content operations
func (s *roomService) ShareInfo(ctx context.Context, req *ShareInfoRequest) (*models.SharedInfo, error) {
	room, err := s.GetRoom(ctx, req.RoomID)
	if err != nil {
		return nil, err
	}
	
	// Check if user is a member
	member, err := s.roomRepo.GetMemberByAddress(ctx, room.ID, req.SharerAddress)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, ErrNotMember
	}
	
	// Convert metadata to JSON string
	var metadataStr string
	if req.Metadata != nil {
		metadataBytes, _ := json.Marshal(req.Metadata)
		metadataStr = string(metadataBytes)
	}
	
	info := &models.SharedInfo{
		RoomID:        room.ID,
		SharerAddress: req.SharerAddress,
		Type:          req.Type,
		Title:         req.Title,
		Content:       req.Content,
		Metadata:      metadataStr,
		IsSticky:      req.IsSticky,
	}
	
	if err := s.roomRepo.CreateSharedInfo(ctx, info); err != nil {
		return nil, err
	}
	
	// Update room activity
	s.roomRepo.UpdateLastActivity(ctx, room.ID)
	
	return info, nil
}

func (s *roomService) GetSharedInfos(ctx context.Context, roomID string, limit, offset int) ([]*models.SharedInfo, error) {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	
	return s.roomRepo.GetSharedInfos(ctx, room.ID, limit, offset)
}

func (s *roomService) UpdateSharedInfo(ctx context.Context, infoID uuid.UUID, req *UpdateSharedInfoRequest) (*models.SharedInfo, error) {
	info, err := s.roomRepo.GetSharedInfoByID(ctx, infoID)
	if err != nil {
		return nil, err
	}
	if info == nil {
		return nil, errors.New("shared info not found")
	}
	
	// Update fields
	if req.Title != nil {
		info.Title = *req.Title
	}
	if req.Content != nil {
		info.Content = *req.Content
	}
	if req.Metadata != nil {
		metadataBytes, _ := json.Marshal(req.Metadata)
		info.Metadata = string(metadataBytes)
	}
	if req.IsSticky != nil {
		info.IsSticky = *req.IsSticky
	}
	
	if err := s.roomRepo.UpdateSharedInfo(ctx, info); err != nil {
		return nil, err
	}
	
	return info, nil
}

func (s *roomService) DeleteSharedInfo(ctx context.Context, infoID uuid.UUID, sharerAddress string) error {
	info, err := s.roomRepo.GetSharedInfoByID(ctx, infoID)
	if err != nil {
		return err
	}
	if info == nil {
		return errors.New("shared info not found")
	}
	
	// Check permission
	if info.SharerAddress != sharerAddress {
		// Check if user is room creator
		room, err := s.roomRepo.GetByID(ctx, info.RoomID)
		if err != nil {
			return err
		}
		if room.CreatorAddress != sharerAddress {
			return ErrInsufficientPermission
		}
	}
	
	return s.roomRepo.DeleteSharedInfo(ctx, infoID)
}

func (s *roomService) LikeSharedInfo(ctx context.Context, infoID uuid.UUID) error {
	return s.roomRepo.IncrementLikeCount(ctx, infoID)
}

func (s *roomService) ViewSharedInfo(ctx context.Context, infoID uuid.UUID) error {
	return s.roomRepo.IncrementViewCount(ctx, infoID)
}

// Trade event operations
func (s *roomService) RecordTradeEvent(ctx context.Context, req *TradeEventRequest) (*models.TradeEvent, error) {
	room, err := s.GetRoom(ctx, req.RoomID)
	if err != nil {
		return nil, err
	}
	
	// Check if user is a member
	member, err := s.roomRepo.GetMemberByAddress(ctx, room.ID, req.WalletAddress)
	if err != nil {
		return nil, err
	}
	if member == nil {
		return nil, ErrNotMember
	}
	
	event := &models.TradeEvent{
		RoomID:        room.ID,
		WalletAddress: req.WalletAddress,
		TokenAddress:  req.TokenAddress,
		EventType:     req.EventType,
		Amount:        req.Amount,
		Price:         req.Price,
		ValueUSD:      req.ValueUSD,
		TxSignature:   req.TxSignature,
		BlockTime:     req.BlockTime,
	}
	
	if err := s.roomRepo.CreateTradeEvent(ctx, event); err != nil {
		return nil, err
	}
	
	// Update room activity
	s.roomRepo.UpdateLastActivity(ctx, room.ID)
	
	return event, nil
}

func (s *roomService) GetTradeEvents(ctx context.Context, roomID string, limit, offset int) ([]*models.TradeEvent, error) {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return nil, err
	}
	
	return s.roomRepo.GetTradeEvents(ctx, room.ID, limit, offset)
}

// Maintenance operations
func (s *roomService) CleanupExpiredRooms(ctx context.Context) error {
	expiredRooms, err := s.roomRepo.GetExpiredRooms(ctx)
	if err != nil {
		return err
	}
	
	for _, room := range expiredRooms {
		room.Status = models.RoomStatusExpired
		if err := s.roomRepo.Update(ctx, room); err != nil {
			s.logger.WithFields(logrus.Fields{"error": err, "room_id": room.RoomID}).Error("Failed to update expired room")
			continue
		}
		s.logger.WithFields(logrus.Fields{"room_id": room.RoomID}).Info("Room expired")
	}
	
	return nil
}

func (s *roomService) UpdateRoomActivity(ctx context.Context, roomID string) error {
	room, err := s.GetRoom(ctx, roomID)
	if err != nil {
		return err
	}
	
	return s.roomRepo.UpdateLastActivity(ctx, room.ID)
}