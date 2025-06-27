package blockchain

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/sirupsen/logrus"
	"github.com/emiyaio/solana-wallet-service/internal/config"
	"github.com/emiyaio/solana-wallet-service/internal/domain/repositories"
)

// TransactionProcessor processes and analyzes Solana transactions
type TransactionProcessor interface {
	ProcessLogNotification(notification *LogsNotification) (*AnalyzedWalletAction, error)
	GetTransactionDetails(signature string) (*SolanaTransactionResponse, error)
	AnalyzeTransaction(tx *SolanaTransactionResponse) (*AnalyzedWalletAction, error)
	IsRelevantTransaction(logs []string) bool
}

type transactionProcessor struct {
	config      *config.QuickNodeConfig
	httpClient  *http.Client
	tokenRepo   repositories.TokenRepository
	logger      *logrus.Logger
	
	// Known DEX program IDs
	dexPrograms map[string]string
}

// Solana transaction structures
type SolanaTransactionResponse struct {
	BlockTime       int64                    `json:"blockTime"`
	Meta            TransactionMeta          `json:"meta"`
	Slot            int64                    `json:"slot"`
	Transaction     TransactionInfo          `json:"transaction"`
}

type TransactionMeta struct {
	Err                interface{}       `json:"err"`
	Fee                int64            `json:"fee"`
	InnerInstructions  []interface{}    `json:"innerInstructions"`
	LogMessages        []string         `json:"logMessages"`
	PostBalances       []int64          `json:"postBalances"`
	PostTokenBalances  []TokenBalance   `json:"postTokenBalances"`
	PreBalances        []int64          `json:"preBalances"`
	PreTokenBalances   []TokenBalance   `json:"preTokenBalances"`
	Rewards            []interface{}    `json:"rewards"`
	Status             map[string]interface{} `json:"status"`
}

type TokenBalance struct {
	AccountIndex  int    `json:"accountIndex"`
	Mint         string `json:"mint"`
	Owner        string `json:"owner"`
	ProgramId    string `json:"programId"`
	UITokenAmount struct {
		Amount         string  `json:"amount"`
		Decimals       int     `json:"decimals"`
		UIAmount       float64 `json:"uiAmount"`
		UIAmountString string  `json:"uiAmountString"`
	} `json:"uiTokenAmount"`
}

type TransactionInfo struct {
	Message    MessageInfo `json:"message"`
	Signatures []string    `json:"signatures"`
}

type MessageInfo struct {
	AccountKeys     []string      `json:"accountKeys"`
	Header          MessageHeader `json:"header"`
	Instructions    []Instruction `json:"instructions"`
	RecentBlockhash string        `json:"recentBlockhash"`
}

type MessageHeader struct {
	NumReadonlySignedAccounts   int `json:"numReadonlySignedAccounts"`
	NumReadonlyUnsignedAccounts int `json:"numReadonlyUnsignedAccounts"`
	NumRequiredSignatures       int `json:"numRequiredSignatures"`
}

type Instruction struct {
	Accounts       []int  `json:"accounts"`
	Data           string `json:"data"`
	ProgramIdIndex int    `json:"programIdIndex"`
}

// AnalyzedWalletAction represents a processed wallet action
type AnalyzedWalletAction struct {
	WalletAddress    string                 `json:"wallet_address"`
	Platform         string                 `json:"platform"`
	TransactionType  string                 `json:"transaction_type"` // buy, sell, swap
	InputToken       *TokenAmount           `json:"input_token"`
	OutputToken      *TokenAmount           `json:"output_token"`
	Signature        string                 `json:"signature"`
	Slot             int64                  `json:"slot"`
	BlockTime        time.Time              `json:"block_time"`
	LogMessages      []string               `json:"log_messages"`
	Success          bool                   `json:"success"`
	Fee              int64                  `json:"fee"`
}

type TokenAmount struct {
	Mint     string  `json:"mint"`
	Amount   float64 `json:"amount"`
	Decimals int     `json:"decimals"`
	Symbol   string  `json:"symbol,omitempty"`
}

