package token

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
)

// SolanaTrackerService handles data fetching from SolanaTracker API
type SolanaTrackerService interface {
	GetTrendingTokens(timeframe string) (*TrendingTokensResponse, error)
	GetVolumeTokens(timeframe string) (*VolumeTokensResponse, error)
	GetLatestTokens() (*LatestTokensResponse, error)
	GetTokenInfo(mintAddress string) (*TokenInfoResponse, error)
	GetTopTraders(page int, sortBy string, expandPnl bool) (*TopTradersResponse, error)
}

type solanaTrackerService struct {
	config        *config.SolanaTrackerConfig
	httpClient    *http.Client
	logger        *logrus.Logger
	rateLimiter   *RateLimiter
	failedTokens  map[string]time.Time // Track failed requests
	failedMutex   sync.RWMutex
}

// RateLimiter implements rate limiting for API calls
type RateLimiter struct {
	tokens   chan struct{}
	interval time.Duration
}

// SolanaTracker API response structures
type TrendingTokensResponse struct {
	Data []TrendingToken `json:"data"`
}

type TrendingToken struct {
	Address           string  `json:"address"`
	Symbol            string  `json:"symbol"`
	Name              string  `json:"name"`
	LogoURI           string  `json:"logoURI"`
	Price             float64 `json:"price"`
	PriceChange1h     float64 `json:"priceChange1h"`
	PriceChange24h    float64 `json:"priceChange24h"`
	Volume24h         float64 `json:"volume24h"`
	VolumeChange24h   float64 `json:"volumeChange24h"`
	MarketCap         float64 `json:"marketCap"`
	Liquidity         float64 `json:"liquidity"`
	HolderCount       int     `json:"holderCount"`
	CreatedAt         string  `json:"createdAt"`
}

type VolumeTokensResponse struct {
	Data []VolumeToken `json:"data"`
}

type VolumeToken struct {
	Address         string  `json:"address"`
	Symbol          string  `json:"symbol"`
	Name            string  `json:"name"`
	LogoURI         string  `json:"logoURI"`
	Price           float64 `json:"price"`
	Volume24h       float64 `json:"volume24h"`
	VolumeChange24h float64 `json:"volumeChange24h"`
	MarketCap       float64 `json:"marketCap"`
	Liquidity       float64 `json:"liquidity"`
}

type LatestTokensResponse struct {
	Data []LatestToken `json:"data"`
}

type LatestToken struct {
	Address     string  `json:"address"`
	Symbol      string  `json:"symbol"`
	Name        string  `json:"name"`
	LogoURI     string  `json:"logoURI"`
	Price       float64 `json:"price"`
	MarketCap   float64 `json:"marketCap"`
	Liquidity   float64 `json:"liquidity"`
	HolderCount int     `json:"holderCount"`
	CreatedAt   string  `json:"createdAt"`
}

type TokenInfoResponse struct {
	Data TokenInfo `json:"data"`
}

type TokenInfo struct {
	Address           string             `json:"address"`
	Symbol            string             `json:"symbol"`
	Name              string             `json:"name"`
	LogoURI           string             `json:"logoURI"`
	Description       string             `json:"description"`
	Website           string             `json:"website"`
	Twitter           string             `json:"twitter"`
	Telegram          string             `json:"telegram"`
	Price             float64            `json:"price"`
	PriceChange1h     float64            `json:"priceChange1h"`
	PriceChange24h    float64            `json:"priceChange24h"`
	PriceChange7d     float64            `json:"priceChange7d"`
	Volume24h         float64            `json:"volume24h"`
	VolumeChange24h   float64            `json:"volumeChange24h"`
	MarketCap         float64            `json:"marketCap"`
	MarketCapRank     int                `json:"marketCapRank"`
	Liquidity         float64            `json:"liquidity"`
	CirculatingSupply float64            `json:"circulatingSupply"`
	TotalSupply       float64            `json:"totalSupply"`
	MaxSupply         float64            `json:"maxSupply"`
	ATH               float64            `json:"ath"`
	ATL               float64            `json:"atl"`
	HolderCount       int                `json:"holderCount"`
	TopHolders        []TokenTopHolder   `json:"topHolders"`
	CreatedAt         string             `json:"createdAt"`
	LastUpdated       string             `json:"lastUpdated"`
}

