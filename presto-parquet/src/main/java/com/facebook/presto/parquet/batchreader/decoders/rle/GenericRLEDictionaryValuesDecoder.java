/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.presto.parquet.batchreader.decoders.rle;

import org.apache.parquet.io.ParquetDecodingException;
import org.openjdk.jol.info.ClassLayout;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.facebook.presto.parquet.batchreader.decoders.rle.GenericRLEDictionaryValuesDecoder.Mode.PACKED;
import static com.facebook.presto.parquet.batchreader.decoders.rle.GenericRLEDictionaryValuesDecoder.Mode.RLE;
import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.slice.SizeOf.sizeOf;
import static java.lang.Math.ceil;
import static org.apache.parquet.bytes.BytesUtils.readIntLittleEndianPaddedOnBitWidth;
import static org.apache.parquet.bytes.BytesUtils.readUnsignedVarInt;

public abstract class GenericRLEDictionaryValuesDecoder
{
    private static final int INSTANCE_SIZE = ClassLayout.parseClass(GenericRLEDictionaryValuesDecoder.class).instanceSize();

    private final boolean rleOnlyMode;
    private final int bitWidth;
    private final BytePacker packer;
    private final InputStream inputStream;

    protected Mode mode;
    protected int currentCount;
    protected int currentValue;
    protected int[] currentBuffer;

    public GenericRLEDictionaryValuesDecoder(int valueCount, int bitWidth, InputStream inputStream)
    {
        checkArgument(bitWidth >= 0 && bitWidth <= 32, "bitWidth must be >= 0 and <= 32");
        this.bitWidth = bitWidth;
        if (bitWidth != 0) {
            this.packer = Packer.LITTLE_ENDIAN.newBytePacker(bitWidth);
            this.inputStream = inputStream;
            this.rleOnlyMode = false;
        }
        else {
            this.rleOnlyMode = true;
            this.packer = null;
            this.inputStream = null;
            this.mode = RLE;
            this.currentValue = 0;
            this.currentCount = valueCount;
        }
    }

    public GenericRLEDictionaryValuesDecoder(int rleValue, int rleValueCount)
    {
        this.rleOnlyMode = true;
        this.bitWidth = 0;
        this.packer = null;
        this.inputStream = null;
        this.mode = RLE;
        this.currentValue = rleValue;
        this.currentCount = rleValueCount;
    }

    public long getRetainedSizeInBytes()
    {
        return INSTANCE_SIZE + sizeOf(currentBuffer);
    }

    protected boolean decode() throws IOException
    {
        if (this instanceof BooleanRLEValuesDecoder) {
            // Boolean RLE specific logic
            if (inputStream.available() <= 0) {
                currentCount = 0;
                return false;
            }

            int header = readUnsignedVarInt(inputStream);
            mode = RLE; // Boolean RLE is always RLE
            currentValue = (header & 1) == 1 ? 1 : 0;
            currentCount = header >>> 1;
            return true;
        }

        if (rleOnlyMode) {
            // for RLE only mode there is nothing more to read
            return false;
        }

        if (inputStream.available() <= 0) {
            currentCount = 0;
            return false;
        }

        int header = readUnsignedVarInt(inputStream);
        mode = (header & 1) == 0 ? RLE : PACKED;
        switch (mode) {
            case RLE:
                currentCount = header >>> 1;
                currentValue = readIntLittleEndianPaddedOnBitWidth(inputStream, bitWidth);
                return true;
            case PACKED:
                int numGroups = header >>> 1;
                currentCount = numGroups * 8;
                currentBuffer = new int[currentCount];
                byte[] bytes = new byte[numGroups * bitWidth];
                int bytesToRead = (int) ceil((double) (currentCount * bitWidth) / 8.0D);
                bytesToRead = Math.min(bytesToRead, inputStream.available());
                DataInputStream dataInputStream = new DataInputStream(inputStream);
                dataInputStream.readFully(bytes, 0, bytesToRead);
                int valueIndex = 0;

                for (int byteIndex = 0; valueIndex < currentCount; byteIndex += bitWidth) {
                    packer.unpack8Values(bytes, byteIndex, currentBuffer, valueIndex);
                    valueIndex += 8;
                }
                return true;
            default:
                throw new ParquetDecodingException("not a valid mode " + mode);
        }
    }

    public int[] getDecodedInts()
    {
        return currentBuffer;
    }

    public int getDecodedInt()
    {
        return currentValue;
    }

    public Mode getCurrentMode()
    {
        return mode;
    }

    public int getCurrentCount()
    {
        return currentCount;
    }

    public void decrementCurrentCount(int amount)
    {
        currentCount -= amount;
    }

    public interface BytePacker
    {
        void unpack8Values(byte[] input, int inputOffset, int[] output, int outputOffset);
    }

    public enum Packer {
        LITTLE_ENDIAN;

        public BytePacker newBytePacker(int bitWidth)
        {
            return new LittleEndianPacker(bitWidth);
        }
    }

    private static class LittleEndianPacker
            implements BytePacker
    {
        private final int bitWidth;

        LittleEndianPacker(int bitWidth)
        {
            this.bitWidth = bitWidth;
        }

        @Override
        public void unpack8Values(byte[] input, int inputOffset, int[] output, int outputOffset)
        {
            long packed = 0;
            for (int i = 0; i < bitWidth; i += 8) {
                packed |= ((long) input[inputOffset + (i / 8)]) << i;
            }
            long mask = (1L << bitWidth) - 1;
            for (int i = 0; i < 8; i++) {
                output[outputOffset + i] = (int) (packed & mask);
                packed >>>= bitWidth;
            }
        }
    }

    public enum Mode {
        RLE, PACKED
    }
}