// NewTransactionProcessor creates a new transaction processor
func NewTransactionProcessor(
	config *config.QuickNodeConfig,
	tokenRepo repositories.TokenRepository,
	logger *logrus.Logger,
) TransactionProcessor {
	// Initialize DEX program mappings
	dexPrograms := map[string]string{
		"JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4":  "Jupiter",
		"675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8": "Raydium",
		"6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P":  "Pump.fun",
		"9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM": "Orca",
		"DjVE6JNiYqPL2QXyCUUh8rNjHrbz9hXHNYt99MQ59qw1": "Orca Whirlpool",
		"CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK": "Raydium CLMM",
		"9KEPoZmtHUrBbhWN1v1KWLMkkvwY6WLtAVUCPRtRjP4z": "Lifinity",
		"SSwpkEEcbUqx4vtoEByFjSkhKdCT862DNVb52nZg1UZ":  "Sabre",
		"AMM55ShdkoGRB5jVYPjWziwk8m5MpwyDgsMWHaMSQWH6": "Aldrin",
		"EhYXq3ANp5nAerUpbSgd7VK2RRcxK1zNuSQ755G5Mtxx": "Step Finance",
	}
	
	return &transactionProcessor{
		config:      config,
		httpClient:  &http.Client{Timeout: 30 * time.Second},
		tokenRepo:   tokenRepo,
		logger:      logger,
		dexPrograms: dexPrograms,
	}
}

// ProcessLogNotification processes a log notification from QuickNode
func (tp *transactionProcessor) ProcessLogNotification(notification *LogsNotification) (*AnalyzedWalletAction, error) {
	// Pre-filter: check if logs contain relevant DEX activity
	if !tp.IsRelevantTransaction(notification.Params.Result.Value.Logs) {
		return nil, nil // Not a relevant transaction
	}
	
	signature := notification.Params.Result.Value.Signature
	
	// Get full transaction details
	txDetails, err := tp.GetTransactionDetails(signature)
	if err != nil {
		return nil, fmt.Errorf("failed to get transaction details: %w", err)
	}
	
	// Analyze transaction
	action, err := tp.AnalyzeTransaction(txDetails)
	if err != nil {
		return nil, fmt.Errorf("failed to analyze transaction: %w", err)
	}
	
	tp.logger.WithFields(logrus.Fields{
		"signature": signature,
		"platform":  action.Platform,
		"type":      action.TransactionType,
	}).Info("Processed transaction")
	
	return action, nil
}

// GetTransactionDetails fetches full transaction details from QuickNode RPC
func (tp *transactionProcessor) GetTransactionDetails(signature string) (*SolanaTransactionResponse, error) {
	requestBody := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      1,
		"method":  "getTransaction",
		"params": []interface{}{
			signature,
			map[string]interface{}{
				"encoding":                       "json",
				"commitment":                     "confirmed",
				"maxSupportedTransactionVersion": 0,
			},
		},
	}
	
	reqBytes, err := json.Marshal(requestBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}
	
	req, err := http.NewRequest("POST", tp.config.HTTPUrl, strings.NewReader(string(reqBytes)))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+tp.config.APIKey)
	
	resp, err := tp.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()
	
	var rpcResponse struct {
		Result *SolanaTransactionResponse `json:"result"`
		Error  *RPCError                  `json:"error"`
	}
	
	if err := json.NewDecoder(resp.Body).Decode(&rpcResponse); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}
	
	if rpcResponse.Error != nil {
		return nil, fmt.Errorf("RPC error: %s", rpcResponse.Error.Message)
	}
	
	if rpcResponse.Result == nil {
		return nil, fmt.Errorf("transaction not found")
	}
	
	return rpcResponse.Result, nil
}

// AnalyzeTransaction analyzes a Solana transaction and extracts wallet actions
func (tp *transactionProcessor) AnalyzeTransaction(tx *SolanaTransactionResponse) (*AnalyzedWalletAction, error) {
	// Determine platform from program IDs
	platform := tp.identifyPlatform(tx)
	
	// Extract wallet address (first signer)
	var walletAddress string
	if len(tx.Transaction.Message.AccountKeys) > 0 {
		walletAddress = tx.Transaction.Message.AccountKeys[0]
	}
	
	// Analyze token balance changes
	inputToken, outputToken, transactionType := tp.analyzeTokenBalanceChanges(
		tx.Meta.PreTokenBalances,
		tx.Meta.PostTokenBalances,
		walletAddress,
	)
	
	// Check transaction success
	success := tx.Meta.Err == nil
	
	action := &AnalyzedWalletAction{
		WalletAddress:   walletAddress,
		Platform:        platform,
		TransactionType: transactionType,
		InputToken:      inputToken,
		OutputToken:     outputToken,
		Signature:       tx.Transaction.Signatures[0],
		Slot:            tx.Slot,
		BlockTime:       time.Unix(tx.BlockTime, 0),
		LogMessages:     tx.Meta.LogMessages,
		Success:         success,
		Fee:             tx.Meta.Fee,
	}
	
	return action, nil
}