type TokenTopHolder struct {
	Address    string  `json:"address"`
	Balance    float64 `json:"balance"`
	Percentage float64 `json:"percentage"`
	Rank       int     `json:"rank"`
}

type TopTradersResponse struct {
	Data []TopTrader `json:"data"`
}

type TopTrader struct {
	WalletAddress string  `json:"walletAddress"`
	TotalTrades   int     `json:"totalTrades"`
	WinRate       float64 `json:"winRate"`
	TotalPnL      float64 `json:"totalPnL"`
	AvgHoldTime   float64 `json:"avgHoldTime"`
	LastActive    string  `json:"lastActive"`
	IsVerified    bool    `json:"isVerified"`
	Reputation    int     `json:"reputation"`
}

// NewSolanaTrackerService creates a new SolanaTracker service instance
func NewSolanaTrackerService(config *config.SolanaTrackerConfig, logger *logrus.Logger) SolanaTrackerService {
	rateLimiter := &RateLimiter{
		tokens:   make(chan struct{}, 1), // 1 request per interval
		interval: time.Second,            // 1 second interval
	}
	
	// Initialize rate limiter
	go rateLimiter.start()
	
	return &solanaTrackerService{
		config:       config,
		httpClient:   &http.Client{Timeout: 30 * time.Second},
		logger:       logger,
		rateLimiter:  rateLimiter,
		failedTokens: make(map[string]time.Time),
	}
}

// start initializes the rate limiter
func (rl *RateLimiter) start() {
	ticker := time.NewTicker(rl.interval)
	defer ticker.Stop()
	
	for range ticker.C {
		select {
		case rl.tokens <- struct{}{}:
		default:
			// Channel is full, skip this tick
		}
	}
}

// wait blocks until a token is available
func (rl *RateLimiter) wait() {
	<-rl.tokens
}

