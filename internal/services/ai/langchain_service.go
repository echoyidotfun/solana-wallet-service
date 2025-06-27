package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
	"github.com/emiyaio/solana-wallet-service/internal/domain/models"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
	"github.com/emiyaio/solana-wallet-service/internal/services/token"
)

// LangChainService provides AI-powered analysis using OpenAI
type LangChainService interface {
	AnalyzeToken(ctx context.Context, tokenIdentifier string) (*TokenAnalysisResponse, error)
	GetChatCompletion(ctx context.Context, userPrompt string) (*ChatResponse, error)
}

type langChainService struct {
	config            *config.OpenAIConfig
	tokenRepo         repositories.TokenRepository
	marketService     token.MarketService
	solanaTracker     token.SolanaTrackerService
	openAIClient      OpenAIClient
	logger            *logrus.Logger
}

// OpenAI client interface
type OpenAIClient interface {
	CreateChatCompletion(ctx context.Context, request *ChatCompletionRequest) (*ChatCompletionResponse, error)
}

// AI response structures
type TokenAnalysisResponse struct {
	TokenAddress string `json:"token_address"`
	Symbol       string `json:"symbol"`
	Name         string `json:"name"`
	Analysis     string `json:"analysis"`
	Confidence   float64 `json:"confidence"`
	Timestamp    string `json:"timestamp"`
}

type ChatResponse struct {
	Content   string `json:"content"`
	Usage     Usage  `json:"usage"`
	Timestamp string `json:"timestamp"`
}

// OpenAI API structures
type ChatCompletionRequest struct {
	Model       string    `json:"model"`
	Messages    []Message `json:"messages"`
	Functions   []Function `json:"functions,omitempty"`
	Temperature float64   `json:"temperature,omitempty"`
	MaxTokens   int       `json:"max_tokens,omitempty"`
}

type ChatCompletionResponse struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Created int64    `json:"created"`
	Model   string   `json:"model"`
	Choices []Choice `json:"choices"`
	Usage   Usage    `json:"usage"`
}

type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type Choice struct {
	Index        int     `json:"index"`
	Message      Message `json:"message"`
	FinishReason string  `json:"finish_reason"`
}

type Usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

type Function struct {
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	Parameters  map[string]interface{} `json:"parameters"`
}

// Token database tool data structure
type AggregatedTokenData struct {
	BasicInfo      *TokenBasicInfo      `json:"basic_info"`
	MarketData     *TokenMarketData     `json:"market_data"`
	TopHolders     []TokenTopHolder     `json:"top_holders"`
	TxStats        *TokenTxStats        `json:"transaction_stats"`
	TrendingRank   *TokenTrendingRank   `json:"trending_rank"`
}

type TokenBasicInfo struct {
	Address     string `json:"address"`
	Symbol      string `json:"symbol"`
	Name        string `json:"name"`
	LogoURI     string `json:"logo_uri"`
	Description string `json:"description"`
	Website     string `json:"website"`
	Twitter     string `json:"twitter"`
	Telegram    string `json:"telegram"`
	CreatedAt   string `json:"created_at"`
}

type TokenMarketData struct {
	Price             float64 `json:"price"`
	PriceChange1h     float64 `json:"price_change_1h"`
	PriceChange24h    float64 `json:"price_change_24h"`
	PriceChange7d     float64 `json:"price_change_7d"`
	Volume24h         float64 `json:"volume_24h"`
	VolumeChange24h   float64 `json:"volume_change_24h"`
	MarketCap         float64 `json:"market_cap"`
	MarketCapRank     int     `json:"market_cap_rank"`
	Liquidity         float64 `json:"liquidity"`
	CirculatingSupply float64 `json:"circulating_supply"`
	TotalSupply       float64 `json:"total_supply"`
	ATH               float64 `json:"ath"`
	ATL               float64 `json:"atl"`
	HolderCount       int     `json:"holder_count"`
}

type TokenTopHolder struct {
	Address    string  `json:"address"`
	Balance    float64 `json:"balance"`
	Percentage float64 `json:"percentage"`
	Rank       int     `json:"rank"`
}

