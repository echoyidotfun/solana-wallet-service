package token

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/domain/models"
	"github.com/wallet/service/internal/domain/repositories"
)

// MarketService defines the interface for token market data operations
type MarketService interface {
	// Token management
	CreateToken(ctx context.Context, req *CreateTokenRequest) (*models.Token, error)
	GetToken(ctx context.Context, mintAddress string) (*models.Token, error)
	GetTokenByID(ctx context.Context, id uuid.UUID) (*models.Token, error)
	ListTokens(ctx context.Context, limit, offset int) ([]*models.Token, error)
	UpdateToken(ctx context.Context, token *models.Token) error
	
	// Market data
	UpdateMarketData(ctx context.Context, tokenID uuid.UUID, data *models.TokenMarketData) error
	GetLatestMarketData(ctx context.Context, tokenID uuid.UUID) (*models.TokenMarketData, error)
	SyncMarketDataFromExternalAPI(ctx context.Context, mintAddress string) (*models.TokenMarketData, error)
	
	// Trending and rankings
	UpdateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error
	GetTrendingTokens(ctx context.Context, category, timeframe string, limit int) ([]*models.TokenTrendingRanking, error)
	
	// Top holders
	UpdateTopHolders(ctx context.Context, tokenID uuid.UUID, holders []*models.TokenTopHolders) error
	GetTopHolders(ctx context.Context, tokenID uuid.UUID, limit int) ([]*models.TokenTopHolders, error)
	
	// Transaction statistics
	UpdateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error
	GetTransactionStats(ctx context.Context, tokenID uuid.UUID, timeframe string) (*models.TokenTransactionStats, error)
	
	// Batch operations
	BatchUpdateMarketData(ctx context.Context, data []*models.TokenMarketData) error
	SyncAllTokensMarketData(ctx context.Context) error
}

type marketService struct {
	tokenRepo    repositories.TokenRepository
	logger       *logrus.Logger
	httpClient   *http.Client
	apiBaseURL   string
	apiKey       string
}

// NewMarketService creates a new market service instance
func NewMarketService(tokenRepo repositories.TokenRepository, logger *logrus.Logger, apiBaseURL, apiKey string) MarketService {
	return &marketService{
		tokenRepo:  tokenRepo,
		logger:     logger,
		httpClient: &http.Client{Timeout: 30 * time.Second},
		apiBaseURL: apiBaseURL,
		apiKey:     apiKey,
	}
}

// Request/Response structs
type CreateTokenRequest struct {
	MintAddress string  `json:"mint_address" validate:"required"`
	Symbol      string  `json:"symbol" validate:"required"`
	Name        string  `json:"name" validate:"required"`
	Decimals    int     `json:"decimals" validate:"required,min=0,max=18"`
	LogoURI     *string `json:"logo_uri,omitempty"`
	Description *string `json:"description,omitempty"`
	Website     *string `json:"website,omitempty"`
	Twitter     *string `json:"twitter,omitempty"`
	Telegram    *string `json:"telegram,omitempty"`
}

// External API response structures
type ExternalMarketDataResponse struct {
	Data struct {
		Price          float64 `json:"price"`
		PriceUSD       float64 `json:"price_usd"`
		Volume24h      float64 `json:"volume_24h"`
		VolumeChange24h float64 `json:"volume_change_24h"`
		MarketCap      float64 `json:"market_cap"`
		MarketCapRank  int     `json:"market_cap_rank"`
		PriceChange1h  float64 `json:"price_change_1h"`
		PriceChange24h float64 `json:"price_change_24h"`
		PriceChange7d  float64 `json:"price_change_7d"`
		CirculatingSupply float64 `json:"circulating_supply"`
		TotalSupply    float64 `json:"total_supply"`
		MaxSupply      float64 `json:"max_supply"`
		ATH            float64 `json:"ath"`
		ATL            float64 `json:"atl"`
	} `json:"data"`
	LastUpdated time.Time `json:"last_updated"`
}

// Token management
func (s *marketService) CreateToken(ctx context.Context, req *CreateTokenRequest) (*models.Token, error) {
	// Check if token already exists
	existingToken, err := s.tokenRepo.GetByMintAddress(ctx, req.MintAddress)
	if err != nil {
		return nil, fmt.Errorf("failed to check existing token: %w", err)
	}
	if existingToken != nil {
		return existingToken, nil // Return existing token
	}
	
	token := &models.Token{
		MintAddress: req.MintAddress,
		Symbol:      req.Symbol,
		Name:        req.Name,
		Decimals:    req.Decimals,
		LogoURI:     req.LogoURI,
		Description: req.Description,
		Website:     req.Website,
		Twitter:     req.Twitter,
		Telegram:    req.Telegram,
	}
	
	if err := s.tokenRepo.Create(ctx, token); err != nil {
		s.logger.WithFields(logrus.Fields{
			"error":        err,
			"mint_address": req.MintAddress,
		}).Error("Failed to create token")
		return nil, err
	}
	
	s.logger.WithFields(logrus.Fields{
		"token_id":     token.ID,
		"mint_address": req.MintAddress,
		"symbol":       req.Symbol,
	}).Info("Token created successfully")
	
	return token, nil
}

