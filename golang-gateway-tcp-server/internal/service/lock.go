package service

import (
	"context"
	"time"

	"github.com/go-redis/redis/v8"
)

const (
	DeviceUpgradeSessionLockKeyPrefix = "fota:upgrade:session-lock:"
	DeviceUpgradeActiveSetKey         = "fota:upgrade:session-lock:active"
	defaultLockExpire                 = 90 * time.Second
	UpgradeRuntimeKeyPrefix           = "fota:upgrade:runtime:"
)

var acquireLockScript = redis.NewScript(`
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
    return 1
end
if redis.call('get', KEYS[1]) == ARGV[1] then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 1
end
return 0
`)

var renewLockScript = redis.NewScript(`
if redis.call('get', KEYS[1]) == ARGV[1] then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 1
end
return 0
`)

var releaseLockScript = redis.NewScript(`
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
`)

type DeviceUpgradeLockService struct {
	redis *redis.Client
}

func NewDeviceUpgradeLockService(redisClient *redis.Client) *DeviceUpgradeLockService {
	return &DeviceUpgradeLockService{redis: redisClient}
}

func (s *DeviceUpgradeLockService) Acquire(ctx context.Context, imei, token string) bool {
	result, err := acquireLockScript.Run(ctx, s.redis, []string{s.lockKey(imei)}, token, defaultLockExpire.Milliseconds()).Int()
	return err == nil && result == 1
}

func (s *DeviceUpgradeLockService) Renew(ctx context.Context, imei, token string) bool {
	result, err := renewLockScript.Run(ctx, s.redis, []string{s.lockKey(imei)}, token, defaultLockExpire.Milliseconds()).Int()
	return err == nil && result == 1
}

func (s *DeviceUpgradeLockService) Release(ctx context.Context, imei, token string) bool {
	result, err := releaseLockScript.Run(ctx, s.redis, []string{s.lockKey(imei)}, token).Int()
	return err == nil && result == 1
}

func (s *DeviceUpgradeLockService) MarkActive(ctx context.Context, imei string) {
	_ = s.redis.SAdd(ctx, DeviceUpgradeActiveSetKey, imei).Err()
}

func (s *DeviceUpgradeLockService) ClearActive(ctx context.Context, imei string) {
	_ = s.redis.SRem(ctx, DeviceUpgradeActiveSetKey, imei).Err()
}

func (s *DeviceUpgradeLockService) RunRenewal(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			imeis, err := s.redis.SMembers(ctx, DeviceUpgradeActiveSetKey).Result()
			if err != nil {
				continue
			}
			for _, imei := range imeis {
				runtime, err := s.redis.HGetAll(ctx, UpgradeRuntimeKeyPrefix+imei).Result()
				if err != nil || len(runtime) == 0 {
					s.ClearActive(ctx, imei)
					continue
				}
				token := runtime["lockToken"]
				status := runtime["status"]
				if token == "" || token == "null" {
					s.ClearActive(ctx, imei)
					continue
				}
				if status == "UPGRADE_REQUESTED" || status == "UPGRADING" || status == "WAIT_RESULT" {
					s.Renew(ctx, imei, token)
				}
			}
		}
	}
}

func (s *DeviceUpgradeLockService) lockKey(imei string) string {
	return DeviceUpgradeSessionLockKeyPrefix + imei
}
