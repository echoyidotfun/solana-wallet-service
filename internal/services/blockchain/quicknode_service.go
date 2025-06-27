package blockchain

import (
	"encoding/json"
	"fmt"
	"net/url"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
)

// QuickNodeService manages WebSocket connections to QuickNode
type QuickNodeService interface {
	Connect() error
	Disconnect() error
	SubscribeWalletLogs(walletAddress string, consumer LogConsumer) error
	UnsubscribeWalletLogs(walletAddress string) error
	IsConnected() bool
	GetActiveSubscriptions() map[string]string
}

// LogConsumer defines callback for processing wallet logs
type LogConsumer func(notification *LogsNotification) error

type quickNodeService struct {
	config                      *config.QuickNodeConfig
	logger                      *logrus.Logger
	conn                        *websocket.Conn
	mu                          sync.RWMutex
	isConnected                 bool
	reconnectAttempts           int
	maxReconnectAttempts        int
	
	// Subscription management
	pendingSubscriptions        map[string]*SubscriptionRequest  // requestId -> request
	activeSubscriptionsByQnId   map[string]string                // quicknodeId -> walletAddress
	activeQnIdByWallet          map[string]string                // walletAddress -> quicknodeId
	walletNotificationConsumers map[string]LogConsumer           // walletAddress -> consumer
	
	// Control channels
	stopChan                    chan bool
	reconnectChan               chan bool
}

// Request/Response structures for QuickNode WebSocket API
type SubscriptionRequest struct {
	ID      string                 `json:"id"`
	JSONRPC string                 `json:"jsonrpc"`
	Method  string                 `json:"method"`
	Params  []interface{}          `json:"params"`
}

type SubscriptionResponse struct {
	ID      string      `json:"id"`
	JSONRPC string      `json:"jsonrpc"`
	Result  interface{} `json:"result,omitempty"`
	Error   *RPCError   `json:"error,omitempty"`
}

type RPCError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type LogsNotification struct {
	JSONRPC string `json:"jsonrpc"`
	Method  string `json:"method"`
	Params  struct {
		Result struct {
			Context struct {
				Slot           int64  `json:"slot"`
				Commitment     string `json:"commitment"`
			} `json:"context"`
			Value struct {
				Signature   string   `json:"signature"`
				Slot        int64    `json:"slot"`
				Timestamp   int64    `json:"blockTime"`
				Logs        []string `json:"logs"`
				Err         interface{} `json:"err"`
			} `json:"value"`
		} `json:"result"`
		Subscription string `json:"subscription"`
	} `json:"params"`
}

// NewQuickNodeService creates a new QuickNode service instance
func NewQuickNodeService(config *config.QuickNodeConfig, logger *logrus.Logger) QuickNodeService {
	return &quickNodeService{
		config:                      config,
		logger:                      logger,
		maxReconnectAttempts:        10,
		pendingSubscriptions:        make(map[string]*SubscriptionRequest),
		activeSubscriptionsByQnId:   make(map[string]string),
		activeQnIdByWallet:          make(map[string]string),
		walletNotificationConsumers: make(map[string]LogConsumer),
		stopChan:                    make(chan bool),
		reconnectChan:               make(chan bool),
	}
}

// Connect establishes WebSocket connection to QuickNode
func (q *quickNodeService) Connect() error {
	q.mu.Lock()
	defer q.mu.Unlock()
	
	if q.isConnected {
		return nil
	}
	
	// Prepare WebSocket URL with auth
	u, err := url.Parse(q.config.WSSUrl)
	if err != nil {
		return fmt.Errorf("invalid WebSocket URL: %w", err)
	}
	
	// Add authentication headers
	headers := map[string][]string{
		"Authorization": {fmt.Sprintf("Bearer %s", q.config.APIKey)},
	}
	
	dialer := websocket.Dialer{
		HandshakeTimeout: 30 * time.Second,
	}
	
	conn, _, err := dialer.Dial(u.String(), headers)
	if err != nil {
		return fmt.Errorf("failed to connect to QuickNode: %w", err)
	}
	
	q.conn = conn
	q.isConnected = true
	q.reconnectAttempts = 0
	
	// Start message handling goroutines
	go q.readPump()
	go q.writePump()
	go q.connectionMonitor()
	
	q.logger.Info("Connected to QuickNode WebSocket")
	return nil
}

// Disconnect closes the WebSocket connection
func (q *quickNodeService) Disconnect() error {
	q.mu.Lock()
	defer q.mu.Unlock()
	
	if !q.isConnected {
		return nil
	}
	
	close(q.stopChan)
	
	if q.conn != nil {
		q.conn.Close()
	}
	
	q.isConnected = false
	q.logger.Info("Disconnected from QuickNode WebSocket")
	return nil
}

