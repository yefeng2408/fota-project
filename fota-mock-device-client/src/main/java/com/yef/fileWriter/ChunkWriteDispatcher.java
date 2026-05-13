package com.yef.fileWriter;

import com.yef.UpgradeFailErrorCode;
import com.yef.protocol.FotaProtocol;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/5/13 10:31
 */
@Component
public class ChunkWriteDispatcher {

    private static final String MOCK_DEV_RUNTIME_KEY = "mock-dev:upgrade:runtime:";
    private static final String UPGRADING_FIRMWARE_PATH = System.getProperty("java.io.tmpdir") + "mock-dev/firmware/";

    private static final int SHARD_COUNT = 8;
    private final ExecutorService[] shardExecutors = new ExecutorService[SHARD_COUNT];

    private final StringRedisTemplate redisTemplate;
    private final FirmwareFileHolder firmwareFileHolder;


    public ChunkWriteDispatcher(StringRedisTemplate redisTemplate, FirmwareFileHolder firmwareFileHolder) {
        this.redisTemplate = redisTemplate;
        this.firmwareFileHolder = firmwareFileHolder;

        for (int i = 0; i < SHARD_COUNT; i++) {
            int threadNum = i;
            shardExecutors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("mock-device-file-writer-" + threadNum);
                return t;

            });

        }
    }

    public void submit(ChannelHandlerContext ctx, FotaProtocol.UpgradePacketDTO packet) {

        long taskId = packet.taskId();
        int shardIndex = Math.floorMod(taskId, SHARD_COUNT);
        shardExecutors[shardIndex].submit(() -> {
            try {
                doWriteChunk(packet);
                ctx.executor().execute(() -> ctx.writeAndFlush(
                        new FotaProtocol.Ack(packet.imei(), packet.taskId(), packet.packetNo(), FotaProtocol.ACK_TYPE_PACKET)
                ));
            } catch (Exception e) {
                ctx.executor().execute(() -> {
                    ctx.writeAndFlush(new FotaProtocol.Fail(
                            packet.imei(),
                            packet.taskId(),
                            packet.packetNo(),
                            UpgradeFailErrorCode.FAIL_ERROR_DEVICE_WRITE
                    ));
                });
            }
        });
    }

    private void doWriteChunk(FotaProtocol.UpgradePacketDTO packet) throws IOException {
        //固件文件路径：/mock-dev/firmware/task/imei.bin
        Path path = Paths.get(UPGRADING_FIRMWARE_PATH + packet.taskId() + "/" + packet.imei() + ".bin");

        int chunkSize = packet.chunkSize();

        long offset = (packet.packetNo() - 1L) * chunkSize;

        try (ChunkedFileWriter writer = new ChunkedFileWriter(path)) {

            writer.writeChunk(offset, packet.chunkData());

        }

        // 文件写成功后，再更新 mock-device runtime checkpoint
        redisTemplate.opsForHash().putAll(
                MOCK_DEV_RUNTIME_KEY + packet.imei(),
                Map.of(
                        "taskId", String.valueOf(packet.taskId()),
                        "packetNo", String.valueOf(packet.packetNo()),
                        "offset", String.valueOf(offset + packet.chunkData().length),
                        "status", "UPGRADING",
                        "lastWriteTime", String.valueOf(System.currentTimeMillis())
                )

        );

    }

}