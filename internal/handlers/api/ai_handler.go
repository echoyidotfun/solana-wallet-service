package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/services/ai"
)

// AIHandler handles AI-related API requests
type AIHandler struct {
	aiService ai.LangChainService
	logger    *logrus.Logger
}

// NewAIHandler creates a new AI handler
func NewAIHandler(aiService ai.LangChainService, logger *logrus.Logger) *AIHandler {
	return &AIHandler{
		aiService: aiService,
		logger:    logger,
	}
}

// AnalyzeToken handles token analysis requests
// @Summary Analyze token using AI
// @Description Get AI-powered analysis for a specific token
// @Tags AI
// @Accept json
// @Produce json
// @Param token_identifier path string true "Token mint address or symbol"
// @Success 200 {object} ai.TokenAnalysisResponse
// @Failure 400 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/ai/analyze/{token_identifier} [get]
func (h *AIHandler) AnalyzeToken(c *gin.Context) {
	tokenIdentifier := c.Param("token_identifier")
	if tokenIdentifier == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Error:   "Bad Request",
			Message: "Token identifier is required",
		})
		return
	}

	result, err := h.aiService.AnalyzeToken(c.Request.Context(), tokenIdentifier)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":            err,
			"token_identifier": tokenIdentifier,
		}).Error("Failed to analyze token")

		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Error:   "Internal Server Error",
			Message: "Failed to analyze token",
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// ChatCompletion handles general AI chat requests
// @Summary Get AI chat completion
// @Description Get AI response for general cryptocurrency and DeFi questions
// @Tags AI
// @Accept json
// @Produce json
// @Param request body ChatRequest true "Chat request"
// @Success 200 {object} ai.ChatResponse
// @Failure 400 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /api/v1/ai/chat [post]
func (h *AIHandler) ChatCompletion(c *gin.Context) {
	var req ChatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Error:   "Bad Request",
			Message: "Invalid request format",
		})
		return
	}

	if req.Message == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Error:   "Bad Request",
			Message: "Message is required",
		})
		return
	}

	result, err := h.aiService.GetChatCompletion(c.Request.Context(), req.Message)
	if err != nil {
		h.logger.WithFields(logrus.Fields{
			"error":   err,
			"message": req.Message,
		}).Error("Failed to get chat completion")

		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Error:   "Internal Server Error",
			Message: "Failed to process chat request",
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// Request/Response structures
type ChatRequest struct {
	Message string `json:"message" binding:"required"`
}

type ErrorResponse struct {
	Error   string `json:"error"`
	Message string `json:"message"`
}