func (s *marketService) GetToken(ctx context.Context, mintAddress string) (*models.Token, error) {
	token, err := s.tokenRepo.GetByMintAddress(ctx, mintAddress)
	if err != nil {
		return nil, err
	}
	if token == nil {
		return nil, fmt.Errorf("token not found: %s", mintAddress)
	}
	return token, nil
}

func (s *marketService) GetTokenByID(ctx context.Context, id uuid.UUID) (*models.Token, error) {
	return s.tokenRepo.GetByID(ctx, id)
}

func (s *marketService) ListTokens(ctx context.Context, limit, offset int) ([]*models.Token, error) {
	return s.tokenRepo.List(ctx, limit, offset)
}

func (s *marketService) UpdateToken(ctx context.Context, token *models.Token) error {
	return s.tokenRepo.Update(ctx, token)
}

// Market data operations
func (s *marketService) UpdateMarketData(ctx context.Context, tokenID uuid.UUID, data *models.TokenMarketData) error {
	data.TokenID = tokenID
	
	// Try to update existing data first
	existing, err := s.tokenRepo.GetLatestMarketData(ctx, tokenID)
	if err != nil {
		return fmt.Errorf("failed to get existing market data: %w", err)
	}
	
	if existing != nil {
		// Update existing record
		data.ID = existing.ID
		return s.tokenRepo.UpdateMarketData(ctx, data)
	}
	
	// Create new record
	return s.tokenRepo.CreateMarketData(ctx, data)
}

func (s *marketService) GetLatestMarketData(ctx context.Context, tokenID uuid.UUID) (*models.TokenMarketData, error) {
	return s.tokenRepo.GetLatestMarketData(ctx, tokenID)
}

func (s *marketService) SyncMarketDataFromExternalAPI(ctx context.Context, mintAddress string) (*models.TokenMarketData, error) {
	// Get token info
	token, err := s.GetToken(ctx, mintAddress)
	if err != nil {
		return nil, fmt.Errorf("failed to get token: %w", err)
	}
	
	// Fetch from external API
	url := fmt.Sprintf("%s/tokens/%s/market", s.apiBaseURL, mintAddress)
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	if s.apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+s.apiKey)
	}
	req.Header.Set("Content-Type", "application/json")
	
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch market data: %w", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API returned status %d", resp.StatusCode)
	}
	
	var apiResponse ExternalMarketDataResponse
	if err := json.NewDecoder(resp.Body).Decode(&apiResponse); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}
	
	// Convert to internal model
	marketData := &models.TokenMarketData{
		TokenID:           token.ID,
		Price:             apiResponse.Data.Price,
		PriceUSD:          apiResponse.Data.PriceUSD,
		Volume24h:         apiResponse.Data.Volume24h,
		VolumeChange24h:   apiResponse.Data.VolumeChange24h,
		MarketCap:         apiResponse.Data.MarketCap,
		MarketCapRank:     apiResponse.Data.MarketCapRank,
		PriceChange1h:     apiResponse.Data.PriceChange1h,
		PriceChange24h:    apiResponse.Data.PriceChange24h,
		PriceChange7d:     apiResponse.Data.PriceChange7d,
		CirculatingSupply: apiResponse.Data.CirculatingSupply,
		TotalSupply:       apiResponse.Data.TotalSupply,
		MaxSupply:         apiResponse.Data.MaxSupply,
		ATH:               apiResponse.Data.ATH,
		ATL:               apiResponse.Data.ATL,
		LastUpdated:       apiResponse.LastUpdated,
	}
	
	// Save to database
	if err := s.UpdateMarketData(ctx, token.ID, marketData); err != nil {
		return nil, fmt.Errorf("failed to save market data: %w", err)
	}
	
	s.logger.WithFields(logrus.Fields{
		"token_id":     token.ID,
		"mint_address": mintAddress,
		"price_usd":    marketData.PriceUSD,
	}).Info("Market data synced successfully")
	
	return marketData, nil
}

