
public class Signature {
	private float[] floats;
	private int lane;
	private boolean complete;
	
	public Signature(float[] floats, int lane) {
		this.floats = floats;
		this.lane = lane;
		this.complete = false;
	}

	public float[] getFloats() {
		return floats;
	}

	public void setFloats(float[] floats) {
		this.floats = floats;
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
	

	public void append (float[] arr1){
		float[] newArr;
		if (this.floats == null){
			this.floats = arr1;
		}
		else{
			if (arr1 != null){
				newArr = new float[this.floats.length + arr1.length];
				
				System.arraycopy(this.floats, 0, newArr, 0, this.floats.length);
				System.arraycopy(arr1, 0, newArr, this.floats.length, arr1.length);
				
				this.floats = newArr;
			}
		}
	}
	
	public void append (Signature sig){
		float[] newArr;
		
		if (this.floats == null){
			this.floats = sig.getFloats();
		}
		else {
			if (sig.getFloats() != null){
				newArr = new float[this.floats.length + sig.getFloats().length];
				System.arraycopy(this.floats, 0, newArr, 0, this.floats.length);
				System.arraycopy(sig.getFloats(), 0, newArr, this.floats.length, sig.getFloats().length);
				
				this.floats = newArr;
			}
		}
	}
	
}
