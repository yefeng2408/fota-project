# golang-gateway-tcp-server

Go rewrite of `fota-gateway-tcp-server`. It keeps the Java gateway's external contracts:

- TCP FOTA protocol on port `7611` by default.
- Internal HTTP APIs:
  - `POST /internal/device/upgrade-request`
  - `POST /internal/device/cancel-request`
- Redis routing keys:
  - `fota:gateway:instance:{instanceId}`
  - `fota:gateway:instances`
  - `fota:device:online:{imei}`
  - `fota:device:online:zset`
  - `fota:upgrade:runtime:{imei}`
  - `fota:upgrade:session-lock:{imei}`
- MinIO firmware preloading by `bucketName/objectName`.
- RocketMQ upgrade events on `FOTA_UPGRADE_EVENT_TOPIC` with the same event type/tag names.

## Run

```bash
go test ./...
go run ./cmd/gateway
```

## Environment

The defaults mirror `fota-gateway-tcp-server/src/main/resources/application.yml`.

| Variable | Default |
| --- | --- |
| `SERVER_PORT` | `8081` |
| `TCP_SERVER_PORT` / `NETTY_SERVER_PORT` | `7611` |
| `REDIS_ADDR` | `localhost:6379` |
| `REDIS_PASSWORD` | empty |
| `REDIS_DB` | `0` |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` |
| `MINIO_ACCESS_KEY` | `minioadmin` |
| `MINIO_SECRET_KEY` | `minioadmin` |
| `ROCKETMQ_NAME_SERVER` | `localhost:9876` |
| `ROCKETMQ_PRODUCER_GROUP` | `fota-gateway-producer-group` |
| `ROCKETMQ_TOPIC_UPGRADE_EVENT` | `FOTA_UPGRADE_EVENT_TOPIC` |
| `GATEWAY_INSTANCE_ID` | generated |
| `GATEWAY_INSTANCE_HTTP_URL` | generated from hostname and `SERVER_PORT` |
| `GATEWAY_INSTANCE_TCP_HOST` | hostname |
| `GATEWAY_INSTANCE_WEIGHT` | `100` |
| `GATEWAY_INSTANCE_TTL_SECONDS` | `30` |
| `GATEWAY_INSTANCE_HEARTBEAT_INTERVAL_MS` | `10000` |
| `GATEWAY_DEVICE_ONLINE_TTL_SECONDS` | `240` |
| `UPGRADE_LOCK_RENEW_INTERVAL_MS` | `30000` |

## Protocol Compatibility

The Go protocol codec matches the Java frame layout:

```text
head(1) version(1) bodyLength(4) imei(8) timestamp(8) seqId(2) messageType(1) body(N) crc16(2) tail(1)
```

CRC16 covers `version` through the final body byte, using the same `0xA001` polynomial and big-endian field serialization as the Java gateway.