// SubscribeWalletLogs subscribes to logs for a specific wallet
func (q *quickNodeService) SubscribeWalletLogs(walletAddress string, consumer LogConsumer) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	
	if !q.isConnected {
		return fmt.Errorf("not connected to QuickNode")
	}
	
	// Check if already subscribed
	if _, exists := q.activeQnIdByWallet[walletAddress]; exists {
		q.walletNotificationConsumers[walletAddress] = consumer
		q.logger.WithField("wallet", walletAddress).Info("Updated consumer for existing subscription")
		return nil
	}
	
	// Create subscription request
	requestID := fmt.Sprintf("sub_%s_%d", walletAddress[:8], time.Now().UnixNano())
	
	request := &SubscriptionRequest{
		ID:      requestID,
		JSONRPC: "2.0",
		Method:  "logsSubscribe",
		Params: []interface{}{
			map[string]interface{}{
				"mentions": []string{walletAddress},
			},
			map[string]interface{}{
				"commitment": "confirmed",
			},
		},
	}
	
	// Store pending subscription
	q.pendingSubscriptions[requestID] = request
	q.walletNotificationConsumers[walletAddress] = consumer
	
	// Send subscription request
	if err := q.conn.WriteJSON(request); err != nil {
		delete(q.pendingSubscriptions, requestID)
		delete(q.walletNotificationConsumers, walletAddress)
		return fmt.Errorf("failed to send subscription request: %w", err)
	}
	
	q.logger.WithFields(logrus.Fields{
		"wallet":     walletAddress,
		"request_id": requestID,
	}).Info("Sent wallet logs subscription request")
	
	return nil
}

// UnsubscribeWalletLogs unsubscribes from logs for a specific wallet
func (q *quickNodeService) UnsubscribeWalletLogs(walletAddress string) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	
	if !q.isConnected {
		return fmt.Errorf("not connected to QuickNode")
	}
	
	// Get QuickNode subscription ID
	qnId, exists := q.activeQnIdByWallet[walletAddress]
	if !exists {
		q.logger.WithField("wallet", walletAddress).Warn("No active subscription found")
		return nil
	}
	
	// Create unsubscribe request
	requestID := fmt.Sprintf("unsub_%s_%d", walletAddress[:8], time.Now().UnixNano())
	
	request := &SubscriptionRequest{
		ID:      requestID,
		JSONRPC: "2.0",
		Method:  "logsUnsubscribe",
		Params:  []interface{}{qnId},
	}
	
	// Send unsubscribe request
	if err := q.conn.WriteJSON(request); err != nil {
		return fmt.Errorf("failed to send unsubscribe request: %w", err)
	}
	
	// Clean up local state
	delete(q.activeQnIdByWallet, walletAddress)
	delete(q.activeSubscriptionsByQnId, qnId)
	delete(q.walletNotificationConsumers, walletAddress)
	
	q.logger.WithFields(logrus.Fields{
		"wallet":       walletAddress,
		"quicknode_id": qnId,
	}).Info("Sent unsubscribe request")
	
	return nil
}

// IsConnected returns connection status
func (q *quickNodeService) IsConnected() bool {
	q.mu.RLock()
	defer q.mu.RUnlock()
	return q.isConnected
}

// GetActiveSubscriptions returns active subscriptions
func (q *quickNodeService) GetActiveSubscriptions() map[string]string {
	q.mu.RLock()
	defer q.mu.RUnlock()
	
	result := make(map[string]string)
	for wallet, qnId := range q.activeQnIdByWallet {
		result[wallet] = qnId
	}
	return result
}

// readPump handles incoming WebSocket messages
func (q *quickNodeService) readPump() {
	defer func() {
		q.mu.Lock()
		q.isConnected = false
		q.mu.Unlock()
		q.triggerReconnect()
	}()
	
	for {
		select {
		case <-q.stopChan:
			return
		default:
			_, message, err := q.conn.ReadMessage()
			if err != nil {
				if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
					q.logger.WithError(err).Error("WebSocket read error")
				}
				return
			}
			
			q.handleMessage(message)
		}
	}
}

// writePump handles outgoing WebSocket messages
func (q *quickNodeService) writePump() {
	ticker := time.NewTicker(54 * time.Second)
	defer ticker.Stop()
	
	for {
		select {
		case <-q.stopChan:
			return
		case <-ticker.C:
			// Send ping to keep connection alive
			q.mu.Lock()
			if q.conn != nil {
				q.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
				if err := q.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
					q.logger.WithError(err).Error("Failed to send ping")
					q.mu.Unlock()
					return
				}
			}
			q.mu.Unlock()
		}
	}
}

// handleMessage processes incoming WebSocket messages
func (q *quickNodeService) handleMessage(message []byte) {
	// Try to parse as subscription response first
	var subResponse SubscriptionResponse
	if err := json.Unmarshal(message, &subResponse); err == nil && subResponse.ID != "" {
		q.handleSubscriptionResponse(&subResponse)
		return
	}
	
	// Try to parse as logs notification
	var notification LogsNotification
	if err := json.Unmarshal(message, &notification); err == nil && notification.Method == "logsNotification" {
		q.handleLogsNotification(&notification)
		return
	}
	
	q.logger.WithField("message", string(message)).Debug("Received unknown message type")
}