// GetTrendingTokens fetches trending tokens from SolanaTracker
func (s *solanaTrackerService) GetTrendingTokens(timeframe string) (*TrendingTokensResponse, error) {
	s.rateLimiter.wait()
	
	url := fmt.Sprintf("%s/tokens/trending", s.config.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	// Add query parameters
	q := req.URL.Query()
	if timeframe != "" {
		q.Add("timeframe", timeframe)
	}
	req.URL.RawQuery = q.Encode()
	
	// Add headers
	s.addAuthHeaders(req)
	
	var response TrendingTokensResponse
	if err := s.makeRequest(req, &response); err != nil {
		return nil, fmt.Errorf("failed to get trending tokens: %w", err)
	}
	
	s.logger.WithFields(logrus.Fields{
		"timeframe": timeframe,
		"count":     len(response.Data),
	}).Info("Fetched trending tokens from SolanaTracker")
	
	return &response, nil
}

// GetVolumeTokens fetches tokens with highest volume
func (s *solanaTrackerService) GetVolumeTokens(timeframe string) (*VolumeTokensResponse, error) {
	s.rateLimiter.wait()
	
	url := fmt.Sprintf("%s/tokens/volume", s.config.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	// Add query parameters
	q := req.URL.Query()
	if timeframe != "" {
		q.Add("timeframe", timeframe)
	}
	req.URL.RawQuery = q.Encode()
	
	s.addAuthHeaders(req)
	
	var response VolumeTokensResponse
	if err := s.makeRequest(req, &response); err != nil {
		return nil, fmt.Errorf("failed to get volume tokens: %w", err)
	}
	
	s.logger.WithFields(logrus.Fields{
		"timeframe": timeframe,
		"count":     len(response.Data),
	}).Info("Fetched volume tokens from SolanaTracker")
	
	return &response, nil
}

// GetLatestTokens fetches latest tokens
func (s *solanaTrackerService) GetLatestTokens() (*LatestTokensResponse, error) {
	s.rateLimiter.wait()
	
	url := fmt.Sprintf("%s/tokens/latest", s.config.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	s.addAuthHeaders(req)
	
	var response LatestTokensResponse
	if err := s.makeRequest(req, &response); err != nil {
		return nil, fmt.Errorf("failed to get latest tokens: %w", err)
	}
	
	s.logger.WithField("count", len(response.Data)).Info("Fetched latest tokens from SolanaTracker")
	
	return &response, nil
}

// GetTokenInfo fetches detailed info for a specific token
func (s *solanaTrackerService) GetTokenInfo(mintAddress string) (*TokenInfoResponse, error) {
	// Check if this token recently failed
	if s.isTokenRecentlyFailed(mintAddress) {
		return nil, fmt.Errorf("token %s recently failed, skipping", mintAddress)
	}
	
	s.rateLimiter.wait()
	
	url := fmt.Sprintf("%s/tokens/%s", s.config.BaseURL, mintAddress)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	s.addAuthHeaders(req)
	
	var response TokenInfoResponse
	if err := s.makeRequest(req, &response); err != nil {
		// Mark token as failed
		s.markTokenAsFailed(mintAddress)
		return nil, fmt.Errorf("failed to get token info: %w", err)
	}
	
	s.logger.WithFields(logrus.Fields{
		"mint_address": mintAddress,
		"symbol":       response.Data.Symbol,
	}).Info("Fetched token info from SolanaTracker")
	
	return &response, nil
}

// GetTopTraders fetches top traders data
func (s *solanaTrackerService) GetTopTraders(page int, sortBy string, expandPnl bool) (*TopTradersResponse, error) {
	s.rateLimiter.wait()
	
	url := fmt.Sprintf("%s/traders/top", s.config.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	// Add query parameters
	q := req.URL.Query()
	if page > 0 {
		q.Add("page", fmt.Sprintf("%d", page))
	}
	if sortBy != "" {
		q.Add("sortBy", sortBy)
	}
	if expandPnl {
		q.Add("expandPnl", "true")
	}
	req.URL.RawQuery = q.Encode()
	
	s.addAuthHeaders(req)
	
	var response TopTradersResponse
	if err := s.makeRequest(req, &response); err != nil {
		return nil, fmt.Errorf("failed to get top traders: %w", err)
	}
	
	s.logger.WithFields(logrus.Fields{
		"page":    page,
		"sort_by": sortBy,
		"count":   len(response.Data),
	}).Info("Fetched top traders from SolanaTracker")
	
	return &response, nil
}

// addAuthHeaders adds authentication headers to the request
func (s *solanaTrackerService) addAuthHeaders(req *http.Request) {
	if s.config.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+s.config.APIKey)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "solana-wallet-service/1.0")
}

// makeRequest executes the HTTP request and decodes the response
func (s *solanaTrackerService) makeRequest(req *http.Request, response interface{}) error {
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("HTTP request failed: %w", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("API returned status %d", resp.StatusCode)
	}
	
	if err := json.NewDecoder(resp.Body).Decode(response); err != nil {
		return fmt.Errorf("failed to decode response: %w", err)
	}
	
	return nil
}

// isTokenRecentlyFailed checks if a token recently failed
func (s *solanaTrackerService) isTokenRecentlyFailed(mintAddress string) bool {
	s.failedMutex.RLock()
	defer s.failedMutex.RUnlock()
	
	failedTime, exists := s.failedTokens[mintAddress]
	if !exists {
		return false
	}
	
	// Block failed tokens for 30 minutes
	return time.Since(failedTime) < 30*time.Minute
}

// markTokenAsFailed marks a token as failed
func (s *solanaTrackerService) markTokenAsFailed(mintAddress string) {
	s.failedMutex.Lock()
	defer s.failedMutex.Unlock()
	
	s.failedTokens[mintAddress] = time.Now()
	
	// Clean up old entries (older than 1 hour)
	cutoff := time.Now().Add(-time.Hour)
	for addr, failTime := range s.failedTokens {
		if failTime.Before(cutoff) {
			delete(s.failedTokens, addr)
		}
	}
}