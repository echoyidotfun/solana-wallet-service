package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// SmartMoneyTransaction represents smart money wallet transactions
type SmartMoneyTransaction struct {
	ID               uuid.UUID              `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	Signature        string                 `gorm:"uniqueIndex;not null;size:128" json:"signature"`
	Slot             int64                  `gorm:"not null" json:"slot"`
	BlockTime        time.Time              `json:"block_time"`
	WalletAddress    string                 `gorm:"size:64;not null;index" json:"wallet_address"`
	TokenAddress     string                 `gorm:"size:64;not null;index" json:"token_address"`
	TransactionType  TransactionType        `gorm:"type:varchar(20);not null" json:"transaction_type"`
	Amount           float64                `gorm:"type:decimal(20,8)" json:"amount"`
	Price            float64                `gorm:"type:decimal(20,10)" json:"price"`
	ValueUSD         float64                `gorm:"type:decimal(20,4)" json:"value_usd"`
	ProgramID        string                 `gorm:"size:64" json:"program_id"`
	InstructionType  string                 `gorm:"size:100" json:"instruction_type"`
	Status           TransactionStatus      `gorm:"type:varchar(20);not null;default:'success'" json:"status"`
	PreBalances      string                 `gorm:"type:jsonb" json:"pre_balances"`   // JSON array
	PostBalances     string                 `gorm:"type:jsonb" json:"post_balances"`  // JSON array
	PreTokenBalances string                 `gorm:"type:jsonb" json:"pre_token_balances"`  // JSON array
	PostTokenBalances string                `gorm:"type:jsonb" json:"post_token_balances"` // JSON array
	LogMessages      string                 `gorm:"type:text" json:"log_messages"`
	CreatedAt        time.Time              `json:"created_at"`
	UpdatedAt        time.Time              `json:"updated_at"`
}

// Trader represents a smart money trader
type Trader struct {
	ID              uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	WalletAddress   string    `gorm:"uniqueIndex;not null;size:64" json:"wallet_address"`
	Nickname        string    `gorm:"size:100" json:"nickname"`
	Avatar          string    `gorm:"size:500" json:"avatar"`
	IsVerified      bool      `gorm:"default:false" json:"is_verified"`
	IsTracked       bool      `gorm:"default:false" json:"is_tracked"`
	TotalTrades     int       `gorm:"default:0" json:"total_trades"`
	WinRate         float64   `gorm:"type:decimal(5,4);default:0" json:"win_rate"`
	TotalPnL        float64   `gorm:"type:decimal(20,4);default:0" json:"total_pnl"`
	AvgHoldTime     int       `gorm:"default:0" json:"avg_hold_time"` // in hours
	LastActiveAt    time.Time `json:"last_active_at"`
	Reputation      int       `gorm:"default:0" json:"reputation"`
	FollowerCount   int       `gorm:"default:0" json:"follower_count"`
	CreatedAt       time.Time `json:"created_at"`
	UpdatedAt       time.Time `json:"updated_at"`
}

// TransactionAnalysis represents AI analysis of transactions
type TransactionAnalysis struct {
	ID                 uuid.UUID  `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	TransactionID      uuid.UUID  `gorm:"type:uuid;not null" json:"transaction_id"`
	Transaction        SmartMoneyTransaction `gorm:"foreignKey:TransactionID;references:ID" json:"transaction"`
	AnalysisType       string     `gorm:"size:50;not null" json:"analysis_type"`
	Confidence         float64    `gorm:"type:decimal(4,3)" json:"confidence"`
	Sentiment          string     `gorm:"size:20" json:"sentiment"` // bullish, bearish, neutral
	SignalStrength     int        `gorm:"check:signal_strength >= 1 AND signal_strength <= 10" json:"signal_strength"`
	RiskLevel          string     `gorm:"size:20" json:"risk_level"` // low, medium, high
	Summary            string     `gorm:"type:text" json:"summary"`
	DetailedAnalysis   string     `gorm:"type:text" json:"detailed_analysis"`
	Metadata           string     `gorm:"type:jsonb" json:"metadata"` // JSON metadata
	CreatedAt          time.Time  `json:"created_at"`
	UpdatedAt          time.Time  `json:"updated_at"`
}

// TransactionType represents the type of transaction
type TransactionType string

const (
	TransactionTypeBuy    TransactionType = "buy"
	TransactionTypeSell   TransactionType = "sell"
	TransactionTypeSwap   TransactionType = "swap"
	TransactionTypeTransfer TransactionType = "transfer"
)

// TransactionStatus represents the status of a transaction
type TransactionStatus string

const (
	TransactionStatusSuccess TransactionStatus = "success"
	TransactionStatusFailed  TransactionStatus = "failed"
	TransactionStatusPending TransactionStatus = "pending"
)

// WalletFollowing represents wallet following relationships
type WalletFollowing struct {
	ID               uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	FollowerAddress  string    `gorm:"size:64;not null" json:"follower_address"`
	FollowingAddress string    `gorm:"size:64;not null" json:"following_address"`
	CreatedAt        time.Time `json:"created_at"`
}

// BeforeCreate hooks
func (smt *SmartMoneyTransaction) BeforeCreate(tx *gorm.DB) error {
	if smt.ID == uuid.Nil {
		smt.ID = uuid.New()
	}
	return nil
}

func (t *Trader) BeforeCreate(tx *gorm.DB) error {
	if t.ID == uuid.Nil {
		t.ID = uuid.New()
	}
	t.LastActiveAt = time.Now()
	return nil
}

func (ta *TransactionAnalysis) BeforeCreate(tx *gorm.DB) error {
	if ta.ID == uuid.Nil {
		ta.ID = uuid.New()
	}
	return nil
}

func (wf *WalletFollowing) BeforeCreate(tx *gorm.DB) error {
	if wf.ID == uuid.Nil {
		wf.ID = uuid.New()
	}
	return nil
}