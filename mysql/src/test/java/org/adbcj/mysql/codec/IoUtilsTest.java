package org.adbcj.mysql.codec;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class IoUtilsTest {

	@Test
	public void testSafeRead() throws IOException {
		InputStream in = new ByteArrayInputStream(new byte[] {0,1,2});
		Assert.assertEquals(IoUtils.safeRead(in), 0);
		Assert.assertEquals(IoUtils.safeRead(in), 1);
		Assert.assertEquals(IoUtils.safeRead(in), 2);
		try {
			IoUtils.safeRead(in);
			Assert.fail("Did not throw EOF exception");
		} catch (EOFException e) {
			// Pass
		}
	}

	@Test(dependsOnMethods="testSafeRead")
	public void testReadShort() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort((short)0).putShort((short)1).putShort((short)2).putShort((short)-1).putShort((short)-2);
		InputStream in = new ByteArrayInputStream(buffer.array(), 0, buffer.position());
		Assert.assertEquals(IoUtils.readShort(in), 0);
		Assert.assertEquals(IoUtils.readShort(in), 1);
		Assert.assertEquals(IoUtils.readShort(in), 2);
		Assert.assertEquals(IoUtils.readShort(in), -1);
		Assert.assertEquals(IoUtils.readShort(in), -2);
	}

	@Test(dependsOnMethods="testSafeRead")
	public void testReadUnsignedShort() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort((short)0).putShort((short)1).putShort((short)2).putShort((short)-1).putShort((short)-2);
		InputStream in = new ByteArrayInputStream(buffer.array(), 0, buffer.position());
		Assert.assertEquals(IoUtils.readUnsignedShort(in), 0);
		Assert.assertEquals(IoUtils.readUnsignedShort(in), 1);
		Assert.assertEquals(IoUtils.readUnsignedShort(in), 2);
		Assert.assertEquals(IoUtils.readUnsignedShort(in), 0xffff);
		Assert.assertEquals(IoUtils.readUnsignedShort(in), 0xfffe);
	}

	@Test
	public void readString() throws IOException {
		final String first = "Hi there";
		final String second = "Have a nice day!";
		byte[] firstBytes = first.getBytes();
		byte[] secondBytes = second.getBytes();
		byte[] newBytes = new byte[firstBytes.length + secondBytes.length + 1];
		System.arraycopy(firstBytes, 0, newBytes, 0, firstBytes.length);
		System.arraycopy(secondBytes, 0, newBytes, firstBytes.length + 1, secondBytes.length);
        InputStream nativeInputStream = new ByteArrayInputStream(newBytes);
        BoundedInputStream in = new BoundedInputStream(nativeInputStream,newBytes.length+1);

		Assert.assertEquals(IoUtils.readNullTerminatedString(in, StandardCharsets.UTF_8), first);
		Assert.assertEquals(IoUtils.readNullTerminatedString(in, StandardCharsets.UTF_8), second);
	}
	@Test
	public void allNullOneByte() throws IOException {
        Object[] noNullValueOneElement = new Object[]{1};
        Assert.assertEquals(IoUtils.nullMask(noNullValueOneElement)[0],(byte)0);
        Object[] noNullValueTwoElement = new Object[]{1,1};
        Assert.assertEquals(IoUtils.nullMask(noNullValueTwoElement)[0],(byte)0);
        Object[] noNullValueFullByte = new Object[]{1,1,1,1,1,1,1,1};
        Assert.assertEquals(IoUtils.nullMask(noNullValueFullByte)[0],(byte)0);
	}
	@Test
	public void someNullOneByte() throws IOException {
        Object[] firstArgNull = new Object[]{null,1,1,1,1,1,1,1};
        Assert.assertEquals(IoUtils.nullMask(firstArgNull)[0],(byte)1);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,null,1,1,1,1,1,1})[0],(byte)2);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,null,1,1,1,1,1,1})[0],(byte)3);
        byte mostSignificatBitSet = (byte)0x80;
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,null})[0],mostSignificatBitSet);
        byte mostAndLeastBitSet = (byte)(mostSignificatBitSet | 0x01);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,null})[0],mostAndLeastBitSet);
	}
	@Test
	public void allNullMutlipleByte() throws IOException {
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1})[0],(byte)0);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1})[1],(byte)0);


        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1})[0],(byte)0);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1})[1],(byte)0);

        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1})[0],(byte)0);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1})[1],(byte)0);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1})[2],(byte)0);
	}
	@Test
	public void someNullMutlipleByte() throws IOException {
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,1,1})[0],(byte)1);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,1,1})[1],(byte)0);

        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,null,1,1,1,1,1,1,1})[0],(byte)3);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,1,null})[1],(byte)1);


        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,1,null,1,1,1,1,1,1,1})[0],(byte)1);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,1,null,1,1,1,1,1,1,1})[1],(byte)1);


        byte mostSignificatBitSet = (byte)0x80;
        byte mostAndLeastBitSet = (byte)(mostSignificatBitSet | 0x01);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,null,null,1,1,1,1,1,1,null})[0], mostAndLeastBitSet);
        Assert.assertEquals(IoUtils.nullMask(new Object[]{null,1,1,1,1,1,1,null,null,1,1,1,1,1,1,null})[1], mostAndLeastBitSet);
	}

}
