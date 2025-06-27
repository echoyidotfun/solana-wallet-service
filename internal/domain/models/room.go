package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// TradeRoom represents a trading room
type TradeRoom struct {
	ID           uuid.UUID    `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	RoomID       string       `gorm:"uniqueIndex;not null;size:20" json:"room_id"`
	CreatorAddress string     `gorm:"size:64;not null" json:"creator_address"`
	TokenID      *uuid.UUID   `gorm:"type:uuid" json:"token_id"`
	Token        *Token       `gorm:"foreignKey:TokenID;references:ID" json:"token,omitempty"`
	TokenAddress *string      `gorm:"size:64" json:"token_address"`
	Password     *string      `gorm:"size:255" json:"password,omitempty"`
	RecycleHours int          `gorm:"not null;default:24" json:"recycle_hours"`
	Status       RoomStatus   `gorm:"type:varchar(20);not null;default:'active'" json:"status"`
	MaxMembers   int          `gorm:"not null;default:100" json:"max_members"`
	CurrentMembers int        `gorm:"not null;default:1" json:"current_members"`
	LastActivity time.Time    `json:"last_activity"`
	ExpiresAt    time.Time    `json:"expires_at"`
	CreatedAt    time.Time    `json:"created_at"`
	UpdatedAt    time.Time    `json:"updated_at"`
	
	// Relationships
	Members      []RoomMember `gorm:"foreignKey:RoomID;references:ID" json:"members,omitempty"`
	SharedInfos  []SharedInfo `gorm:"foreignKey:RoomID;references:ID" json:"shared_infos,omitempty"`
}

// RoomStatus represents the status of a room
type RoomStatus string

const (
	RoomStatusActive   RoomStatus = "active"
	RoomStatusClosed   RoomStatus = "closed"
	RoomStatusExpired  RoomStatus = "expired"
)

// RoomMember represents a member in a trading room
type RoomMember struct {
	ID            uuid.UUID  `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	RoomID        uuid.UUID  `gorm:"type:uuid;not null" json:"room_id"`
	Room          TradeRoom  `gorm:"foreignKey:RoomID;references:ID" json:"room"`
	WalletAddress string     `gorm:"size:64;not null" json:"wallet_address"`
	JoinedAt      time.Time  `json:"joined_at"`
	LastSeen      time.Time  `json:"last_seen"`
	IsOnline      bool       `gorm:"default:false" json:"is_online"`
	Role          MemberRole `gorm:"type:varchar(20);not null;default:'member'" json:"role"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
}

// MemberRole represents the role of a member in a room
type MemberRole string

const (
	MemberRoleCreator MemberRole = "creator"
	MemberRoleMember  MemberRole = "member"
)

// SharedInfo represents shared information in a room
type SharedInfo struct {
	ID          uuid.UUID       `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	RoomID      uuid.UUID       `gorm:"type:uuid;not null" json:"room_id"`
	Room        TradeRoom       `gorm:"foreignKey:RoomID;references:ID" json:"room"`
	SharerAddress string        `gorm:"size:64;not null" json:"sharer_address"`
	Type        SharedInfoType  `gorm:"type:varchar(50);not null" json:"type"`
	Title       string          `gorm:"size:255;not null" json:"title"`
	Content     string          `gorm:"type:text;not null" json:"content"`
	Metadata    string          `gorm:"type:jsonb" json:"metadata"` // JSON metadata
	IsSticky    bool            `gorm:"default:false" json:"is_sticky"`
	ViewCount   int             `gorm:"default:0" json:"view_count"`
	LikeCount   int             `gorm:"default:0" json:"like_count"`
	CreatedAt   time.Time       `json:"created_at"`
	UpdatedAt   time.Time       `json:"updated_at"`
}

// SharedInfoType represents the type of shared information
type SharedInfoType string

const (
	SharedInfoTypeAnalysis    SharedInfoType = "analysis"
	SharedInfoTypeSignal      SharedInfoType = "signal"
	SharedInfoTypeNews        SharedInfoType = "news"
	SharedInfoTypeDiscussion  SharedInfoType = "discussion"
	SharedInfoTypeAlert       SharedInfoType = "alert"
)

// TradeEvent represents trading events in a room
type TradeEvent struct {
	ID            uuid.UUID   `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	RoomID        uuid.UUID   `gorm:"type:uuid;not null" json:"room_id"`
	Room          TradeRoom   `gorm:"foreignKey:RoomID;references:ID" json:"room"`
	WalletAddress string      `gorm:"size:64;not null" json:"wallet_address"`
	TokenAddress  string      `gorm:"size:64;not null" json:"token_address"`
	EventType     TradeEventType `gorm:"type:varchar(20);not null" json:"event_type"`
	Amount        float64     `gorm:"type:decimal(20,8)" json:"amount"`
	Price         float64     `gorm:"type:decimal(20,10)" json:"price"`
	ValueUSD      float64     `gorm:"type:decimal(20,4)" json:"value_usd"`
	TxSignature   string      `gorm:"size:128" json:"tx_signature"`
	BlockTime     time.Time   `json:"block_time"`
	CreatedAt     time.Time   `json:"created_at"`
}

// TradeEventType represents the type of trading event
type TradeEventType string

const (
	TradeEventTypeBuy  TradeEventType = "buy"
	TradeEventTypeSell TradeEventType = "sell"
)

// BeforeCreate hooks
func (tr *TradeRoom) BeforeCreate(tx *gorm.DB) error {
	if tr.ID == uuid.Nil {
		tr.ID = uuid.New()
	}
	if tr.RoomID == "" {
		tr.RoomID = generateRoomID()
	}
	tr.LastActivity = time.Now()
	tr.ExpiresAt = time.Now().Add(time.Duration(tr.RecycleHours) * time.Hour)
	return nil
}

func (rm *RoomMember) BeforeCreate(tx *gorm.DB) error {
	if rm.ID == uuid.Nil {
		rm.ID = uuid.New()
	}
	rm.JoinedAt = time.Now()
	rm.LastSeen = time.Now()
	return nil
}

func (si *SharedInfo) BeforeCreate(tx *gorm.DB) error {
	if si.ID == uuid.Nil {
		si.ID = uuid.New()
	}
	return nil
}

func (te *TradeEvent) BeforeCreate(tx *gorm.DB) error {
	if te.ID == uuid.Nil {
		te.ID = uuid.New()
	}
	return nil
}

// generateRoomID generates a unique room ID
func generateRoomID() string {
	// Simple room ID generation - in production, use more sophisticated method
	return uuid.New().String()[:8]
}