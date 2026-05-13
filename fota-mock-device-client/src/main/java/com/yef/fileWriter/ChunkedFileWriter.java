/*
package com.yef.fileWriter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

*/
/**
 * @description:
 * @author: 叶丰
 * @date: 2026/5/11 23:06
 *//*

public class ChunkedFileWriter implements AutoCloseable {

    private final RandomAccessFile raf;

    public ChunkedFileWriter(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        this.raf = new RandomAccessFile(filePath.toFile(), "rw");

    }

    public void writeChunk(long offset, byte[] data) throws IOException {
        raf.seek(offset);
        raf.write(data);
        //raf.getFD().sync();
    }


    @Override
    public void close() throws Exception {
        if (raf != null) {
            raf.close();
        }
    }


}*/
