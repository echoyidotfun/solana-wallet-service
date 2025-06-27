package api

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/services/token"
)

// TokenHandler handles HTTP requests for token operations
type TokenHandler struct {
	marketService   token.MarketService
	analysisService token.AnalysisService
	logger          *logrus.Logger
}

// NewTokenHandler creates a new token handler
func NewTokenHandler(marketService token.MarketService, analysisService token.AnalysisService, logger *logrus.Logger) *TokenHandler {
	return &TokenHandler{
		marketService:   marketService,
		analysisService: analysisService,
		logger:          logger,
	}
}

// CreateToken creates a new token
func (h *TokenHandler) CreateToken(c *gin.Context) {
	var req token.CreateTokenRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	token, err := h.marketService.CreateToken(c.Request.Context(), &req)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":        err,
			"mint_address": req.MintAddress,
		}).Error("Failed to create token")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create token"})
		return
	}
	
	c.JSON(http.StatusCreated, gin.H{
		"success": true,
		"data":    token,
	})
}

// GetToken gets token by mint address
func (h *TokenHandler) GetToken(c *gin.Context) {
	mintAddress := c.Param("mintAddress")
	if mintAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "mint_address is required"})
		return
	}
	
	token, err := h.marketService.GetToken(c.Request.Context(), mintAddress)
	if err != nil {
		if err.Error() == "token not found: "+mintAddress {
			c.JSON(http.StatusNotFound, gin.H{"error": "Token not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get token"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    token,
	})
}

// ListTokens lists all tokens with pagination
func (h *TokenHandler) ListTokens(c *gin.Context) {
	limitStr := c.DefaultQuery("limit", "20")
	offsetStr := c.DefaultQuery("offset", "0")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}
	
	tokens, err := h.marketService.ListTokens(c.Request.Context(), limit, offset)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to list tokens"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    tokens,
		"pagination": gin.H{
			"limit":  limit,
			"offset": offset,
			"count":  len(tokens),
		},
	})
}

// GetMarketData gets latest market data for a token
func (h *TokenHandler) GetMarketData(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	marketData, err := h.marketService.GetLatestMarketData(c.Request.Context(), tokenID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get market data"})
		return
	}
	
	if marketData == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Market data not found"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    marketData,
	})
}

// SyncMarketData syncs market data from external API
func (h *TokenHandler) SyncMarketData(c *gin.Context) {
	mintAddress := c.Param("mintAddress")
	if mintAddress == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "mint_address is required"})
		return
	}
	
	marketData, err := h.marketService.SyncMarketDataFromExternalAPI(c.Request.Context(), mintAddress)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":        err,
			"mint_address": mintAddress,
		}).Error("Failed to sync market data")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to sync market data"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    marketData,
	})
}

// SyncAllMarketData syncs market data for all tokens
func (h *TokenHandler) SyncAllMarketData(c *gin.Context) {
	err := h.marketService.SyncAllTokensMarketData(c.Request.Context())
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error": err,
		}).Error("Failed to sync all market data")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to sync all market data"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Market data sync initiated for all tokens",
	})
}

// GetTrendingTokens gets trending tokens by category
func (h *TokenHandler) GetTrendingTokens(c *gin.Context) {
	category := c.DefaultQuery("category", "general")
	timeframe := c.DefaultQuery("timeframe", "24h")
	limitStr := c.DefaultQuery("limit", "50")
	
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 50
	}
	
	rankings, err := h.marketService.GetTrendingTokens(c.Request.Context(), category, timeframe, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get trending tokens"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"category":  category,
			"timeframe": timeframe,
			"rankings":  rankings,
		},
	})
}

// GetTopHolders gets top holders for a token
func (h *TokenHandler) GetTopHolders(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	limitStr := c.DefaultQuery("limit", "20")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit <= 0 || limit > 100 {
		limit = 20
	}
	
	holders, err := h.marketService.GetTopHolders(c.Request.Context(), tokenID, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get top holders"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    holders,
	})
}

// GetTransactionStats gets transaction statistics for a token
func (h *TokenHandler) GetTransactionStats(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	timeframe := c.DefaultQuery("timeframe", "24h")
	
	stats, err := h.marketService.GetTransactionStats(c.Request.Context(), tokenID, timeframe)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to get transaction stats"})
		return
	}
	
	if stats == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Transaction stats not found"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    stats,
	})
}

// AnalyzeToken performs comprehensive token analysis
func (h *TokenHandler) AnalyzeToken(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	analysis, err := h.analysisService.AnalyzeTokenMarketData(c.Request.Context(), tokenID)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":    err,
			"token_id": tokenID,
		}).Error("Failed to analyze token")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to analyze token"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    analysis,
	})
}

