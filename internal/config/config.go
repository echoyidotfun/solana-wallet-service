package config

import (
	"time"

	"github.com/spf13/viper"
)

type Config struct {
	Server       ServerConfig       `mapstructure:"server"`
	Database     DatabaseConfig     `mapstructure:"database"`
	Redis        RedisConfig        `mapstructure:"redis"`
	Log          LogConfig          `mapstructure:"log"`
	ExternalAPIs ExternalAPIsConfig `mapstructure:"external_apis"`
	WorkerPool   WorkerPoolConfig   `mapstructure:"worker_pool"`
	SyncScheduler SyncSchedulerConfig `mapstructure:"sync_scheduler"`
	WebSocket    WebSocketConfig    `mapstructure:"websocket"`
	Room         RoomConfig         `mapstructure:"room"`
	RateLimit    RateLimitConfig    `mapstructure:"rate_limit"`
	Metrics      MetricsConfig      `mapstructure:"metrics"`
}

type ServerConfig struct {
	Port           string        `mapstructure:"port"`
	Mode           string        `mapstructure:"mode"`
	ReadTimeout    time.Duration `mapstructure:"read_timeout"`
	WriteTimeout   time.Duration `mapstructure:"write_timeout"`
	MaxHeaderBytes int           `mapstructure:"max_header_bytes"`
}

type DatabaseConfig struct {
	Host            string        `mapstructure:"host"`
	Port            int           `mapstructure:"port"`
	User            string        `mapstructure:"user"`
	Password        string        `mapstructure:"password"`
	DBName          string        `mapstructure:"dbname"`
	SSLMode         string        `mapstructure:"sslmode"`
	TimeZone        string        `mapstructure:"timezone"`
	MaxIdleConns    int           `mapstructure:"max_idle_conns"`
	MaxOpenConns    int           `mapstructure:"max_open_conns"`
	ConnMaxLifetime time.Duration `mapstructure:"conn_max_lifetime"`
}

type RedisConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
	PoolSize int    `mapstructure:"pool_size"`
}

type LogConfig struct {
	Level      string `mapstructure:"level"`
	Format     string `mapstructure:"format"`
	Output     string `mapstructure:"output"`
	MaxSize    int    `mapstructure:"max_size"`
	MaxBackups int    `mapstructure:"max_backups"`
	MaxAge     int    `mapstructure:"max_age"`
}

type ExternalAPIsConfig struct {
	OpenAI       OpenAIConfig       `mapstructure:"openai"`
	QuickNode    QuickNodeConfig    `mapstructure:"quicknode"`
	SolanaTracker SolanaTrackerConfig `mapstructure:"solana_tracker"`
	Helius       HeliusConfig       `mapstructure:"helius"`
}

type OpenAIConfig struct {
	BaseURL string        `mapstructure:"base_url"`
	APIKey  string        `mapstructure:"api_key"`
	Model   string        `mapstructure:"model"`
	Timeout time.Duration `mapstructure:"timeout"`
}

type QuickNodeConfig struct {
	HTTPUrl string        `mapstructure:"http_url"`
	WSSUrl  string        `mapstructure:"wss_url"`
	APIKey  string        `mapstructure:"api_key"`
	Timeout time.Duration `mapstructure:"timeout"`
}

type SolanaTrackerConfig struct {
	BaseURL string        `mapstructure:"base_url"`
	APIKey  string        `mapstructure:"api_key"`
	Timeout time.Duration `mapstructure:"timeout"`
}

type HeliusConfig struct {
	HTTPUrl string        `mapstructure:"http_url"`
	WSSUrl  string        `mapstructure:"wss_url"`
	APIKey  string        `mapstructure:"api_key"`
	Timeout time.Duration `mapstructure:"timeout"`
}

type WorkerPoolConfig struct {
	MaxWorkers   int `mapstructure:"max_workers"`
	JobQueueSize int `mapstructure:"job_queue_size"`
}

type SyncSchedulerConfig struct {
	UnifiedSyncInterval      time.Duration `mapstructure:"unified_sync_interval"`
	TrendingTokensInterval   time.Duration `mapstructure:"trending_tokens_interval"`
	VolumeTokensInterval     time.Duration `mapstructure:"volume_tokens_interval"`
	LatestTokensInterval     time.Duration `mapstructure:"latest_tokens_interval"`
	APICallInterval          time.Duration `mapstructure:"api_call_interval"`
}

type WebSocketConfig struct {
	ReadBufferSize   int           `mapstructure:"read_buffer_size"`
	WriteBufferSize  int           `mapstructure:"write_buffer_size"`
	HeartbeatInterval time.Duration `mapstructure:"heartbeat_interval"`
	PongWait         time.Duration `mapstructure:"pong_wait"`
	PingPeriod       time.Duration `mapstructure:"ping_period"`
	MaxMessageSize   int64         `mapstructure:"max_message_size"`
}

type RoomConfig struct {
	DefaultRecycleHours int           `mapstructure:"default_recycle_hours"`
	MaxMembers          int           `mapstructure:"max_members"`
	CleanupInterval     time.Duration `mapstructure:"cleanup_interval"`
}

type RateLimitConfig struct {
	RequestsPerSecond float64 `mapstructure:"requests_per_second"`
	Burst             int     `mapstructure:"burst"`
}

type MetricsConfig struct {
	Enabled bool   `mapstructure:"enabled"`
	Path    string `mapstructure:"path"`
}

var globalConfig *Config

func Load(configPath string) (*Config, error) {
	viper.SetConfigFile(configPath)
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		return nil, err
	}

	config := &Config{}
	if err := viper.Unmarshal(config); err != nil {
		return nil, err
	}

	globalConfig = config
	return config, nil
}

func Get() *Config {
	return globalConfig
}