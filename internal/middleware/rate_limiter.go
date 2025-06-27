package middleware

import (
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

// RateLimiter implements a simple token bucket rate limiter
type RateLimiter struct {
	visitors map[string]*visitor
	mu       sync.RWMutex
	rate     time.Duration
	capacity int
}

type visitor struct {
	tokens   int
	lastSeen time.Time
}

// NewRateLimiter creates a new rate limiter
func NewRateLimiter(requestsPerMinute int) *RateLimiter {
	rl := &RateLimiter{
		visitors: make(map[string]*visitor),
		rate:     time.Minute / time.Duration(requestsPerMinute),
		capacity: requestsPerMinute,
	}
	
	// Start cleanup goroutine
	go rl.cleanupVisitors()
	
	return rl
}

// Middleware returns the rate limiting middleware
func (rl *RateLimiter) Middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		clientIP := c.ClientIP()
		
		if !rl.allow(clientIP) {
			c.JSON(http.StatusTooManyRequests, gin.H{
				"error": "Rate limit exceeded",
				"retry_after": rl.rate.Seconds(),
			})
			c.Abort()
			return
		}
		
		c.Next()
	}
}

// allow checks if a request from the given IP is allowed
func (rl *RateLimiter) allow(ip string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	
	v, exists := rl.visitors[ip]
	if !exists {
		rl.visitors[ip] = &visitor{
			tokens:   rl.capacity - 1,
			lastSeen: time.Now(),
		}
		return true
	}
	
	// Refill tokens based on time passed
	now := time.Now()
	elapsed := now.Sub(v.lastSeen)
	tokensToAdd := int(elapsed / rl.rate)
	
	if tokensToAdd > 0 {
		v.tokens += tokensToAdd
		if v.tokens > rl.capacity {
			v.tokens = rl.capacity
		}
		v.lastSeen = now
	}
	
	if v.tokens <= 0 {
		return false
	}
	
	v.tokens--
	return true
}

// cleanupVisitors removes old visitor entries
func (rl *RateLimiter) cleanupVisitors() {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	
	for range ticker.C {
		rl.mu.Lock()
		threshold := time.Now().Add(-time.Hour)
		
		for ip, v := range rl.visitors {
			if v.lastSeen.Before(threshold) {
				delete(rl.visitors, ip)
			}
		}
		rl.mu.Unlock()
	}
}