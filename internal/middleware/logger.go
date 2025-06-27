package middleware

import (
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
)

// Logger middleware for request logging
func Logger(logger *logrus.Logger) gin.HandlerFunc {
	return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		// Custom log format
		logger.WithFields(logrus.Fields{
			"method":      param.Method,
			"path":        param.Path,
			"status":      param.StatusCode,
			"latency":     param.Latency,
			"client_ip":   param.ClientIP,
			"user_agent":  param.Request.UserAgent(),
			"error":       param.ErrorMessage,
		}).Info("HTTP Request")
		
		return ""
	})
}

// RequestID middleware adds a unique request ID to each request
func RequestID() gin.HandlerFunc {
	return gin.HandlerFunc(func(c *gin.Context) {
		requestID := c.GetHeader("X-Request-ID")
		if requestID == "" {
			requestID = fmt.Sprintf("%d", time.Now().UnixNano())
		}
		
		c.Header("X-Request-ID", requestID)
		c.Set("request_id", requestID)
		c.Next()
	})
}