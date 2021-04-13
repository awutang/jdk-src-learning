/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import java.io.FileDescriptor;
import sun.misc.Cleaner;
import sun.misc.Unsafe;
import sun.misc.VM;
import sun.nio.ch.DirectBuffer;

/**
 * 只能存储字节数据
 * IO性能更好，可以省一次cpu copy
 *
 * 内存映射文件的作用是使一个磁盘文件与存储空间中的一个缓冲区建立映射关系，然后当从缓冲区中取数据，就相当于读文件中的相应字节；而将数据存入缓冲区，就相当于写文件中的相应字节。这样就可以不使用read和write直接执行I/O了
 */
class DirectByteBuffer

    extends MappedByteBuffer



    implements DirectBuffer
{



    // Cached unsafe-access object
    protected static final Unsafe unsafe = Bits.unsafe();

    // Cached array base offset
    private static final long arrayBaseOffset = (long)unsafe.arrayBaseOffset(byte[].class);

    // Cached unaligned-access capability
    protected static final boolean unaligned = Bits.unaligned();

    // Base address, used in all indexing calculations
    // NOTE: moved up to Buffer.java for speed in JNI GetDirectBufferAddress
    //    protected long address;

    // An object attached to this buffer. If this buffer is a view of another
    // buffer then we use this field to keep a reference to that buffer to
    // ensure that its memory isn't freed before we are done with it.
    private final Object att;

    public Object attachment() {
        return att;
    }



    private static class Deallocator
        implements Runnable
    {

        private static Unsafe unsafe = Unsafe.getUnsafe();

        private long address;
        private long size;
        private int capacity;

        private Deallocator(long address, long size, int capacity) {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.capacity = capacity;
        }

        /**
         * 在回收DirectByteBuffer对象时执行的逻辑
         */
        public void run() {
            if (address == 0) {
                // Paranoia
                return;
            }
            // 回收堆外内存
            unsafe.freeMemory(address);
            address = 0;
            Bits.unreserveMemory(size, capacity);
        }

    }

    // cleaner是虚引用在jdk中的典型应用场景
    private final Cleaner cleaner;

    public Cleaner cleaner() { return cleaner; }











    // Primary constructor
    //
    DirectByteBuffer(int cap) {                   // package-private
        // 调用DirectByteBuffer构造方法，为字段赋值
        super(-1, 0, cap, cap);
        // 分配的直接内存是否按照页对齐
        boolean pa = VM.isDirectMemoryPageAligned();
        // 一页直接内存的大小（以byte为单位）
        int ps = Bits.pageSize();
        // 分配的内存大小，如果按照页对齐，还得加一页内存的容量大小，我们只需要分析pa为false的情况就好了
        long size = Math.max(1L, (long)cap + (pa ? ps : 0));
        // 预分配内存：保存总内存大小和实际内存大小 size-reservedMemory cap-totalCapacity
        // 预分配内存就是在真正的分配内存时，先判断堆外内存空闲空间是否足够：如果不够则触发Cleaner.clean()回收堆外内存，
        Bits.reserveMemory(size, cap);

        long base = 0;
        try {
            // 以指定的size，分配直接内存，返回的是分配的堆外内存的基地址 myConfusion:直接内存的分配与回收是啥样的？
            base = unsafe.allocateMemory(size);
        } catch (OutOfMemoryError x) {
            Bits.unreserveMemory(size, cap);
            throw x;
        }
        // 给刚分配的内存初始化为零值
        unsafe.setMemory(base, size, (byte) 0);

        // 将分配的内存按页对齐，因此得出新的基地址（这个address是分配内存的起始地址，之后的数据读写以它作为基准）
        if (pa && (base % ps != 0)) {
            // Round up to page boundary 向上取整（向上取到页面边界）
            address = base + ps - (base & (ps - 1));
        } else {
            // pa==false
            address = base;
        }
        // 创建用于回收this对象的清理者，当DirectByteBuffer对象（在堆内的）被回收时，释放其对应的堆外内存
        cleaner = Cleaner.create(this, new Deallocator(base, size, cap));
        att = null;

    }



    // Invoked to construct a direct ByteBuffer referring to the block of
    // memory. A given arbitrary object may also be attached to the buffer.
    //
    DirectByteBuffer(long addr, int cap, Object ob) {
        super(-1, 0, cap, cap);
        address = addr;
        cleaner = null;
        att = ob;
    }


    // Invoked only by JNI: NewDirectByteBuffer(void*, long)
    //
    private DirectByteBuffer(long addr, int cap) {
        super(-1, 0, cap, cap);
        address = addr;
        cleaner = null;
        att = null;
    }



    // For memory-mapped buffers -- invoked by FileChannelImpl via reflection
    //
    protected DirectByteBuffer(int cap, long addr,
                                     FileDescriptor fd,
                                     Runnable unmapper)
    {

        super(-1, 0, cap, cap, fd);
        address = addr;
        cleaner = Cleaner.create(this, unmapper);
        att = null;



    }



    // For duplicates and slices
    //
    DirectByteBuffer(DirectBuffer db,         // package-private
                               int mark, int pos, int lim, int cap,
                               int off)
    {

        super(mark, pos, lim, cap);
        // 与传入的db指向同一块直接内存空间，起始地址为db原position
        address = db.address() + off;

        cleaner = null;

        att = db;



    }

    /**
     * 新建一个buffer,其实是将起始地址移到了原position处，所以新position=0
     * @return
     */
    public ByteBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << 0);
        assert (off >= 0);
        return new DirectByteBuffer(this, -1, 0, rem, rem, off);
    }

    public ByteBuffer duplicate() {
        return new DirectByteBuffer(this,
                                              this.markValue(),
                                              this.position(),
                                              this.limit(),
                                              this.capacity(),
                                              0);
    }

    public ByteBuffer asReadOnlyBuffer() {

        return new DirectByteBufferR(this,
                                           this.markValue(),
                                           this.position(),
                                           this.limit(),
                                           this.capacity(),
                                           0);



    }



    public long address() {
        return address;
    }

    private long ix(int i) {
        // address是之前分配的那一块直接内存的起始地址，与position、limit等配合使用，维护一个buffer对象对应的一块直接内存
        return address + ((long)i << 0);
    }

    public byte get() {
        return ((unsafe.getByte(ix(nextGetIndex()))));
    }

    public byte get(int i) {
        return ((unsafe.getByte(ix(checkIndex(i)))));
    }







    public ByteBuffer get(byte[] dst, int offset, int length) {

        if (((long)length << 0) > Bits.JNI_COPY_TO_ARRAY_THRESHOLD) {
            checkBounds(offset, length, dst.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferUnderflowException();
                Bits.copyToArray(ix(pos), dst, arrayBaseOffset,
                                 (long)offset << 0,
                                 (long)length << 0);
            position(pos + length);
        } else {
            super.get(dst, offset, length);
        }
        return this;



    }



    public ByteBuffer put(byte x) {

        unsafe.putByte(ix(nextPutIndex()), ((x)));
        return this;



    }

    public ByteBuffer put(int i, byte x) {

        unsafe.putByte(ix(checkIndex(i)), ((x)));
        return this;



    }

    public ByteBuffer put(ByteBuffer src) {

        if (src instanceof DirectByteBuffer) {
            if (src == this)
                throw new IllegalArgumentException();
            DirectByteBuffer sb = (DirectByteBuffer)src;

            int spos = sb.position();
            int slim = sb.limit();
            assert (spos <= slim);
            int srem = (spos <= slim ? slim - spos : 0);

            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);

            if (srem > rem)
                throw new BufferOverflowException();
            // copy
            unsafe.copyMemory(sb.ix(spos), ix(pos), (long)srem << 0);
            sb.position(spos + srem);
            position(pos + srem);
        } else if (src.hb != null) {

            int spos = src.position();
            int slim = src.limit();
            assert (spos <= slim);
            int srem = (spos <= slim ? slim - spos : 0);

            put(src.hb, src.offset + spos, srem);
            src.position(spos + srem);

        } else {
            super.put(src);
        }
        return this;



    }

    /**
     * 从src的offset处将length个byte复制到当前直接内存
     * @param src
     * @param offset
     * @param length
     * @return
     */
    public ByteBuffer put(byte[] src, int offset, int length) {

        if (((long)length << 0) > Bits.JNI_COPY_FROM_ARRAY_THRESHOLD) {
            checkBounds(offset, length, src.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferOverflowException();
            Bits.copyFromArray(src, arrayBaseOffset,
                                   (long)offset << 0,
                                   ix(pos),
                                   (long)length << 0);
            position(pos + length);
        } else {
            super.put(src, offset, length);
        }
        return this;



    }

    public ByteBuffer compact() {

        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        unsafe.copyMemory(ix(pos), ix(0), (long)rem << 0);
        position(rem);
        limit(capacity());
        discardMark();
        return this;



    }

    public boolean isDirect() {
        return true;
    }

    public boolean isReadOnly() {
        return false;
    }
































































    byte _get(int i) {                          // package-private
        return unsafe.getByte(address + i);
    }

    void _put(int i, byte b) {                  // package-private

        unsafe.putByte(address + i, b);



    }




    private char getChar(long a) {
        if (unaligned) {
            char x = unsafe.getChar(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getChar(a, bigEndian);
    }

    public char getChar() {
        return getChar(ix(nextGetIndex((1 << 1))));
    }

    public char getChar(int i) {
        return getChar(ix(checkIndex(i, (1 << 1))));
    }



    private ByteBuffer putChar(long a, char x) {

        if (unaligned) {
            char y = (x);
            unsafe.putChar(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putChar(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putChar(char x) {

        putChar(ix(nextPutIndex((1 << 1))), x);
        return this;



    }

    public ByteBuffer putChar(int i, char x) {

        putChar(ix(checkIndex(i, (1 << 1))), x);
        return this;



    }

    public CharBuffer asCharBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 1;
        if (!unaligned && ((address + off) % (1 << 1) != 0)) {
            return (bigEndian
                    ? (CharBuffer)(new ByteBufferAsCharBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (CharBuffer)(new ByteBufferAsCharBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (CharBuffer)(new DirectCharBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (CharBuffer)(new DirectCharBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private short getShort(long a) {
        if (unaligned) {
            short x = unsafe.getShort(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getShort(a, bigEndian);
    }

    public short getShort() {
        return getShort(ix(nextGetIndex((1 << 1))));
    }

    public short getShort(int i) {
        return getShort(ix(checkIndex(i, (1 << 1))));
    }



    private ByteBuffer putShort(long a, short x) {

        if (unaligned) {
            short y = (x);
            unsafe.putShort(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putShort(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putShort(short x) {

        putShort(ix(nextPutIndex((1 << 1))), x);
        return this;



    }

    public ByteBuffer putShort(int i, short x) {

        putShort(ix(checkIndex(i, (1 << 1))), x);
        return this;



    }

    public ShortBuffer asShortBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 1;
        if (!unaligned && ((address + off) % (1 << 1) != 0)) {
            return (bigEndian
                    ? (ShortBuffer)(new ByteBufferAsShortBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (ShortBuffer)(new ByteBufferAsShortBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (ShortBuffer)(new DirectShortBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (ShortBuffer)(new DirectShortBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private int getInt(long a) {
        if (unaligned) {
            int x = unsafe.getInt(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getInt(a, bigEndian);
    }

    public int getInt() {
        return getInt(ix(nextGetIndex((1 << 2))));
    }

    public int getInt(int i) {
        return getInt(ix(checkIndex(i, (1 << 2))));
    }



    private ByteBuffer putInt(long a, int x) {

        if (unaligned) {
            int y = (x);
            unsafe.putInt(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putInt(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putInt(int x) {

        putInt(ix(nextPutIndex((1 << 2))), x);
        return this;



    }

    public ByteBuffer putInt(int i, int x) {

        putInt(ix(checkIndex(i, (1 << 2))), x);
        return this;



    }

    public IntBuffer asIntBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 2;
        if (!unaligned && ((address + off) % (1 << 2) != 0)) {
            return (bigEndian
                    ? (IntBuffer)(new ByteBufferAsIntBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (IntBuffer)(new ByteBufferAsIntBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (IntBuffer)(new DirectIntBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (IntBuffer)(new DirectIntBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private long getLong(long a) {
        if (unaligned) {
            long x = unsafe.getLong(a);
            return (nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getLong(a, bigEndian);
    }

    public long getLong() {
        return getLong(ix(nextGetIndex((1 << 3))));
    }

    public long getLong(int i) {
        return getLong(ix(checkIndex(i, (1 << 3))));
    }



    private ByteBuffer putLong(long a, long x) {

        if (unaligned) {
            long y = (x);
            unsafe.putLong(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putLong(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putLong(long x) {

        putLong(ix(nextPutIndex((1 << 3))), x);
        return this;



    }

    public ByteBuffer putLong(int i, long x) {

        putLong(ix(checkIndex(i, (1 << 3))), x);
        return this;



    }

    public LongBuffer asLongBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 3;
        if (!unaligned && ((address + off) % (1 << 3) != 0)) {
            return (bigEndian
                    ? (LongBuffer)(new ByteBufferAsLongBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (LongBuffer)(new ByteBufferAsLongBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (LongBuffer)(new DirectLongBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (LongBuffer)(new DirectLongBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private float getFloat(long a) {
        if (unaligned) {
            int x = unsafe.getInt(a);
            return Float.intBitsToFloat(nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getFloat(a, bigEndian);
    }

    public float getFloat() {
        return getFloat(ix(nextGetIndex((1 << 2))));
    }

    public float getFloat(int i) {
        return getFloat(ix(checkIndex(i, (1 << 2))));
    }



    private ByteBuffer putFloat(long a, float x) {

        if (unaligned) {
            int y = Float.floatToRawIntBits(x);
            unsafe.putInt(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putFloat(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putFloat(float x) {

        putFloat(ix(nextPutIndex((1 << 2))), x);
        return this;



    }

    public ByteBuffer putFloat(int i, float x) {

        putFloat(ix(checkIndex(i, (1 << 2))), x);
        return this;



    }

    public FloatBuffer asFloatBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 2;
        if (!unaligned && ((address + off) % (1 << 2) != 0)) {
            return (bigEndian
                    ? (FloatBuffer)(new ByteBufferAsFloatBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (FloatBuffer)(new ByteBufferAsFloatBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (FloatBuffer)(new DirectFloatBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (FloatBuffer)(new DirectFloatBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }




    private double getDouble(long a) {
        if (unaligned) {
            long x = unsafe.getLong(a);
            return Double.longBitsToDouble(nativeByteOrder ? x : Bits.swap(x));
        }
        return Bits.getDouble(a, bigEndian);
    }

    public double getDouble() {
        return getDouble(ix(nextGetIndex((1 << 3))));
    }

    public double getDouble(int i) {
        return getDouble(ix(checkIndex(i, (1 << 3))));
    }



    private ByteBuffer putDouble(long a, double x) {

        if (unaligned) {
            long y = Double.doubleToRawLongBits(x);
            unsafe.putLong(a, (nativeByteOrder ? y : Bits.swap(y)));
        } else {
            Bits.putDouble(a, x, bigEndian);
        }
        return this;



    }

    public ByteBuffer putDouble(double x) {

        putDouble(ix(nextPutIndex((1 << 3))), x);
        return this;



    }

    public ByteBuffer putDouble(int i, double x) {

        putDouble(ix(checkIndex(i, (1 << 3))), x);
        return this;



    }

    public DoubleBuffer asDoubleBuffer() {
        int off = this.position();
        int lim = this.limit();
        assert (off <= lim);
        int rem = (off <= lim ? lim - off : 0);

        int size = rem >> 3;
        if (!unaligned && ((address + off) % (1 << 3) != 0)) {
            return (bigEndian
                    ? (DoubleBuffer)(new ByteBufferAsDoubleBufferB(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off))
                    : (DoubleBuffer)(new ByteBufferAsDoubleBufferL(this,
                                                                       -1,
                                                                       0,
                                                                       size,
                                                                       size,
                                                                       off)));
        } else {
            return (nativeByteOrder
                    ? (DoubleBuffer)(new DirectDoubleBufferU(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off))
                    : (DoubleBuffer)(new DirectDoubleBufferS(this,
                                                                 -1,
                                                                 0,
                                                                 size,
                                                                 size,
                                                                 off)));
        }
    }

}
