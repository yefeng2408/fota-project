package service

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/go-redis/redis/v8"
)

const (
	deviceCacheKeyPrefix = "fota:device:"
	deviceOnlineZSetKey  = "fota:device:online:zset"
	onlineWindow         = 60 * time.Second
)

type DeviceKeepAliveService struct {
	redis *redis.Client
}

func NewDeviceKeepAliveService(redisClient *redis.Client) *DeviceKeepAliveService {
	return &DeviceKeepAliveService{redis: redisClient}
}

func (s *DeviceKeepAliveService) GetDeviceID(ctx context.Context, imei string) (int64, error) {
	id, err := s.redis.HGet(ctx, deviceCacheKeyPrefix+imei, "id").Result()
	if err == redis.Nil || id == "" {
		return 0, fmt.Errorf("设备不存在。请检查设备imei:%s是否存在", imei)
	}
	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(id, 10, 64)
}

func (s *DeviceKeepAliveService) OnDeviceFirstConnect(ctx context.Context, deviceID int64) {
	if deviceID == 0 {
		return
	}
	_ = s.redis.ZAdd(ctx, deviceOnlineZSetKey, &redis.Z{
		Score:  float64(time.Now().UnixMilli()),
		Member: strconv.FormatInt(deviceID, 10),
	}).Err()
}

func (s *DeviceKeepAliveService) RefreshHeartbeat(ctx context.Context, deviceID int64) {
	s.OnDeviceFirstConnect(ctx, deviceID)
}

func (s *DeviceKeepAliveService) OnDeviceOffline(deviceID int64) {
	if deviceID == 0 {
		return
	}
	_ = s.redis.ZRem(context.Background(), deviceOnlineZSetKey, strconv.FormatInt(deviceID, 10)).Err()
}

func (s *DeviceKeepAliveService) RunCleanup(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			threshold := time.Now().Add(-onlineWindow).UnixMilli()
			_ = s.redis.ZRemRangeByScore(ctx, deviceOnlineZSetKey, "0", strconv.FormatInt(threshold, 10)).Err()
		}
	}
}
