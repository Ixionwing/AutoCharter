
public class Signature {
	private byte[] bytes;
	private int lane;
	private boolean complete;
	
	public Signature(byte[] bytes, int lane) {
		this.bytes = bytes;
		this.lane = lane;
		this.complete = false;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public void setBytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public int getLane() {
		return lane;
	}

	public void setLane(int lane) {
		this.lane = lane;
	}
	
	public boolean isComplete(){
		return complete;
	}
	
	public void setComplete(boolean complete){
		this.complete = complete;
	}
	

	public void append (byte[] arr1){
		byte[] newArr = new byte[this.bytes.length + arr1.length];
		
		System.arraycopy(this.bytes, 0, newArr, 0, this.bytes.length);
		System.arraycopy(arr1, 0, newArr, this.bytes.length, arr1.length);
		
		this.bytes = newArr;
	}
	
	public void append (Signature sig){
		byte[] newArr = new byte[this.bytes.length + sig.getBytes().length];
		
		System.arraycopy(this.bytes, 0, newArr, 0, this.bytes.length);
		System.arraycopy(sig.getBytes(), 0, newArr, this.bytes.length, sig.getBytes().length);
		
		this.bytes = newArr;
	}
	
}
