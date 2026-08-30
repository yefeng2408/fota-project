package registry

import (
	"context"
	"fmt"
	"log"
	"os"
	"strconv"
	"time"

	"github.com/go-redis/redis/v8"

	"golang-gateway-tcp-server/internal/config"
	"golang-gateway-tcp-server/internal/session"
)

const (
	GatewayInstancesKey      = "fota:gateway:instances"
	GatewayInstanceKeyPrefix = "fota:gateway:instance:"
)

type GatewayRegistry struct {
	redis      *redis.Client
	sessions   *session.Manager
	cfg        config.Config
	instanceID string
	httpURL    string
	tcpHost    string
	startedAt  int64
}

func NewGatewayRegistry(redisClient *redis.Client, sessions *session.Manager, cfg config.Config) *GatewayRegistry {
	return &GatewayRegistry{redis: redisClient, sessions: sessions, cfg: cfg}
}

func (g *GatewayRegistry) Init(ctx context.Context) error {
	host, err := os.Hostname()
	if err != nil || host == "" {
		host = fmt.Sprintf("unknown-%d", time.Now().UnixMilli())
	}
	g.startedAt = time.Now().UnixMilli()
	g.instanceID = g.cfg.GatewayInstanceID
	if g.instanceID == "" {
		g.instanceID = fmt.Sprintf("gateway-%s-%d-%d", host, g.cfg.TCPPort, g.startedAt)
	}
	g.tcpHost = g.cfg.GatewayInstanceTCPHost
	if g.tcpHost == "" {
		g.tcpHost = host
	}
	g.httpURL = g.cfg.GatewayInstanceHTTPURL
	if g.httpURL == "" {
		g.httpURL = fmt.Sprintf("http://%s:%d", host, g.cfg.ServerPort)
	}
	if err := g.RegisterHeartbeat(ctx); err != nil {
		return err
	}
	log.Printf("gateway instance registered, instanceId=%s httpUrl=%s tcpHost=%s tcpPort=%d", g.instanceID, g.httpURL, g.tcpHost, g.cfg.TCPPort)
	return nil
}

func (g *GatewayRegistry) RegisterHeartbeat(ctx context.Context) error {
	if g.instanceID == "" {
		return nil
	}
	now := time.Now().UnixMilli()
	key := GatewayInstanceKeyPrefix + g.instanceID
	fields := map[string]interface{}{
		"instanceId":        g.instanceID,
		"httpUrl":           g.httpURL,
		"tcpHost":           g.tcpHost,
		"tcpPort":           strconv.Itoa(g.cfg.TCPPort),
		"status":            "UP",
		"weight":            strconv.Itoa(g.cfg.GatewayInstanceWeight),
		"activeConnections": strconv.Itoa(g.sessions.OnlineSessionCount()),
		"startedAt":         strconv.FormatInt(g.startedAt, 10),
		"lastHeartbeatAt":   strconv.FormatInt(now, 10),
	}
	if err := g.redis.HSet(ctx, key, fields).Err(); err != nil {
		return err
	}
	if err := g.redis.Expire(ctx, key, g.cfg.GatewayInstanceTTL).Err(); err != nil {
		return err
	}
	return g.redis.SAdd(ctx, GatewayInstancesKey, g.instanceID).Err()
}

func (g *GatewayRegistry) Run(ctx context.Context) {
	ticker := time.NewTicker(g.cfg.GatewayHeartbeatInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			_ = g.Destroy(context.Background())
			return
		case <-ticker.C:
			if err := g.RegisterHeartbeat(ctx); err != nil {
				log.Printf("gateway heartbeat failed: %v", err)
			}
		}
	}
}

func (g *GatewayRegistry) Destroy(ctx context.Context) error {
	if g.instanceID == "" {
		return nil
	}
	if err := g.redis.Del(ctx, GatewayInstanceKeyPrefix+g.instanceID).Err(); err != nil {
		return err
	}
	return g.redis.SRem(ctx, GatewayInstancesKey, g.instanceID).Err()
}

func (g *GatewayRegistry) InstanceID() string {
	return g.instanceID
}
