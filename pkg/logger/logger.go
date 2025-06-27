package logger

import (
	"os"
	"path/filepath"

	"github.com/emiyaio/solana-wallet-service/internal/config"
	"github.com/sirupsen/logrus"
)

var globalLogger *logrus.Logger

func InitLogger(cfg config.LogConfig) (*logrus.Logger, error) {
	logger := logrus.New()

	// Set log level
	level, err := logrus.ParseLevel(cfg.Level)
	if err != nil {
		return nil, err
	}
	logger.SetLevel(level)

	// Set log format
	if cfg.Format == "json" {
		logger.SetFormatter(&logrus.JSONFormatter{
			TimestampFormat: "2006-01-02 15:04:05",
		})
	} else {
		logger.SetFormatter(&logrus.TextFormatter{
			FullTimestamp:   true,
			TimestampFormat: "2006-01-02 15:04:05",
		})
	}

	// Set output
	if cfg.Output != "" {
		// Create log directory if it doesn't exist
		logDir := filepath.Dir(cfg.Output)
		if err := os.MkdirAll(logDir, 0755); err != nil {
			return nil, err
		}

		file, err := os.OpenFile(cfg.Output, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
		if err != nil {
			return nil, err
		}
		logger.SetOutput(file)
	}

	globalLogger = logger
	return logger, nil
}

func GetLogger() *logrus.Logger {
	if globalLogger == nil {
		globalLogger = logrus.New()
	}
	return globalLogger
}

func Info(args ...interface{}) {
	GetLogger().Info(args...)
}

func Debug(args ...interface{}) {
	GetLogger().Debug(args...)
}

func Warn(args ...interface{}) {
	GetLogger().Warn(args...)
}

func Error(args ...interface{}) {
	GetLogger().Error(args...)
}

func Fatal(args ...interface{}) {
	GetLogger().Fatal(args...)
}

func Infof(format string, args ...interface{}) {
	GetLogger().Infof(format, args...)
}

func Debugf(format string, args ...interface{}) {
	GetLogger().Debugf(format, args...)
}

func Warnf(format string, args ...interface{}) {
	GetLogger().Warnf(format, args...)
}

func Errorf(format string, args ...interface{}) {
	GetLogger().Errorf(format, args...)
}

func Fatalf(format string, args ...interface{}) {
	GetLogger().Fatalf(format, args...)
}