type TokenTxStats struct {
	TransactionCount int     `json:"transaction_count"`
	BuyCount         int     `json:"buy_count"`
	SellCount        int     `json:"sell_count"`
	UniqueTraders    int     `json:"unique_traders"`
	BuyVolume        float64 `json:"buy_volume"`
	SellVolume       float64 `json:"sell_volume"`
}

type TokenTrendingRank struct {
	Rank     int    `json:"rank"`
	Category string `json:"category"`
	Score    float64 `json:"score"`
}

// NewLangChainService creates a new AI service instance
func NewLangChainService(
	config *config.OpenAIConfig,
	tokenRepo repositories.TokenRepository,
	marketService token.MarketService,
	solanaTracker token.SolanaTrackerService,
	logger *logrus.Logger,
) LangChainService {
	openAIClient := NewOpenAIClient(config.APIKey, config.BaseURL)
	
	return &langChainService{
		config:        config,
		tokenRepo:     tokenRepo,
		marketService: marketService,
		solanaTracker: solanaTracker,
		openAIClient:  openAIClient,
		logger:        logger,
	}
}

// AnalyzeToken performs AI-powered token analysis
func (s *langChainService) AnalyzeToken(ctx context.Context, tokenIdentifier string) (*TokenAnalysisResponse, error) {
	// Get aggregated token data using the tool function
	tokenData, err := s.getTokenAnalysisData(ctx, tokenIdentifier)
	if err != nil {
		return nil, fmt.Errorf("failed to get token data: %w", err)
	}
	
	// Prepare the analysis prompt
	systemPrompt := `You are a professional cryptocurrency market analyst with deep knowledge of DeFi and Solana ecosystem. 
	Analyze the provided token data and give a comprehensive but concise analysis covering:
	1. Current market position and performance
	2. Price trends and momentum
	3. Trading volume and liquidity analysis
	4. Holder distribution insights
	5. Risk assessment and key considerations
	6. Short-term outlook (next 1-7 days)
	
	Keep your analysis factual, balanced, and professional. Highlight both opportunities and risks.
	Provide actionable insights for traders and investors.`
	
	// Convert token data to JSON for the prompt
	dataJSON, err := json.MarshalIndent(tokenData, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to marshal token data: %w", err)
	}
	
	userPrompt := fmt.Sprintf("Please analyze this token based on the following data:\n\n%s", string(dataJSON))
	
	// Create chat completion request
	request := &ChatCompletionRequest{
		Model: s.config.Model,
		Messages: []Message{
			{Role: "system", Content: systemPrompt},
			{Role: "user", Content: userPrompt},
		},
		Temperature: 0.3, // Lower temperature for more consistent analysis
		MaxTokens:   1500,
	}
	
	// Call OpenAI API
	response, err := s.openAIClient.CreateChatCompletion(ctx, request)
	if err != nil {
		return nil, fmt.Errorf("failed to get AI analysis: %w", err)
	}
	
	if len(response.Choices) == 0 {
		return nil, fmt.Errorf("no response from AI model")
	}
	
	analysis := response.Choices[0].Message.Content
	confidence := s.calculateConfidence(tokenData)
	
	result := &TokenAnalysisResponse{
		TokenAddress: tokenData.BasicInfo.Address,
		Symbol:       tokenData.BasicInfo.Symbol,
		Name:         tokenData.BasicInfo.Name,
		Analysis:     analysis,
		Confidence:   confidence,
		Timestamp:    fmt.Sprintf("%d", getCurrentUnixTimestamp()),
	}
	
	s.logger.WithFields(logrus.Fields{
		"token_address": tokenData.BasicInfo.Address,
		"symbol":        tokenData.BasicInfo.Symbol,
		"confidence":    confidence,
		"tokens_used":   response.Usage.TotalTokens,
	}).Info("AI token analysis completed")
	
	return result, nil
}

