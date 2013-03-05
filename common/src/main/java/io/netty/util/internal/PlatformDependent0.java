/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import sun.misc.Cleaner;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

    private static final Unsafe UNSAFE;
    private static final long CLEANER_FIELD_OFFSET;
    private static final long ADDRESS_FIELD_OFFSET;
    /**
     * {@code true} if and only if the platform supports unaligned access.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Segmentation_fault#Bus_error">Wikipedia on segfault</a>
     */
    private static final boolean UNALIGNED;
    private static final boolean HAS_COPY_METHODS;

    static {
        Unsafe unsafe;
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            unsafe = (Unsafe) singleoneInstanceField.get(null);
        } catch (Throwable cause) {
            unsafe = null;
        }
        UNSAFE = unsafe;

        if (unsafe == null) {
            CLEANER_FIELD_OFFSET = -1;
            ADDRESS_FIELD_OFFSET = -1;
            UNALIGNED = false;
            HAS_COPY_METHODS = false;
        } else {
            ByteBuffer direct = ByteBuffer.allocateDirect(1);
            Field cleanerField;
            try {
                cleanerField = direct.getClass().getDeclaredField("cleaner");
                cleanerField.setAccessible(true);
                Cleaner cleaner = (Cleaner) cleanerField.get(direct);
                cleaner.clean();
            } catch (Throwable t) {
                cleanerField = null;
            }
            CLEANER_FIELD_OFFSET = cleanerField != null? objectFieldOffset(cleanerField) : -1;

            boolean unaligned;
            try {
                Class<?> bitsClass = Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
                Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                unalignedMethod.setAccessible(true);
                unaligned = Boolean.TRUE.equals(unalignedMethod.invoke(null));
            } catch (Throwable t) {
                unaligned = false;
            }

            if (unaligned) {
                Field addressField = null;
                try {
                    addressField = Buffer.class.getDeclaredField("address");
                    addressField.setAccessible(true);
                    if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
                        unaligned = false;
                    } else {
                        ByteBuffer directBuf = ByteBuffer.allocateDirect(1);
                        if (addressField.getLong(directBuf) == 0) {
                            unaligned = false;
                        }
                        freeDirectBuffer(directBuf);
                    }
                } catch (Throwable t) {
                    unaligned = false;
                }
                ADDRESS_FIELD_OFFSET = addressField != null? objectFieldOffset(addressField) : -1;
            } else {
                ADDRESS_FIELD_OFFSET = -1;
            }

            UNALIGNED = unaligned;
            boolean hasCopyMethods;
            try {
                // Unsafe does not shop all copy methods in latest openjdk update..
                // https://github.com/netty/netty/issues/1061
                // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
                UNSAFE.getClass().getDeclaredMethod("copyMemory",
                        new Class[] { Object.class, long.class, Object.class, long.class, long.class });
                hasCopyMethods = true;
            } catch (Throwable ignore) {
                hasCopyMethods = false;
            }
            HAS_COPY_METHODS = hasCopyMethods;
        }
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static boolean canFreeDirectBuffer() {
        return ADDRESS_FIELD_OFFSET >= 0;
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static void freeDirectBuffer(ByteBuffer buffer) {
        Cleaner cleaner;
        try {
            cleaner = (Cleaner) getObject(buffer, CLEANER_FIELD_OFFSET);
            cleaner.clean();
        } catch (Throwable t) {
            // Nothing we can do here.
        }
    }

    static boolean hasCopyMethods() {
        return HAS_COPY_METHODS;
    }

    static long arrayBaseOffset() {
        return UNSAFE.arrayBaseOffset(byte[].class);
    }

    static Object getObject(Object object, long fieldOffset) {
        return UNSAFE.getObject(object, fieldOffset);
    }

    private static long getLong(Object object, long fieldOffset) {
        return UNSAFE.getLong(object, fieldOffset);
    }

    static long objectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    static short getShort(long address) {
        if (UNALIGNED) {
            return UNSAFE.getShort(address);
        } else {
            return (short)(getByte(address) << 8 | getByte(address + 1) & 0xff);
        }
    }

    static int getInt(long address) {
        if (UNALIGNED) {
            return UNSAFE.getInt(address);
        } else {
            return getByte(address) << 24 |
                  (getByte(address + 1) & 0xff) << 16 |
                  (getByte(address + 2) & 0xff) <<  8 |
                   getByte(address + 3) & 0xff;
        }
    }

    static long getLong(long address) {
        if (UNALIGNED) {
            return UNSAFE.getLong(address);
        } else {
            return (long) getByte(address) << 56 |
                  ((long) getByte(address + 1) & 0xff) << 48 |
                  ((long) getByte(address + 2) & 0xff) << 40 |
                  ((long) getByte(address + 3) & 0xff) << 32 |
                  ((long) getByte(address + 4) & 0xff) << 24 |
                  ((long) getByte(address + 5) & 0xff) << 16 |
                  ((long) getByte(address + 6) & 0xff) <<  8 |
                   (long) getByte(address + 7) & 0xff;
        }
    }

    static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    static void putShort(long address, short value) {
        if (UNALIGNED) {
            UNSAFE.putShort(address, value);
        } else {
            putByte(address, (byte) (value >>> 8));
            putByte(address + 1, (byte) value);
        }
    }

    static void putInt(long address, int value) {
        if (UNALIGNED) {
            UNSAFE.putInt(address, value);
        } else {
            putByte(address, (byte) (value >>> 24));
            putByte(address + 1, (byte) (value >>> 16));
            putByte(address + 2, (byte) (value >>> 8));
            putByte(address + 3, (byte) value);
        }
    }

    static void putLong(long address, long value) {
        if (UNALIGNED) {
            UNSAFE.putLong(address, value);
        } else {
            putByte(address, (byte) (value >>> 56));
            putByte(address + 1, (byte) (value >>> 48));
            putByte(address + 2, (byte) (value >>> 40));
            putByte(address + 3, (byte) (value >>> 32));
            putByte(address + 4, (byte) (value >>> 24));
            putByte(address + 5, (byte) (value >>> 16));
            putByte(address + 6, (byte) (value >>> 8));
            putByte(address + 7, (byte) value);
        }
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        UNSAFE.copyMemory(srcAddr, dstAddr, length);
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
    }

    private PlatformDependent0() {
    }
}