// IsRelevantTransaction checks if log messages indicate DEX activity
func (tp *transactionProcessor) IsRelevantTransaction(logs []string) bool {
	relevantKeywords := []string{
		"Program log: Instruction: Swap",
		"Program log: ray_log:",
		"Program log: instruction: Buy",
		"Program log: instruction: Sell",
		"Program JUP",
		"Program 675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8",
		"Program 6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P",
		"swap",
		"trade",
	}
	
	for _, log := range logs {
		logLower := strings.ToLower(log)
		for _, keyword := range relevantKeywords {
			if strings.Contains(logLower, strings.ToLower(keyword)) {
				return true
			}
		}
	}
	
	return false
}

// identifyPlatform identifies the DEX platform from transaction
func (tp *transactionProcessor) identifyPlatform(tx *SolanaTransactionResponse) string {
	// Check instructions for known program IDs
	for _, instruction := range tx.Transaction.Message.Instructions {
		if instruction.ProgramIdIndex < len(tx.Transaction.Message.AccountKeys) {
			programId := tx.Transaction.Message.AccountKeys[instruction.ProgramIdIndex]
			if platform, exists := tp.dexPrograms[programId]; exists {
				return platform
			}
		}
	}
	
	// Fallback: check log messages for platform indicators
	for _, log := range tx.Meta.LogMessages {
		if strings.Contains(log, "JUP") {
			return "Jupiter"
		}
		if strings.Contains(log, "ray_log") {
			return "Raydium"
		}
		if strings.Contains(log, "Pump") {
			return "Pump.fun"
		}
	}
	
	return "Unknown"
}

// analyzeTokenBalanceChanges analyzes pre/post token balances to determine swap details
func (tp *transactionProcessor) analyzeTokenBalanceChanges(
	preBalances, postBalances []TokenBalance,
	walletAddress string,
) (*TokenAmount, *TokenAmount, string) {
	
	// Create maps for easier comparison
	preMap := make(map[string]TokenBalance)
	postMap := make(map[string]TokenBalance)
	
	for _, balance := range preBalances {
		if balance.Owner == walletAddress {
			preMap[balance.Mint] = balance
		}
	}
	
	for _, balance := range postBalances {
		if balance.Owner == walletAddress {
			postMap[balance.Mint] = balance
		}
	}
	
	var inputToken, outputToken *TokenAmount
	
	// Find tokens with balance changes
	for mint, postBalance := range postMap {
		preBalance, hadBefore := preMap[mint]
		
		var preAmount, postAmount float64
		if hadBefore {
			preAmount = preBalance.UITokenAmount.UIAmount
		}
		postAmount = postBalance.UITokenAmount.UIAmount
		
		change := postAmount - preAmount
		
		if change > 0 {
			// Token increased - this is output
			outputToken = &TokenAmount{
				Mint:     mint,
				Amount:   change,
				Decimals: postBalance.UITokenAmount.Decimals,
			}
		} else if change < 0 {
			// Token decreased - this is input
			inputToken = &TokenAmount{
				Mint:     mint,
				Amount:   -change, // Make positive
				Decimals: postBalance.UITokenAmount.Decimals,
			}
		}
	}
	
	// Check for tokens that were completely spent
	for mint, preBalance := range preMap {
		if _, stillHas := postMap[mint]; !stillHas && preBalance.UITokenAmount.UIAmount > 0 {
			inputToken = &TokenAmount{
				Mint:     mint,
				Amount:   preBalance.UITokenAmount.UIAmount,
				Decimals: preBalance.UITokenAmount.Decimals,
			}
		}
	}
	
	// Determine transaction type
	transactionType := "swap"
	if inputToken != nil && outputToken != nil {
		// Check if SOL is involved
		solMint := "So11111111111111111111111111111111111111112" // Wrapped SOL
		if inputToken.Mint == solMint {
			transactionType = "buy"
		} else if outputToken.Mint == solMint {
			transactionType = "sell"
		}
	}
	
	// Enrich with token symbols
	tp.enrichTokenSymbols(inputToken, outputToken)
	
	return inputToken, outputToken, transactionType
}

// enrichTokenSymbols adds symbol information to tokens
func (tp *transactionProcessor) enrichTokenSymbols(tokens ...*TokenAmount) {
	for _, token := range tokens {
		if token == nil {
			continue
		}
		
		// Try to get token info from database
		if tokenInfo, err := tp.tokenRepo.GetByMintAddress(context.Background(), token.Mint); err == nil && tokenInfo != nil {
			token.Symbol = tokenInfo.Symbol
		} else {
			// Special case for SOL
			if token.Mint == "So11111111111111111111111111111111111111112" {
				token.Symbol = "SOL"
			}
		}
	}
}