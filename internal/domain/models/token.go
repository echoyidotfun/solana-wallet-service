package models

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// Token represents the basic token information
type Token struct {
	ID          uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	MintAddress string    `gorm:"uniqueIndex;not null" json:"mint_address"`
	Symbol      string    `gorm:"size:50" json:"symbol"`
	Name        string    `gorm:"size:255" json:"name"`
	Decimals    int       `gorm:"not null;default:9" json:"decimals"`
	LogoURI     string    `gorm:"size:500" json:"logo_uri"`
	Description string    `gorm:"type:text" json:"description"`
	Website     string    `gorm:"size:500" json:"website"`
	Twitter     string    `gorm:"size:500" json:"twitter"`
	Telegram    string    `gorm:"size:500" json:"telegram"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// TokenMarketData represents real-time market data for tokens
type TokenMarketData struct {
	ID                uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	TokenID           uuid.UUID `gorm:"type:uuid;not null" json:"token_id"`
	Token             Token     `gorm:"foreignKey:TokenID;references:ID" json:"token"`
	Price             float64   `gorm:"type:decimal(20,10)" json:"price"`
	PriceUSD          float64   `gorm:"type:decimal(20,10)" json:"price_usd"`
	Volume24h         float64   `gorm:"type:decimal(20,4)" json:"volume_24h"`
	VolumeChange24h   float64   `gorm:"type:decimal(10,4)" json:"volume_change_24h"`
	MarketCap         float64   `gorm:"type:decimal(20,4)" json:"market_cap"`
	MarketCapRank     int       `json:"market_cap_rank"`
	PriceChange1h     float64   `gorm:"type:decimal(10,4)" json:"price_change_1h"`
	PriceChange24h    float64   `gorm:"type:decimal(10,4)" json:"price_change_24h"`
	PriceChange7d     float64   `gorm:"type:decimal(10,4)" json:"price_change_7d"`
	CirculatingSupply float64   `gorm:"type:decimal(20,4)" json:"circulating_supply"`
	TotalSupply       float64   `gorm:"type:decimal(20,4)" json:"total_supply"`
	MaxSupply         float64   `gorm:"type:decimal(20,4)" json:"max_supply"`
	ATH               float64   `gorm:"type:decimal(20,10)" json:"ath"`
	ATL               float64   `gorm:"type:decimal(20,10)" json:"atl"`
	LastUpdated       time.Time `json:"last_updated"`
	CreatedAt         time.Time `json:"created_at"`
	UpdatedAt         time.Time `json:"updated_at"`
}

// TokenTrendingRanking represents trending token rankings
type TokenTrendingRanking struct {
	ID          uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	TokenID     uuid.UUID `gorm:"type:uuid;not null" json:"token_id"`
	Token       Token     `gorm:"foreignKey:TokenID;references:ID" json:"token"`
	Rank        int       `gorm:"not null" json:"rank"`
	Category    string    `gorm:"size:50;not null" json:"category"` // trending, volume, latest
	Timeframe   string    `gorm:"size:10;not null" json:"timeframe"` // 1h, 24h, 7d
	Score       float64   `gorm:"type:decimal(10,4)" json:"score"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// TokenTopHolders represents top holders information
type TokenTopHolders struct {
	ID              uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	TokenID         uuid.UUID `gorm:"type:uuid;not null" json:"token_id"`
	Token           Token     `gorm:"foreignKey:TokenID;references:ID" json:"token"`
	HolderAddress   string    `gorm:"size:64;not null" json:"holder_address"`
	Balance         float64   `gorm:"type:decimal(20,4)" json:"balance"`
	Percentage      float64   `gorm:"type:decimal(6,4)" json:"percentage"`
	Rank            int       `gorm:"not null" json:"rank"`
	CreatedAt       time.Time `json:"created_at"`
	UpdatedAt       time.Time `json:"updated_at"`
}

// TokenTransactionStats represents transaction statistics
type TokenTransactionStats struct {
	ID                uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"id"`
	TokenID           uuid.UUID `gorm:"type:uuid;not null" json:"token_id"`
	Token             Token     `gorm:"foreignKey:TokenID;references:ID" json:"token"`
	Timeframe         string    `gorm:"size:10;not null" json:"timeframe"` // 1h, 24h, 7d
	TransactionCount  int       `json:"transaction_count"`
	BuyCount          int       `json:"buy_count"`
	SellCount         int       `json:"sell_count"`
	UniqueTraders     int       `json:"unique_traders"`
	BuyVolume         float64   `gorm:"type:decimal(20,4)" json:"buy_volume"`
	SellVolume        float64   `gorm:"type:decimal(20,4)" json:"sell_volume"`
	NetVolume         float64   `gorm:"type:decimal(20,4)" json:"net_volume"`
	AverageTradeSize  float64   `gorm:"type:decimal(20,4)" json:"average_trade_size"`
	CreatedAt         time.Time `json:"created_at"`
	UpdatedAt         time.Time `json:"updated_at"`
}

// BeforeCreate hook for Token
func (t *Token) BeforeCreate(tx *gorm.DB) error {
	if t.ID == uuid.Nil {
		t.ID = uuid.New()
	}
	return nil
}

// BeforeCreate hooks for other models
func (tmd *TokenMarketData) BeforeCreate(tx *gorm.DB) error {
	if tmd.ID == uuid.Nil {
		tmd.ID = uuid.New()
	}
	return nil
}

func (ttr *TokenTrendingRanking) BeforeCreate(tx *gorm.DB) error {
	if ttr.ID == uuid.Nil {
		ttr.ID = uuid.New()
	}
	return nil
}

func (tth *TokenTopHolders) BeforeCreate(tx *gorm.DB) error {
	if tth.ID == uuid.Nil {
		tth.ID = uuid.New()
	}
	return nil
}

func (tts *TokenTransactionStats) BeforeCreate(tx *gorm.DB) error {
	if tts.ID == uuid.Nil {
		tts.ID = uuid.New()
	}
	return nil
}