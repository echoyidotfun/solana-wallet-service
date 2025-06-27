package services

import (
	"github.com/sirupsen/logrus"
	"github.com/wallet/service/internal/domain/repositories"
	"github.com/wallet/service/internal/services/room"
)

// Services holds all service instances
type Services struct {
	Room room.RoomService
}

// NewServices creates and returns all service instances
func NewServices(repos *repositories.Repositories, logger *logrus.Logger) *Services {
	return &Services{
		Room: room.NewRoomService(repos.Room, logger),
	}
}