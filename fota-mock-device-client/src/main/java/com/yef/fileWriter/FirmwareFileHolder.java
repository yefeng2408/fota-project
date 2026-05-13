package com.yef.fileWriter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FirmwareFileHolder {
    // key = taskId 保证每个升级任务唯一的文件
    private final Map<Long, FileChannel> channelMap = new ConcurrentHashMap<>();

    public FileChannel getOrCreateChannel(Long key, Path filePath) throws IOException {
        return channelMap.computeIfAbsent(key, k -> {
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // 以读写方式打开，若文件不存在则创建；注意追加模式
                return FileChannel.open(filePath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.READ);
            } catch (IOException e) {
                throw new RuntimeException("打开文件失败", e);
            }
        });
    }

    // 可选：在接收完所有分片后调用 closeAndRemove
    public void closeAndRemove(Long key) {
        FileChannel channel = channelMap.remove(key);
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
               log.error(e.getMessage(), e);
            }
        }
    }


}