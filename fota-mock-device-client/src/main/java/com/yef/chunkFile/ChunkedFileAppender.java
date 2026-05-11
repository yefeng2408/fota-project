package com.yef.chunkFile;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/5/11 23:06
 */
public class ChunkedFileAppender implements AutoCloseable {

    private final BufferedOutputStream bos;
    private int totalBytesWritten = 0;

    public ChunkedFileAppender(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        // 第二个参数 true 表示追加模式
        this.bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile(), true));
    }

    /**
     * 追加一个数据块到文件
     *
     * @param data 需要写入的数据块
     * @return 当前累计写入的总字节数
     */
    public int appendChunk(byte[] data) throws IOException {
        bos.write(data);
        totalBytesWritten += data.length;
        return totalBytesWritten;
    }

    /**
     * 强制将缓冲区数据刷新到磁盘
     */
    public void flush() throws IOException {
        bos.flush();
    }

    @Override
    public void close() throws Exception {
        if (bos != null) {
            bos.close();
        }
    }


}