// AnalyzeTrends analyzes token trends
func (h *TokenHandler) AnalyzeTrends(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	timeframe := c.DefaultQuery("timeframe", "24h")
	
	trends, err := h.analysisService.AnalyzeTokenTrends(c.Request.Context(), tokenID, timeframe)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":     err,
			"token_id":  tokenID,
			"timeframe": timeframe,
		}).Error("Failed to analyze trends")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to analyze trends"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    trends,
	})
}

// AnalyzeSentiment analyzes market sentiment for a token
func (h *TokenHandler) AnalyzeSentiment(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	sentiment, err := h.analysisService.AnalyzeMarketSentiment(c.Request.Context(), tokenID)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":    err,
			"token_id": tokenID,
		}).Error("Failed to analyze sentiment")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to analyze sentiment"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    sentiment,
	})
}

// AssessRisk performs risk assessment for a token
func (h *TokenHandler) AssessRisk(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	riskAssessment, err := h.analysisService.AssessTokenRisk(c.Request.Context(), tokenID)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":    err,
			"token_id": tokenID,
		}).Error("Failed to assess risk")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to assess risk"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    riskAssessment,
	})
}

// GetVolatilityMetrics gets volatility metrics for a token
func (h *TokenHandler) GetVolatilityMetrics(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	volatility, err := h.analysisService.CalculateVolatilityMetrics(c.Request.Context(), tokenID)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":    err,
			"token_id": tokenID,
		}).Error("Failed to calculate volatility")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to calculate volatility"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    volatility,
	})
}

// GetRecommendation gets AI recommendation for a token
func (h *TokenHandler) GetRecommendation(c *gin.Context) {
	tokenIDStr := c.Param("tokenId")
	tokenID, err := uuid.Parse(tokenIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID"})
		return
	}
	
	recommendation, err := h.analysisService.GenerateTokenRecommendation(c.Request.Context(), tokenID)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":    err,
			"token_id": tokenID,
		}).Error("Failed to generate recommendation")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to generate recommendation"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    recommendation,
	})
}

// BatchAnalyzeTokens performs batch analysis on multiple tokens
func (h *TokenHandler) BatchAnalyzeTokens(c *gin.Context) {
	var req struct {
		TokenIDs []string `json:"token_ids" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	
	// Parse token IDs
	var tokenIDs []uuid.UUID
	for _, idStr := range req.TokenIDs {
		id, err := uuid.Parse(idStr)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid token ID: " + idStr})
			return
		}
		tokenIDs = append(tokenIDs, id)
	}
	
	if len(tokenIDs) > 50 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Maximum 50 tokens allowed per batch"})
		return
	}
	
	results, err := h.analysisService.BatchAnalyzeTokens(c.Request.Context(), tokenIDs)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error": err,
			"count": len(tokenIDs),
		}).Error("Failed to perform batch analysis")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to perform batch analysis"})
		return
	}
	
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"results": results,
			"total":   len(results),
		},
	})
}

// RegisterRoutes registers token API routes
func (h *TokenHandler) RegisterRoutes(router *gin.RouterGroup) {
	tokens := router.Group("/tokens")
	{
		// Token management
		tokens.POST("", h.CreateToken)
		tokens.GET("", h.ListTokens)
		tokens.GET("/mint/:mintAddress", h.GetToken)
		
		// Market data
		tokens.GET("/:tokenId/market", h.GetMarketData)
		tokens.POST("/mint/:mintAddress/sync", h.SyncMarketData)
		tokens.POST("/sync-all", h.SyncAllMarketData)
		
		// Trending and stats
		tokens.GET("/trending", h.GetTrendingTokens)
		tokens.GET("/:tokenId/holders", h.GetTopHolders)
		tokens.GET("/:tokenId/stats", h.GetTransactionStats)
		
		// Analysis endpoints
		tokens.GET("/:tokenId/analyze", h.AnalyzeToken)
		tokens.GET("/:tokenId/trends", h.AnalyzeTrends)
		tokens.GET("/:tokenId/sentiment", h.AnalyzeSentiment)
		tokens.GET("/:tokenId/risk", h.AssessRisk)
		tokens.GET("/:tokenId/volatility", h.GetVolatilityMetrics)
		tokens.GET("/:tokenId/recommendation", h.GetRecommendation)
		
		// Batch operations
		tokens.POST("/batch/analyze", h.BatchAnalyzeTokens)
	}
}