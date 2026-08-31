package service

import (
	"context"
	"fmt"
	"io"
	"log"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/minio/minio-go/v7"
)

type FirmwareCacheHolder struct {
	FirmwareID   int64
	BucketName   string
	ObjectName   string
	Bytes        []byte
	FileSize     int64
	ChunkSize    int32
	TotalPacket  int32
	MD5          string
	RefCount     atomic.Int32
	CreatedAt    int64
	LastAccessAt atomic.Int64
}

type loadResult struct {
	holder *FirmwareCacheHolder
	err    error
	ready  chan struct{}
}

type FirmwareCacheManager struct {
	mu       sync.Mutex
	cache    map[int64]*FirmwareCacheHolder
	inflight map[int64]*loadResult
	minio    *minio.Client
}

func NewFirmwareCacheManager(minioClient *minio.Client) *FirmwareCacheManager {
	return &FirmwareCacheManager{
		cache:    make(map[int64]*FirmwareCacheHolder),
		inflight: make(map[int64]*loadResult),
		minio:    minioClient,
	}
}

func (m *FirmwareCacheManager) Get(firmwareID int64) *FirmwareCacheHolder {
	m.mu.Lock()
	defer m.mu.Unlock()
	holder := m.cache[firmwareID]
	if holder != nil {
		holder.LastAccessAt.Store(time.Now().UnixMilli())
	}
	return holder
}

func (m *FirmwareCacheManager) Load(ctx context.Context, runtime map[string]string) (*FirmwareCacheHolder, error) {
	firmwareID, err := strconv.ParseInt(runtime["firmwareId"], 10, 64)
	if err != nil {
		return nil, err
	}

	m.mu.Lock()
	if holder := m.cache[firmwareID]; holder != nil && holder.Bytes != nil {
		holder.RefCount.Add(1)
		holder.LastAccessAt.Store(time.Now().UnixMilli())
		m.mu.Unlock()
		return holder, nil
	}
	if lr := m.inflight[firmwareID]; lr != nil {
		m.mu.Unlock()
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-lr.ready:
			if lr.holder != nil {
				lr.holder.RefCount.Add(1)
				lr.holder.LastAccessAt.Store(time.Now().UnixMilli())
			}
			return lr.holder, lr.err
		}
	}
	lr := &loadResult{ready: make(chan struct{})}
	m.inflight[firmwareID] = lr
	m.mu.Unlock()

	lr.holder, lr.err = m.loadObject(ctx, firmwareID, runtime)
	close(lr.ready)

	m.mu.Lock()
	delete(m.inflight, firmwareID)
	if lr.err == nil && lr.holder != nil {
		lr.holder.RefCount.Add(1)
		m.cache[firmwareID] = lr.holder
	}
	m.mu.Unlock()
	return lr.holder, lr.err
}

func (m *FirmwareCacheManager) loadObject(ctx context.Context, firmwareID int64, runtime map[string]string) (*FirmwareCacheHolder, error) {
	if m.minio == nil {
		return nil, fmt.Errorf("minio client is nil")
	}
	object, err := m.minio.GetObject(ctx, runtime["bucketName"], runtime["objectName"], minio.GetObjectOptions{})
	if err != nil {
		return nil, err
	}
	defer object.Close()
	data, err := io.ReadAll(object)
	if err != nil {
		return nil, err
	}
	fileSize, _ := strconv.ParseInt(runtime["fileSize"], 10, 64)
	chunkSize, _ := strconv.ParseInt(runtime["chunkSize"], 10, 32)
	totalPacket, _ := strconv.ParseInt(runtime["totalPacket"], 10, 32)
	now := time.Now().UnixMilli()
	holder := &FirmwareCacheHolder{
		FirmwareID:  firmwareID,
		BucketName:  runtime["bucketName"],
		ObjectName:  runtime["objectName"],
		Bytes:       data,
		FileSize:    fileSize,
		ChunkSize:   int32(chunkSize),
		TotalPacket: int32(totalPacket),
		MD5:         runtime["md5"],
		CreatedAt:   now,
	}
	holder.LastAccessAt.Store(now)
	return holder, nil
}

func (m *FirmwareCacheManager) Decrement(firmwareID int64) {
	holder := m.Get(firmwareID)
	if holder == nil {
		return
	}
	holder.RefCount.Add(-1)
	holder.LastAccessAt.Store(time.Now().UnixMilli())
}

func (m *FirmwareCacheManager) RunCleanup(ctx context.Context) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			m.cleanup()
		}
	}
}

func (m *FirmwareCacheManager) cleanup() {
	const idleRelease = 10 * time.Minute
	const forceRelease = 30 * time.Minute
	now := time.Now()
	m.mu.Lock()
	defer m.mu.Unlock()
	for id, holder := range m.cache {
		idle := now.Sub(time.UnixMilli(holder.LastAccessAt.Load()))
		ref := holder.RefCount.Load()
		if ref <= 0 && idle > idleRelease {
			delete(m.cache, id)
			log.Printf("固件缓存正常释放 firmwareId=%d refCount=%d idle=%s", id, ref, idle)
			continue
		}
		if idle > forceRelease {
			delete(m.cache, id)
			log.Printf("固件缓存强制释放 firmwareId=%d refCount=%d idle=%s", id, ref, idle)
		}
	}
}
