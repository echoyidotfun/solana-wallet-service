package repositories

import "gorm.io/gorm"

// Repositories holds all repository instances
type Repositories struct {
	Token       TokenRepository
	Room        RoomRepository
	Transaction TransactionRepository
	Trader      TraderRepository
}

// NewRepositories creates and returns all repository instances
func NewRepositories(db *gorm.DB) *Repositories {
	return &Repositories{
		Token:       NewTokenRepository(db),
		Room:        NewRoomRepository(db),
		Transaction: NewTransactionRepository(db),
		Trader:      NewTraderRepository(db),
	}
}