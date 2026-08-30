package registry

import (
	"context"
	"strconv"
	"time"

	"github.com/go-redis/redis/v8"

	"golang-gateway-tcp-server/internal/config"
	"golang-gateway-tcp-server/internal/session"
)

const DeviceOnlineKeyPrefix = "fota:device:online:"

var deleteIfSessionMatches = redis.NewScript(`
if redis.call('hget', KEYS[1], 'sessionId') == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
`)

type DeviceOnlineRegistry struct {
	redis   *redis.Client
	gateway *GatewayRegistry
	ttl     time.Duration
}

func NewDeviceOnlineRegistry(redisClient *redis.Client, gateway *GatewayRegistry, cfg config.Config) *DeviceOnlineRegistry {
	return &DeviceOnlineRegistry{redis: redisClient, gateway: gateway, ttl: cfg.GatewayDeviceOnlineTTL}
}

func (r *DeviceOnlineRegistry) Register(ctx context.Context, s *session.DeviceSession, currentFirmwareVersion, deviceType, remoteAddress string) error {
	if s == nil || s.IMEI == "" || s.SessionID == "" {
		return nil
	}
	now := time.Now().UnixMilli()
	fields := map[string]interface{}{
		"imei":                   s.IMEI,
		"instanceId":             r.gateway.InstanceID(),
		"sessionId":              s.SessionID,
		"channelId":              s.SessionID,
		"connectedAt":            strconv.FormatInt(s.ConnectTime, 10),
		"lastSeenAt":             strconv.FormatInt(now, 10),
		"deviceType":             deviceType,
		"currentFirmwareVersion": currentFirmwareVersion,
		"remoteAddress":          remoteAddress,
	}
	key := DeviceOnlineKeyPrefix + s.IMEI
	if err := r.redis.HSet(ctx, key, fields).Err(); err != nil {
		return err
	}
	return r.redis.Expire(ctx, key, r.ttl).Err()
}

func (r *DeviceOnlineRegistry) Touch(ctx context.Context, s *session.DeviceSession) {
	if s == nil || s.IMEI == "" || s.SessionID == "" {
		return
	}
	key := DeviceOnlineKeyPrefix + s.IMEI
	_ = r.redis.HSet(ctx, key, "lastSeenAt", strconv.FormatInt(time.Now().UnixMilli(), 10)).Err()
	_ = r.redis.Expire(ctx, key, r.ttl).Err()
}

func (r *DeviceOnlineRegistry) RemoveIfSessionMatches(ctx context.Context, imei, sessionID string) bool {
	if imei == "" || sessionID == "" {
		return false
	}
	result, err := deleteIfSessionMatches.Run(ctx, r.redis, []string{DeviceOnlineKeyPrefix + imei}, sessionID).Int()
	return err == nil && result == 1
}
