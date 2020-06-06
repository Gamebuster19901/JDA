/*
 * Copyright 2015-2020 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api.utils.data.etf;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static net.dv8tion.jda.api.utils.data.etf.ExTermTag.*;

public class ExTermEncoder
{
    public static ByteBuffer pack(Object data)
    {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put((byte) 131);

        ByteBuffer packed = pack(buffer, data);
        // This cast prevents issues with backwards compatibility in the ABI (java 11 made breaking changes)
        ((Buffer) packed).flip();
        return packed;
    }

    @SuppressWarnings("unchecked")
    public static ByteBuffer pack(ByteBuffer buffer, Object value)
    {
        if (value instanceof String)
            return packBinary(buffer, (String) value);
        if (value instanceof Map)
            return packMap(buffer, (Map<String, Object>) value);
        if (value instanceof Collection)
            return packList(buffer, (Collection<Object>) value);
        if (value instanceof Integer)
            return packInt(buffer, (int) value);
        if (value instanceof Long)
            return packLong(buffer, (long) value);
        if (value instanceof Boolean)
            return packAtom(buffer, String.valueOf(value));
        if (value == null)
            return packAtom(buffer, "nil");

        throw new UnsupportedOperationException("Cannot pack value of type " + value.getClass().getName());
    }

    private static ByteBuffer realloc(ByteBuffer buffer, int length)
    {
        if (buffer.remaining() >= length)
            return buffer;

        ByteBuffer allocated = ByteBuffer.allocate((buffer.position() + length) * 2);
        // This cast prevents issues with backwards compatibility in the ABI (java 11 made breaking changes)
        ((Buffer) buffer).flip();
        allocated.put(buffer);
        return allocated;
    }

    private static ByteBuffer packMap(ByteBuffer buffer, Map<String, Object> data)
    {
        buffer = realloc(buffer, data.size() + 5);
        buffer.put(MAP);
        buffer.putInt(data.size());

        for (Map.Entry<String, Object> entry : data.entrySet())
        {
            buffer = packBinary(buffer, entry.getKey());
            buffer = pack(buffer, entry.getValue());
        }

        return buffer;
    }

    private static ByteBuffer packList(ByteBuffer buffer, Collection<Object> data)
    {
        buffer = realloc(buffer, data.size() + 5);
        buffer.put(LIST);
        buffer.putInt(data.size());
        for (Object element : data)
            buffer = pack(buffer, element);
        buffer.put(NIL);
        return buffer;
    }

    private static ByteBuffer packBinary(ByteBuffer buffer, String value)
    {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        buffer = realloc(buffer, encoded.length + 5);
        buffer.put(BINARY);
        buffer.putInt(value.length());
        buffer.put(encoded);
        return buffer;
    }

    private static ByteBuffer packInt(ByteBuffer buffer, int value)
    {
        buffer = realloc(buffer, 5);
        buffer.put(INT);
        buffer.putInt(value);
        return buffer;
    }

    private static ByteBuffer packLong(ByteBuffer buffer, long value)
    {
        byte bytes = countBytes(value);
        buffer = realloc(buffer, 3 + bytes);
        buffer.put(SMALL_BIGINT);
        buffer.put(bytes);
        // We only use "unsigned" value so the sign is always positive
        buffer.put((byte) 0);
        while (value > 0)
        {
            buffer.put((byte) value);
            value >>>= 8;
        }

        return buffer;
    }

    private static ByteBuffer packAtom(ByteBuffer buffer, String value)
    {
        byte[] array = value.getBytes(StandardCharsets.ISO_8859_1);
        buffer = realloc(buffer, array.length + 3);
        buffer.put(ATOM);
        buffer.putShort((short) array.length);
        buffer.put(array);
        return buffer;
    }

    private static byte countBytes(long value)
    {
        int leadingZeros = Long.numberOfLeadingZeros(value);
        return (byte) Math.ceil((64 - leadingZeros) / 8.);
    }
}