import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;

public class Converter {
	
	public Converter(){
	
	}
	
	public static short[] bytesToShort(byte[] bytes, boolean isBigEndian){
		
		ShortBuffer sbuf = ByteBuffer.wrap(bytes).order((isBigEndian? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)).asShortBuffer();
		
		short[] shorts = new short[sbuf.capacity()];
		sbuf.get(shorts);
		
		return shorts;
	}
	
	public static int[] bytesToInt(byte[] bytes, boolean isBigEndian){
		IntBuffer ibuf = ByteBuffer.wrap(bytes).order((isBigEndian? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)).asIntBuffer();
		
		int[] ints = new int[ibuf.capacity()];
		ibuf.get(ints);
		
		return ints;
	}
	
	public static float[] shortsToFloat(short[] shorts){
		float[] floats = new float[shorts.length];
		for (int i = 0; i < shorts.length; i++) {
		    floats[i] = ((float)shorts[i])/0x8000;
		}
		
		return floats;
	}
	
	public static float[] intsToFloat(int[] ints){
		float[] floats = new float[ints.length];
		for (int i = 0; i < ints.length; i++) {
		    floats[i] = ((float)ints[i])/0x80000000;
		}
		
		return floats;
	}
	
	public static short[] floatsToShort(float[] floats){
		short[] shorts = new short[floats.length];
		for (int i = 0; i < floats.length; i++){
			shorts[i] = (short) (floats[i] * (0x8000));
		}
		
		return shorts;
	}
	
	public static int[] floatsToInt(float[] floats){
		int[] ints = new int[floats.length];
		for (int i = 0; i < floats.length; i++){
			ints[i] = (int) (floats[i] * (0x8000));
		}
		
		return ints;
	}
	
	public static byte[] shortsToByte(short[] shorts, boolean isBigEndian){
		byte[] bytes = new byte[shorts.length * 2];
		ByteBuffer.wrap(bytes).order((isBigEndian? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)).asShortBuffer().put(shorts);
		return bytes;
	}
	
	public static byte[] intsToByte(int[] ints, boolean isBigEndian){
		byte[] bytes = new byte[ints.length * 4];
		ByteBuffer.wrap(bytes).order((isBigEndian? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)).asIntBuffer().put(ints);
		return bytes;
	}
	
	public static float[] bytesToFloat(byte[] bytes, boolean isBigEndian, int sampleSize){
		if (sampleSize == 16){
			return shortsToFloat(bytesToShort(bytes, isBigEndian));
		}
		else if (sampleSize == 32){
			return intsToFloat(bytesToInt(bytes, isBigEndian));
		}
		else return null;
	}
	
}