// GetChatCompletion provides general AI chat functionality
func (s *langChainService) GetChatCompletion(ctx context.Context, userPrompt string) (*ChatResponse, error) {
	systemPrompt := `You are a knowledgeable cryptocurrency and DeFi expert assistant. 
	Provide helpful, accurate, and educational responses about blockchain technology, 
	cryptocurrency trading, DeFi protocols, and market analysis. 
	Be concise but informative, and always emphasize the importance of DYOR (Do Your Own Research).`
	
	request := &ChatCompletionRequest{
		Model: s.config.Model,
		Messages: []Message{
			{Role: "system", Content: systemPrompt},
			{Role: "user", Content: userPrompt},
		},
		Temperature: 0.7,
		MaxTokens:   800,
	}
	
	response, err := s.openAIClient.CreateChatCompletion(ctx, request)
	if err != nil {
		return nil, fmt.Errorf("failed to get chat completion: %w", err)
	}
	
	if len(response.Choices) == 0 {
		return nil, fmt.Errorf("no response from AI model")
	}
	
	result := &ChatResponse{
		Content:   response.Choices[0].Message.Content,
		Usage:     response.Usage,
		Timestamp: fmt.Sprintf("%d", getCurrentUnixTimestamp()),
	}
	
	s.logger.WithFields(logrus.Fields{
		"tokens_used": response.Usage.TotalTokens,
		"prompt_len":  len(userPrompt),
	}).Info("AI chat completion completed")
	
	return result, nil
}