// Trending and rankings
func (s *marketService) UpdateTrendingRanking(ctx context.Context, ranking *models.TokenTrendingRanking) error {
	// Try to update existing ranking first
	existing, err := s.tokenRepo.GetTrendingTokens(ctx, string(ranking.Category), ranking.Timeframe, 1)
	if err != nil {
		return fmt.Errorf("failed to check existing ranking: %w", err)
	}
	
	// Check if this token already has a ranking for this category/timeframe
	for _, existingRanking := range existing {
		if existingRanking.TokenID == ranking.TokenID {
			ranking.ID = existingRanking.ID
			return s.tokenRepo.UpdateTrendingRanking(ctx, ranking)
		}
	}
	
	// Create new ranking
	return s.tokenRepo.CreateTrendingRanking(ctx, ranking)
}

func (s *marketService) GetTrendingTokens(ctx context.Context, category, timeframe string, limit int) ([]*models.TokenTrendingRanking, error) {
	return s.tokenRepo.GetTrendingTokens(ctx, category, timeframe, limit)
}

// Top holders
func (s *marketService) UpdateTopHolders(ctx context.Context, tokenID uuid.UUID, holders []*models.TokenTopHolders) error {
	for _, holder := range holders {
		holder.TokenID = tokenID
		
		// Try to update existing holder first
		existing, err := s.tokenRepo.GetTopHolders(ctx, tokenID, 1000) // Get all holders
		if err != nil {
			return fmt.Errorf("failed to get existing holders: %w", err)
		}
		
		found := false
		for _, existingHolder := range existing {
			if existingHolder.HolderAddress == holder.HolderAddress {
				holder.ID = existingHolder.ID
				if err := s.tokenRepo.UpdateTopHolder(ctx, holder); err != nil {
					return fmt.Errorf("failed to update holder: %w", err)
				}
				found = true
				break
			}
		}
		
		if !found {
			if err := s.tokenRepo.CreateTopHolder(ctx, holder); err != nil {
				return fmt.Errorf("failed to create holder: %w", err)
			}
		}
	}
	
	return nil
}

func (s *marketService) GetTopHolders(ctx context.Context, tokenID uuid.UUID, limit int) ([]*models.TokenTopHolders, error) {
	return s.tokenRepo.GetTopHolders(ctx, tokenID, limit)
}

// Transaction statistics
func (s *marketService) UpdateTransactionStats(ctx context.Context, stats *models.TokenTransactionStats) error {
	// Try to update existing stats first
	existing, err := s.tokenRepo.GetTransactionStats(ctx, stats.TokenID, stats.Timeframe)
	if err != nil {
		return fmt.Errorf("failed to get existing stats: %w", err)
	}
	
	if existing != nil {
		stats.ID = existing.ID
		return s.tokenRepo.UpdateTransactionStats(ctx, stats)
	}
	
	// Create new stats
	return s.tokenRepo.CreateTransactionStats(ctx, stats)
}

func (s *marketService) GetTransactionStats(ctx context.Context, tokenID uuid.UUID, timeframe string) (*models.TokenTransactionStats, error) {
	return s.tokenRepo.GetTransactionStats(ctx, tokenID, timeframe)
}

// Batch operations
func (s *marketService) BatchUpdateMarketData(ctx context.Context, data []*models.TokenMarketData) error {
	for _, marketData := range data {
		if err := s.UpdateMarketData(ctx, marketData.TokenID, marketData); err != nil {
			s.logger.WithFields(logrus.Fields{
				"error":    err,
				"token_id": marketData.TokenID,
			}).Error("Failed to update market data in batch")
			continue // Continue with other tokens
		}
	}
	
	s.logger.WithFields(logrus.Fields{
		"count": len(data),
	}).Info("Batch market data update completed")
	
	return nil
}

func (s *marketService) SyncAllTokensMarketData(ctx context.Context) error {
	// Get all tokens with pagination
	limit := 100
	offset := 0
	totalSynced := 0
	
	for {
		tokens, err := s.tokenRepo.List(ctx, limit, offset)
		if err != nil {
			return fmt.Errorf("failed to get tokens: %w", err)
		}
		
		if len(tokens) == 0 {
			break // No more tokens
		}
		
		// Sync market data for each token
		for _, token := range tokens {
			if _, err := s.SyncMarketDataFromExternalAPI(ctx, token.MintAddress); err != nil {
				s.logger.WithFields(logrus.Fields{
					"error":        err,
					"mint_address": token.MintAddress,
				}).Error("Failed to sync market data")
				continue // Continue with other tokens
			}
			totalSynced++
			
			// Add small delay to avoid rate limiting
			time.Sleep(100 * time.Millisecond)
		}
		
		offset += limit
		
		// Break if we got less than the limit (last page)
		if len(tokens) < limit {
			break
		}
	}
	
	s.logger.WithFields(logrus.Fields{
		"total_synced": totalSynced,
	}).Info("All tokens market data sync completed")
	
	return nil
}