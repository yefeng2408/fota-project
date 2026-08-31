package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	ServerPort int
	TCPPort    int

	RedisAddr     string
	RedisPassword string
	RedisDB       int

	MinioEndpoint  string
	MinioAccessKey string
	MinioSecretKey string
	MinioSecure    bool

	RocketMQNameServer string
	RocketMQGroup      string
	RocketMQTopic      string

	GatewayInstanceID         string
	GatewayInstanceHTTPURL    string
	GatewayInstanceTCPHost    string
	GatewayInstanceWeight     int
	GatewayInstanceTTL        time.Duration
	GatewayHeartbeatInterval  time.Duration
	GatewayDeviceOnlineTTL    time.Duration
	UpgradeLockRenewInterval  time.Duration
	DeviceOnlineCleanupPeriod time.Duration
}

func Load() Config {
	return Config{
		ServerPort: getInt("SERVER_PORT", 8081),
		TCPPort:    getInt("NETTY_SERVER_PORT", getInt("TCP_SERVER_PORT", 7611)),

		RedisAddr:     getString("REDIS_ADDR", getString("SPRING_REDIS_HOST", "localhost")+":"+getString("SPRING_REDIS_PORT", "6379")),
		RedisPassword: getString("REDIS_PASSWORD", ""),
		RedisDB:       getInt("REDIS_DB", getInt("SPRING_REDIS_DATABASE", 0)),

		MinioEndpoint:  trimHTTPPrefix(getString("MINIO_ENDPOINT", "http://127.0.0.1:9000")),
		MinioAccessKey: getString("MINIO_ACCESS_KEY", "minioadmin"),
		MinioSecretKey: getString("MINIO_SECRET_KEY", "minioadmin"),
		MinioSecure:    getBool("MINIO_SECURE", false),

		RocketMQNameServer: getString("ROCKETMQ_NAME_SERVER", "localhost:9876"),
		RocketMQGroup:      getString("ROCKETMQ_PRODUCER_GROUP", "fota-gateway-producer-group"),
		RocketMQTopic:      getString("ROCKETMQ_TOPIC_UPGRADE_EVENT", "FOTA_UPGRADE_EVENT_TOPIC"),

		GatewayInstanceID:         getString("GATEWAY_INSTANCE_ID", ""),
		GatewayInstanceHTTPURL:    getString("GATEWAY_INSTANCE_HTTP_URL", ""),
		GatewayInstanceTCPHost:    getString("GATEWAY_INSTANCE_TCP_HOST", ""),
		GatewayInstanceWeight:     getInt("GATEWAY_INSTANCE_WEIGHT", 100),
		GatewayInstanceTTL:        getDurationSeconds("GATEWAY_INSTANCE_TTL_SECONDS", 30),
		GatewayHeartbeatInterval:  getDurationMillis("GATEWAY_INSTANCE_HEARTBEAT_INTERVAL_MS", 10000),
		GatewayDeviceOnlineTTL:    getDurationSeconds("GATEWAY_DEVICE_ONLINE_TTL_SECONDS", 240),
		UpgradeLockRenewInterval:  getDurationMillis("UPGRADE_LOCK_RENEW_INTERVAL_MS", 30000),
		DeviceOnlineCleanupPeriod: getDurationMillis("DEVICE_ONLINE_CLEANUP_INTERVAL_MS", 30000),
	}
}

func getString(key string, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return fallback
}

func getInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getBool(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getDurationSeconds(key string, fallback int) time.Duration {
	return time.Duration(getInt(key, fallback)) * time.Second
}

func getDurationMillis(key string, fallback int) time.Duration {
	return time.Duration(getInt(key, fallback)) * time.Millisecond
}

func trimHTTPPrefix(value string) string {
	value = strings.TrimPrefix(value, "http://")
	value = strings.TrimPrefix(value, "https://")
	return value
}