// getTokenAnalysisData aggregates token data from multiple sources (similar to Java TokenDatabaseTool)
func (s *langChainService) getTokenAnalysisData(ctx context.Context, tokenIdentifier string) (*AggregatedTokenData, error) {
	// Try to find token by symbol first, then by address
	var tokenAddress string
	var token *models.Token
	var err error
	
	// Check if it's a valid Solana address (base58, 32-44 characters)
	if len(tokenIdentifier) >= 32 && len(tokenIdentifier) <= 44 {
		tokenAddress = tokenIdentifier
		token, err = s.tokenRepo.GetByMintAddress(ctx, tokenIdentifier)
	} else {
		// Search by symbol
		tokens, err := s.tokenRepo.List(ctx, 1000, 0) // Get many tokens to search
		if err == nil {
			for _, t := range tokens {
				if strings.EqualFold(t.Symbol, tokenIdentifier) {
					token = t
					tokenAddress = t.MintAddress
					break
				}
			}
		}
	}
	
	// If token not found in database, try to get from SolanaTracker
	if token == nil {
		tokenInfoResp, err := s.solanaTracker.GetTokenInfo(tokenAddress)
		if err != nil {
			return nil, fmt.Errorf("token not found in database or SolanaTracker: %w", err)
		}
		
		tokenInfo := tokenInfoResp.Data
		tokenAddress = tokenInfo.Address
		
		// Create basic info from SolanaTracker data
		basicInfo := &TokenBasicInfo{
			Address:     tokenInfo.Address,
			Symbol:      tokenInfo.Symbol,
			Name:        tokenInfo.Name,
			LogoURI:     tokenInfo.LogoURI,
			Description: tokenInfo.Description,
			Website:     tokenInfo.Website,
			Twitter:     tokenInfo.Twitter,
			Telegram:    tokenInfo.Telegram,
			CreatedAt:   tokenInfo.CreatedAt,
		}
		
		marketData := &TokenMarketData{
			Price:             tokenInfo.Price,
			PriceChange1h:     tokenInfo.PriceChange1h,
			PriceChange24h:    tokenInfo.PriceChange24h,
			PriceChange7d:     tokenInfo.PriceChange7d,
			Volume24h:         tokenInfo.Volume24h,
			VolumeChange24h:   tokenInfo.VolumeChange24h,
			MarketCap:         tokenInfo.MarketCap,
			MarketCapRank:     tokenInfo.MarketCapRank,
			Liquidity:         tokenInfo.Liquidity,
			CirculatingSupply: tokenInfo.CirculatingSupply,
			TotalSupply:       tokenInfo.TotalSupply,
			ATH:               tokenInfo.ATH,
			ATL:               tokenInfo.ATL,
			HolderCount:       tokenInfo.HolderCount,
		}
		
		var topHolders []TokenTopHolder
		for _, holder := range tokenInfo.TopHolders {
			topHolders = append(topHolders, TokenTopHolder{
				Address:    holder.Address,
				Balance:    holder.Balance,
				Percentage: holder.Percentage,
				Rank:       holder.Rank,
			})
		}
		
		return &AggregatedTokenData{
			BasicInfo:    basicInfo,
			MarketData:   marketData,
			TopHolders:   topHolders,
			TxStats:      nil, // Not available from SolanaTracker
			TrendingRank: nil, // Would need to check trending data
		}, nil
	}
	
	// Token found in database, aggregate data
	basicInfo := &TokenBasicInfo{
		Address:     token.MintAddress,
		Symbol:      token.Symbol,
		Name:        token.Name,
		LogoURI:     *token.LogoURI,
		Description: *token.Description,
		Website:     *token.Website,
		Twitter:     *token.Twitter,
		Telegram:    *token.Telegram,
		CreatedAt:   token.CreatedAt.Format("2006-01-02T15:04:05Z"),
	}
	
	// Get market data
	var marketData *TokenMarketData
	if latestMarket, err := s.marketService.GetLatestMarketData(ctx, token.ID); err == nil && latestMarket != nil {
		marketData = &TokenMarketData{
			Price:             latestMarket.PriceUSD,
			PriceChange1h:     latestMarket.PriceChange1h,
			PriceChange24h:    latestMarket.PriceChange24h,
			PriceChange7d:     latestMarket.PriceChange7d,
			Volume24h:         latestMarket.Volume24h,
			VolumeChange24h:   latestMarket.VolumeChange24h,
			MarketCap:         latestMarket.MarketCap,
			MarketCapRank:     latestMarket.MarketCapRank,
			CirculatingSupply: latestMarket.CirculatingSupply,
			TotalSupply:       latestMarket.TotalSupply,
			ATH:               latestMarket.ATH,
			ATL:               latestMarket.ATL,
		}
	}
	
	// Get top holders
	var topHolders []TokenTopHolder
	if holders, err := s.marketService.GetTopHolders(ctx, token.ID, 10); err == nil {
		for _, holder := range holders {
			topHolders = append(topHolders, TokenTopHolder{
				Address:    holder.HolderAddress,
				Balance:    holder.Balance,
				Percentage: holder.Percentage,
				Rank:       holder.Rank,
			})
		}
	}
	
	// Get transaction stats
	var txStats *TokenTxStats
	if stats, err := s.marketService.GetTransactionStats(ctx, token.ID, "24h"); err == nil && stats != nil {
		txStats = &TokenTxStats{
			TransactionCount: stats.TransactionCount,
			BuyCount:         stats.BuyCount,
			SellCount:        stats.SellCount,
			UniqueTraders:    stats.UniqueTraders,
			BuyVolume:        stats.BuyVolume,
			SellVolume:       stats.SellVolume,
		}
	}
	
	return &AggregatedTokenData{
		BasicInfo:    basicInfo,
		MarketData:   marketData,
		TopHolders:   topHolders,
		TxStats:      txStats,
		TrendingRank: nil, // Would need to implement trending rank lookup
	}, nil
}

// calculateConfidence calculates analysis confidence based on data availability
func (s *langChainService) calculateConfidence(data *AggregatedTokenData) float64 {
	confidence := 0.0
	
	// Basic info availability
	if data.BasicInfo != nil {
		confidence += 0.2
	}
	
	// Market data availability and quality
	if data.MarketData != nil {
		confidence += 0.3
		if data.MarketData.Volume24h > 10000 { // Decent volume
			confidence += 0.1
		}
		if data.MarketData.MarketCap > 100000 { // Decent market cap
			confidence += 0.1
		}
	}
	
	// Top holders data
	if len(data.TopHolders) > 0 {
		confidence += 0.1
	}
	
	// Transaction stats
	if data.TxStats != nil {
		confidence += 0.1
		if data.TxStats.UniqueTraders > 100 {
			confidence += 0.1
		}
	}
	
	return confidence
}

// getCurrentUnixTimestamp returns current Unix timestamp
func getCurrentUnixTimestamp() int64 {
	return time.Now().Unix()
}