// handleSubscriptionResponse processes subscription confirmation/error responses
func (q *quickNodeService) handleSubscriptionResponse(response *SubscriptionResponse) {
	q.mu.Lock()
	defer q.mu.Unlock()
	
	pendingReq, exists := q.pendingSubscriptions[response.ID]
	if !exists {
		q.logger.WithField("response_id", response.ID).Warn("Received response for unknown request")
		return
	}
	
	delete(q.pendingSubscriptions, response.ID)
	
	if response.Error != nil {
		q.logger.WithFields(logrus.Fields{
			"request_id": response.ID,
			"error_code": response.Error.Code,
			"error_msg":  response.Error.Message,
		}).Error("Subscription request failed")
		return
	}
	
	// Extract wallet address from mentions parameter
	if len(pendingReq.Params) > 0 {
		if filterMap, ok := pendingReq.Params[0].(map[string]interface{}); ok {
			if mentions, ok := filterMap["mentions"].([]string); ok && len(mentions) > 0 {
				walletAddress := mentions[0]
				qnId := fmt.Sprintf("%v", response.Result)
				
				q.activeQnIdByWallet[walletAddress] = qnId
				q.activeSubscriptionsByQnId[qnId] = walletAddress
				
				q.logger.WithFields(logrus.Fields{
					"wallet":       walletAddress,
					"quicknode_id": qnId,
				}).Info("Wallet logs subscription confirmed")
			}
		}
	}
}

// handleLogsNotification processes incoming log notifications
func (q *quickNodeService) handleLogsNotification(notification *LogsNotification) {
	q.mu.RLock()
	walletAddress, exists := q.activeSubscriptionsByQnId[notification.Params.Subscription]
	consumer, hasConsumer := q.walletNotificationConsumers[walletAddress]
	q.mu.RUnlock()
	
	if !exists {
		q.logger.WithField("subscription", notification.Params.Subscription).Warn("Received notification for unknown subscription")
		return
	}
	
	if !hasConsumer {
		q.logger.WithField("wallet", walletAddress).Warn("No consumer registered for wallet")
		return
	}
	
	// Process notification asynchronously
	go func() {
		if err := consumer(notification); err != nil {
			q.logger.WithFields(logrus.Fields{
				"wallet": walletAddress,
				"error":  err,
			}).Error("Error processing log notification")
		}
	}()
}

// connectionMonitor monitors connection health and triggers reconnection
func (q *quickNodeService) connectionMonitor() {
	for {
		select {
		case <-q.stopChan:
			return
		case <-q.reconnectChan:
			q.attemptReconnect()
		}
	}
}

// triggerReconnect triggers a reconnection attempt
func (q *quickNodeService) triggerReconnect() {
	select {
	case q.reconnectChan <- true:
	default:
		// Channel is full, reconnect already in progress
	}
}

// attemptReconnect attempts to reconnect to QuickNode
func (q *quickNodeService) attemptReconnect() {
	q.mu.Lock()
	if q.isConnected {
		q.mu.Unlock()
		return
	}
	
	if q.reconnectAttempts >= q.maxReconnectAttempts {
		q.logger.Error("Max reconnect attempts reached, giving up")
		q.mu.Unlock()
		return
	}
	
	q.reconnectAttempts++
	q.mu.Unlock()
	
	// Exponential backoff
	backoff := time.Duration(q.reconnectAttempts) * time.Second
	if backoff > 30*time.Second {
		backoff = 30 * time.Second
	}
	
	q.logger.WithFields(logrus.Fields{
		"attempt": q.reconnectAttempts,
		"backoff": backoff,
	}).Info("Attempting to reconnect to QuickNode")
	
	time.Sleep(backoff)
	
	if err := q.Connect(); err != nil {
		q.logger.WithError(err).Error("Reconnection failed")
		q.triggerReconnect()
		return
	}
	
	// Restore previous subscriptions
	q.restoreSubscriptions()
}

// restoreSubscriptions restores all active subscriptions after reconnection
func (q *quickNodeService) restoreSubscriptions() {
	q.mu.RLock()
	consumersToRestore := make(map[string]LogConsumer)
	for wallet, consumer := range q.walletNotificationConsumers {
		consumersToRestore[wallet] = consumer
	}
	q.mu.RUnlock()
	
	for wallet, consumer := range consumersToRestore {
		if err := q.SubscribeWalletLogs(wallet, consumer); err != nil {
			q.logger.WithFields(logrus.Fields{
				"wallet": wallet,
				"error":  err,
			}).Error("Failed to restore subscription")
		}
	}
	
	q.logger.WithField("count", len(consumersToRestore)).Info("Restored wallet subscriptions")
}