public class Note{

    // format: #[measure number][lane ID]:[tokens]
	// ex. #00113:2Q00002R000000000000000000000000
	// 		> measure 1, lane 3, sample 2Q at start of measure, skip two 16th notes, sample 2R, no more notes for this lane
	//
	// lane IDs in BMS:
	// 11: 1 (array index 0)
	// 12: 2 (array index 1)
	// 13: 3 (array index 2)
	// 14: 4 (array index 4)
	// 15: 5 (array index 5)
	// 16: SC (array index 3)
	// 18: 6 (array index 6)
	// 19: 7 (array index 7)
    // 
    // SOON: lanes 5-7 to be used for melodies

    private int position;
    private int interval; // 16 normal, 24 triple
    private int lane; 
    private int sample;
    
    public Note(){
        this.position = 0;
        this.interval = 16;
        this.lane = 1;
    }
    
    public Note(int position, int lane){
        this.position = position;
        this.interval = 16;
        this.lane = lane;
    }
    
    public Note(int position, int lane, int sample){
        this.position = position;
        this.interval = 16;
        this.lane = lane;
        this.sample = sample;
    }
    
    public Note(int position, int lane, int sample, int interval){
        this.position = position;
        this.lane = lane;
        this.sample = sample;
        this.interval = interval;
    }
    
    public int getPosition(){
        return this.position;
    }
    
    public int getInterval(){
        return this.interval;
    }
    
    public int getLane(){
        return this.lane;
    }
    
    public int getSample(){
    	return this.sample;
    }
    
    public void setPosition(int position){
        this.position = position;
    }
    
    public void setInterval(int interval){
        this.interval = interval;
    }
    
    public void setLane(int lane){
        this.lane = lane;
    }
    
    public void setSample(int sample){
    	this.sample = sample;
    }
    
    @Override
    public String toString(){
        return "Note | Position: " + position + " Interval: " + interval;
    }
}