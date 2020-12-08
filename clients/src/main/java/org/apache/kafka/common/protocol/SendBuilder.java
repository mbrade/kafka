/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.protocol;

import org.apache.kafka.common.network.ByteBufferSend;
import org.apache.kafka.common.network.Send;
import org.apache.kafka.common.record.BaseRecords;
import org.apache.kafka.common.record.MemoryRecords;
import org.apache.kafka.common.record.MultiRecordsSend;
import org.apache.kafka.common.requests.RequestHeader;
import org.apache.kafka.common.requests.RequestUtils;
import org.apache.kafka.common.requests.ResponseHeader;
import org.apache.kafka.common.utils.ByteUtils;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * This class provides a way to build {@link Send} objects for network transmission
 * from generated {@link org.apache.kafka.common.protocol.ApiMessage} types without
 * allocating new space for "zero-copy" fields (see {@link #writeByteBuffer(ByteBuffer)}
 * and {@link #writeRecords(BaseRecords)}).
 *
 * See {@link org.apache.kafka.common.requests.EnvelopeRequest#toSend(String, RequestHeader)}
 * for example usage.
 */
public class SendBuilder implements Writable {
    private final ByteBuffer buffer;
    private final String destinationId;

    private final Queue<Send> sends = new ArrayDeque<>(1);
    private long sizeOfSends = 0;

    private final List<ByteBuffer> buffers = new ArrayList<>();
    private long sizeOfBuffers = 0;

    SendBuilder(String destinationId, int size) {
        this.destinationId = destinationId;
        this.buffer = ByteBuffer.allocate(size);
        this.buffer.mark();
    }

    @Override
    public void writeByte(byte val) {
        buffer.put(val);
    }

    @Override
    public void writeShort(short val) {
        buffer.putShort(val);
    }

    @Override
    public void writeInt(int val) {
        buffer.putInt(val);
    }

    @Override
    public void writeLong(long val) {
        buffer.putLong(val);
    }

    @Override
    public void writeDouble(double val) {
        buffer.putDouble(val);
    }

    @Override
    public void writeByteArray(byte[] arr) {
        buffer.put(arr);
    }

    @Override
    public void writeUnsignedVarint(int i) {
        ByteUtils.writeUnsignedVarint(i, buffer);
    }

    /**
     * Write a byte buffer. The reference to the underlying buffer will
     * be retained in the result of {@link #build()}.
     *
     * @param buf the buffer to write
     */
    @Override
    public void writeByteBuffer(ByteBuffer buf) {
        flushPendingBuffer();
        addBuffer(buf.duplicate());
    }

    @Override
    public void writeVarint(int i) {
        ByteUtils.writeVarint(i, buffer);
    }

    @Override
    public void writeVarlong(long i) {
        ByteUtils.writeVarlong(i, buffer);
    }

    private void addBuffer(ByteBuffer buffer) {
        buffers.add(buffer);
        sizeOfBuffers += buffer.remaining();
    }

    private void addSend(Send send) {
        sends.add(send);
        sizeOfSends += send.size();
    }

    private void clearBuffers() {
        buffers.clear();
        sizeOfBuffers = 0;
    }

    /**
     * Write a record set. The underlying record data will be retained
     * in the result of {@link #build()}. See {@link BaseRecords#toSend(String)}.
     *
     * @param records the records to write
     */
    @Override
    public void writeRecords(BaseRecords records) {
        if (records instanceof MemoryRecords) {
            flushPendingBuffer();
            addBuffer(((MemoryRecords) records).buffer());
        } else {
            flushPendingSend();
            addSend(records.toSend(destinationId));
        }
    }

    private void flushPendingSend() {
        flushPendingBuffer();
        if (!buffers.isEmpty()) {
            ByteBuffer[] byteBufferArray = buffers.toArray(new ByteBuffer[0]);
            addSend(new ByteBufferSend(destinationId, byteBufferArray, sizeOfBuffers));
            clearBuffers();
        }
    }

    private void flushPendingBuffer() {
        int latestPosition = buffer.position();
        buffer.reset();

        if (latestPosition > buffer.position()) {
            buffer.limit(latestPosition);
            addBuffer(buffer.slice());

            buffer.position(latestPosition);
            buffer.limit(buffer.capacity());
            buffer.mark();
        }
    }

    public Send build() {
        flushPendingSend();

        if (sends.size() == 1) {
            return sends.poll();
        } else {
            return new MultiRecordsSend(destinationId, sends, sizeOfSends);
        }
    }

    public static Send buildRequestSend(
        String destination,
        RequestHeader header,
        Message apiRequest
    ) {
        return buildSend(
            destination,
            header.data(),
            header.headerVersion(),
            apiRequest,
            header.apiVersion()
        );
    }

    public static Send buildResponseSend(
        String destination,
        ResponseHeader header,
        Message apiResponse,
        short apiVersion
    ) {
        return buildSend(
            destination,
            header.data(),
            header.headerVersion(),
            apiResponse,
            apiVersion
        );
    }

    private static Send buildSend(
        String destination,
        Message header,
        short headerVersion,
        Message apiMessage,
        short apiVersion
    ) {
        ObjectSerializationCache serializationCache = new ObjectSerializationCache();
        MessageSizeAccumulator messageSize = RequestUtils.size(serializationCache, header, headerVersion, apiMessage, apiVersion);

        int totalSize = messageSize.totalSize();
        int sizeExcludingZeroCopyFields = totalSize - messageSize.zeroCopySize();

        SendBuilder builder = new SendBuilder(destination, sizeExcludingZeroCopyFields + 4);
        builder.writeInt(totalSize);
        header.write(builder, serializationCache, headerVersion);
        apiMessage.write(builder, serializationCache, apiVersion);
        return builder.build();
    }

}
