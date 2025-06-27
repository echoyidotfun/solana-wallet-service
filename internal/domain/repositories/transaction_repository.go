package repositories

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/wallet/service/internal/domain/models"
	"gorm.io/gorm"
)

type transactionRepository struct {
	db *gorm.DB
}

// NewTransactionRepository creates a new transaction repository instance
func NewTransactionRepository(db *gorm.DB) TransactionRepository {
	return &transactionRepository{db: db}
}

// Transaction methods
func (r *transactionRepository) Create(ctx context.Context, tx *models.SmartMoneyTransaction) error {
	return r.db.WithContext(ctx).Create(tx).Error
}

func (r *transactionRepository) GetByID(ctx context.Context, id uuid.UUID) (*models.SmartMoneyTransaction, error) {
	var tx models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&tx).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &tx, nil
}

func (r *transactionRepository) GetBySignature(ctx context.Context, signature string) (*models.SmartMoneyTransaction, error) {
	var tx models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).Where("signature = ?", signature).First(&tx).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &tx, nil
}

func (r *transactionRepository) GetByWallet(ctx context.Context, walletAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error) {
	var transactions []*models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).
		Where("wallet_address = ?", walletAddress).
		Order("block_time DESC").
		Limit(limit).
		Offset(offset).
		Find(&transactions).Error
	return transactions, err
}

func (r *transactionRepository) GetByToken(ctx context.Context, tokenAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error) {
	var transactions []*models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).
		Where("token_address = ?", tokenAddress).
		Order("block_time DESC").
		Limit(limit).
		Offset(offset).
		Find(&transactions).Error
	return transactions, err
}

func (r *transactionRepository) GetByWalletAndToken(ctx context.Context, walletAddress, tokenAddress string, limit, offset int) ([]*models.SmartMoneyTransaction, error) {
	var transactions []*models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).
		Where("wallet_address = ? AND token_address = ?", walletAddress, tokenAddress).
		Order("block_time DESC").
		Limit(limit).
		Offset(offset).
		Find(&transactions).Error
	return transactions, err
}

func (r *transactionRepository) List(ctx context.Context, limit, offset int) ([]*models.SmartMoneyTransaction, error) {
	var transactions []*models.SmartMoneyTransaction
	err := r.db.WithContext(ctx).
		Order("block_time DESC").
		Limit(limit).
		Offset(offset).
		Find(&transactions).Error
	return transactions, err
}

func (r *transactionRepository) Update(ctx context.Context, tx *models.SmartMoneyTransaction) error {
	return r.db.WithContext(ctx).Save(tx).Error
}

func (r *transactionRepository) Delete(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.SmartMoneyTransaction{}, id).Error
}

func (r *transactionRepository) GetRecentTransactions(ctx context.Context, hours int, limit int) ([]*models.SmartMoneyTransaction, error) {
	var transactions []*models.SmartMoneyTransaction
	since := time.Now().Add(-time.Duration(hours) * time.Hour)
	
	err := r.db.WithContext(ctx).
		Where("block_time >= ?", since).
		Order("block_time DESC").
		Limit(limit).
		Find(&transactions).Error
	return transactions, err
}

// Analysis methods
func (r *transactionRepository) CreateAnalysis(ctx context.Context, analysis *models.TransactionAnalysis) error {
	return r.db.WithContext(ctx).Create(analysis).Error
}

func (r *transactionRepository) GetAnalysisByTransactionID(ctx context.Context, transactionID uuid.UUID) ([]*models.TransactionAnalysis, error) {
	var analyses []*models.TransactionAnalysis
	err := r.db.WithContext(ctx).
		Where("transaction_id = ?", transactionID).
		Order("created_at DESC").
		Find(&analyses).Error
	return analyses, err
}

func (r *transactionRepository) UpdateAnalysis(ctx context.Context, analysis *models.TransactionAnalysis) error {
	return r.db.WithContext(ctx).Save(analysis).Error
}

func (r *transactionRepository) DeleteAnalysis(ctx context.Context, id uuid.UUID) error {
	return r.db.WithContext(ctx).Delete(&models.TransactionAnalysis{}, id).Error
}