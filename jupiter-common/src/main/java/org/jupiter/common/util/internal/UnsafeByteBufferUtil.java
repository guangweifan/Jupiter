/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.common.util.internal;

import org.jupiter.common.util.internal.logging.InternalLogger;
import org.jupiter.common.util.internal.logging.InternalLoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.nio.ByteOrder;

import static org.jupiter.common.util.StackTraceUtil.stackTrace;

/**
 * jupiter
 * org.jupiter.common.util.internal
 *
 * @author jiachun.fjc
 */
public final class UnsafeByteBufferUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(UnsafeByteBufferUtil.class);

    private static final Unsafe unsafe;

    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final boolean UNALIGNED;
    private static final boolean BIG_ENDIAN_NATIVE_ORDER;

    // These numbers represent the point at which we have empirically
    // determined that the average cost of a JNI call exceeds the expense
    // of an element by element copy.  These numbers may change over time.
    private static final int JNI_COPY_TO_ARRAY_THRESHOLD = 6;
    private static final int JNI_COPY_FROM_ARRAY_THRESHOLD = 6;

    // Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
    // during a large copy.
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    static {
        unsafe = JUnsafe.getUnsafe();

        if (unsafe == null) {
            BYTE_ARRAY_BASE_OFFSET = -1;
            UNALIGNED = false;
        } else {
            BYTE_ARRAY_BASE_OFFSET = unsafe.arrayBaseOffset(byte[].class);
            boolean _unaligned;
            try {
                Class<?> bitsClass = Class.forName("java.nio.Bits", false, JUnsafe.getSystemClassLoader());
                Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                unalignedMethod.setAccessible(true);
                _unaligned = (boolean) unalignedMethod.invoke(null);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("java.nio.Bits: unavailable, {}.", stackTrace(t));
                }

                _unaligned = false;
            }
            UNALIGNED = _unaligned;
        }

        BIG_ENDIAN_NATIVE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    public static byte getByte(long address) {
        return unsafe.getByte(address);
    }

    public static short getShort(long address) {
        if (UNALIGNED) {
            short v = unsafe.getShort(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Short.reverseBytes(v);
        }
        return (short) (unsafe.getByte(address) << 8 | unsafe.getByte(address + 1) & 0xff);
    }

    public static short getShortLE(long address) {
        if (UNALIGNED) {
            short v = unsafe.getShort(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes(v) : v;
        }
        return (short) (unsafe.getByte(address) & 0xff | unsafe.getByte(address + 1) << 8);
    }

    public static int getInt(long address) {
        if (UNALIGNED) {
            int v = unsafe.getInt(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Integer.reverseBytes(v);
        }
        return unsafe.getByte(address) << 24 |
                (unsafe.getByte(address + 1) & 0xff) << 16 |
                (unsafe.getByte(address + 2) & 0xff) << 8 |
                unsafe.getByte(address + 3) & 0xff;
    }

    public static int getIntLE(long address) {
        if (UNALIGNED) {
            int v = unsafe.getInt(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(v) : v;
        }
        return unsafe.getByte(address) & 0xff |
                (unsafe.getByte(address + 1) & 0xff) << 8 |
                (unsafe.getByte(address + 2) & 0xff) << 16 |
                unsafe.getByte(address + 3) << 24;
    }

    public static long getLong(long address) {
        if (UNALIGNED) {
            long v = unsafe.getLong(address);
            return BIG_ENDIAN_NATIVE_ORDER ? v : Long.reverseBytes(v);
        }
        return ((long) unsafe.getByte(address)) << 56 |
                (unsafe.getByte(address + 1) & 0xffL) << 48 |
                (unsafe.getByte(address + 2) & 0xffL) << 40 |
                (unsafe.getByte(address + 3) & 0xffL) << 32 |
                (unsafe.getByte(address + 4) & 0xffL) << 24 |
                (unsafe.getByte(address + 5) & 0xffL) << 16 |
                (unsafe.getByte(address + 6) & 0xffL) << 8 |
                (unsafe.getByte(address + 7)) & 0xffL;
    }

    public static long getLongLE(long address) {
        if (UNALIGNED) {
            long v = unsafe.getLong(address);
            return BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(v) : v;
        }
        return (unsafe.getByte(address)) & 0xffL |
                (unsafe.getByte(address + 1) & 0xffL) << 8 |
                (unsafe.getByte(address + 2) & 0xffL) << 16 |
                (unsafe.getByte(address + 3) & 0xffL) << 24 |
                (unsafe.getByte(address + 4) & 0xffL) << 32 |
                (unsafe.getByte(address + 5) & 0xffL) << 40 |
                (unsafe.getByte(address + 6) & 0xffL) << 48 |
                ((long) unsafe.getByte(address + 7)) << 56;
    }

    public static void getBytes(long address, byte[] dst, int dstIndex, int length) {
        if (length > JNI_COPY_TO_ARRAY_THRESHOLD) {
            copyMemory(null, address, dst, BYTE_ARRAY_BASE_OFFSET + dstIndex, length);
        } else {
            int end = dstIndex + length;
            for (int i = dstIndex; i < end; i++) {
                dst[i] = unsafe.getByte(address++);
            }
        }
    }

    public static void setByte(long address, int value) {
        unsafe.putByte(address, (byte) value);
    }

    public static void setShort(long address, int value) {
        if (UNALIGNED) {
            unsafe.putShort(
                    address, BIG_ENDIAN_NATIVE_ORDER ? (short) value : Short.reverseBytes((short) value));
        } else {
            unsafe.putByte(address, (byte) (value >>> 8));
            unsafe.putByte(address + 1, (byte) value);
        }
    }

    public static void setShortLE(long address, int value) {
        if (UNALIGNED) {
            unsafe.putShort(
                    address, BIG_ENDIAN_NATIVE_ORDER ? Short.reverseBytes((short) value) : (short) value);
        } else {
            unsafe.putByte(address, (byte) value);
            unsafe.putByte(address + 1, (byte) (value >>> 8));
        }
    }

    public static void setInt(long address, int value) {
        if (UNALIGNED) {
            unsafe.putInt(address, BIG_ENDIAN_NATIVE_ORDER ? value : Integer.reverseBytes(value));
        } else {
            unsafe.putByte(address, (byte) (value >>> 24));
            unsafe.putByte(address + 1, (byte) (value >>> 16));
            unsafe.putByte(address + 2, (byte) (value >>> 8));
            unsafe.putByte(address + 3, (byte) value);
        }
    }

    public static void setIntLE(long address, int value) {
        if (UNALIGNED) {
            unsafe.putInt(address, BIG_ENDIAN_NATIVE_ORDER ? Integer.reverseBytes(value) : value);
        } else {
            unsafe.putByte(address, (byte) value);
            unsafe.putByte(address + 1, (byte) (value >>> 8));
            unsafe.putByte(address + 2, (byte) (value >>> 16));
            unsafe.putByte(address + 3, (byte) (value >>> 24));
        }
    }

    public static void setLong(long address, long value) {
        if (UNALIGNED) {
            unsafe.putLong(address, BIG_ENDIAN_NATIVE_ORDER ? value : Long.reverseBytes(value));
        } else {
            unsafe.putByte(address, (byte) (value >>> 56));
            unsafe.putByte(address + 1, (byte) (value >>> 48));
            unsafe.putByte(address + 2, (byte) (value >>> 40));
            unsafe.putByte(address + 3, (byte) (value >>> 32));
            unsafe.putByte(address + 4, (byte) (value >>> 24));
            unsafe.putByte(address + 5, (byte) (value >>> 16));
            unsafe.putByte(address + 6, (byte) (value >>> 8));
            unsafe.putByte(address + 7, (byte) value);
        }
    }

    public static void setLongLE(long address, long value) {
        if (UNALIGNED) {
            unsafe.putLong(address, BIG_ENDIAN_NATIVE_ORDER ? Long.reverseBytes(value) : value);
        } else {
            unsafe.putByte(address, (byte) value);
            unsafe.putByte(address + 1, (byte) (value >>> 8));
            unsafe.putByte(address + 2, (byte) (value >>> 16));
            unsafe.putByte(address + 3, (byte) (value >>> 24));
            unsafe.putByte(address + 4, (byte) (value >>> 32));
            unsafe.putByte(address + 5, (byte) (value >>> 40));
            unsafe.putByte(address + 6, (byte) (value >>> 48));
            unsafe.putByte(address + 7, (byte) (value >>> 56));
        }
    }

    public static void setBytes(long address, byte[] src, int srcIndex, int length) {
        if (length > JNI_COPY_FROM_ARRAY_THRESHOLD) {
            copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcIndex, null, address, length);
        } else {
            int end = srcIndex + length;
            for (int i = srcIndex; i < end; i++) {
                unsafe.putByte(address++, src[i]);
            }
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            unsafe.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }
}
