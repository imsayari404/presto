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

import com.facebook.presto.parquet.batchreader.decoders.ValuesDecoder.TimestampValuesDecoder;
import com.facebook.presto.parquet.batchreader.dictionary.TimestampDictionary;
import org.apache.parquet.io.ParquetDecodingException;
import org.openjdk.jol.info.ClassLayout;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.slice.SizeOf.sizeOf;

public class TimestampRLEDictionaryValuesDecoder
        extends GenericRLEDictionaryValuesDecoder
        implements TimestampValuesDecoder
{
    private static final int INSTANCE_SIZE = ClassLayout.parseClass(TimestampRLEDictionaryValuesDecoder.class).instanceSize();

    private final TimestampDictionary dictionary;

    public TimestampRLEDictionaryValuesDecoder(int bitWidth, InputStream inputStream, TimestampDictionary dictionary)
    {
        super(Integer.MAX_VALUE, bitWidth, inputStream);
        this.dictionary = dictionary;
    }

    @Override
    public void readNext(long[] values, int offset, int length)
            throws IOException
    {
        int destinationIndex = offset;
        int remainingToCopy = length;
        while (remainingToCopy > 0) {
            if (getCurrentCount() == 0) {
                if (!decode()) {
                    break;
                }
            }

            int numEntriesToFill = Math.min(remainingToCopy, getCurrentCount());
            int endIndex = destinationIndex + numEntriesToFill;
            switch (getCurrentMode()) {
                case RLE: {
                    final int rleValue = getDecodedInt();
                    final long rleValueMillis = dictionary.decodeToLong(rleValue);
                    while (destinationIndex < endIndex) {
                        values[destinationIndex++] = rleValueMillis;
                    }
                    break;
                }
                case PACKED: {
                    final int[] localBuffer = getDecodedInts();
                    final TimestampDictionary localDictionary = dictionary;
                    for (int srcIndex = localBuffer.length - getCurrentCount(); destinationIndex < endIndex; srcIndex++) {
                        values[destinationIndex++] = localDictionary.decodeToLong(localBuffer[srcIndex]);
                    }
                    break;
                }
                default:
                    throw new ParquetDecodingException("not a valid mode " + getCurrentMode());
            }

            decrementCurrentCount(numEntriesToFill);
            remainingToCopy -= numEntriesToFill;
        }
        checkState(remainingToCopy == 0, "End of stream: Invalid read size request");
    }

    @Override
    public void skip(int length)
            throws IOException
    {
        checkArgument(length >= 0, "invalid length %s", length);
        int remaining = length;
        while (remaining > 0) {
            if (getCurrentCount() == 0) {
                if (!decode()) {
                    break;
                }
            }

            int readChunkSize = Math.min(remaining, getCurrentCount());
            decrementCurrentCount(readChunkSize);
            remaining -= readChunkSize;
        }
        checkState(remaining == 0, "End of stream: Invalid skip size request: %s", length);
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return INSTANCE_SIZE + (dictionary == null ? 0 : dictionary.getRetainedSizeInBytes()) + sizeOf(getDecodedInts());
    }
}
