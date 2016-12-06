import java.io.*;
import javax.sound.sampled.*;
import java.util.*;

public class SoundProcessor{
    private ArrayList<AudioInputStream> fileStreams;
    private ArrayList<File> files;
    private int bpm;
    private ArrayList<ArrayList<byte[]>> signatures;
    private ArrayList<byte[]> signaturesMaster;
    private ArrayList<ArrayList<byte[]>> tails;
    private ArrayList<byte[]> tailsMaster;
    private ArrayList<ArrayList<Boolean>> tailsCompletion; // for master
    private ArrayList<AudioFormat> afs;
    private ArrayList<AudioFileFormat> affs;
    
    public SoundProcessor(ArrayList<AudioInputStream> fileStreams, ArrayList<File> files, int bpm){
        this.fileStreams = fileStreams;
        this.files = files;
        this.bpm = bpm;
        signatures = new ArrayList<ArrayList<byte[]>>();
        tails = new ArrayList<ArrayList<byte[]>>();
        tailsCompletion = new ArrayList<ArrayList<Boolean>>();
        for (int i = 0; i < 8; i++){
        	signatures.add(new ArrayList<byte[]>(255));
        	tails.add(new ArrayList<byte[]>(255));
        	tailsCompletion.add(new ArrayList<Boolean>(255));
        }
        
        signaturesMaster = new ArrayList<byte[]>();
        tailsMaster = new ArrayList<byte[]>();
        
        afs = new ArrayList<AudioFormat>();
        affs = new ArrayList<AudioFileFormat>();
    }
    
    public ArrayList<NoteList> process(){ // NoteList for 8 lanes, any number of NoteList arrays for any number of measures
            ArrayList<NoteList> noteLists = new ArrayList<NoteList>();
			double[] byteArraySize = new double[fileStreams.size()];
			byte[] chunk;
			int[] newChunkFirstHalf = {0,0,0,0,0};
			int[] prevChunkSecondHalf = {0,0,0,0,0};
			double sixteenthLength;
			boolean[] foundNotes = new boolean[8];
			int[] lastNote = new int[8]; // keeps track of the index of the last note confirmed in each lane
			int[] lastMeasure = new int[8]; // keeps track of the measure where the last note was confirmed lane
			
			for(int i = 0; i < fileStreams.size(); i++){
				afs.add((fileStreams.get(i)).getFormat());
				try{
					affs.add(AudioSystem.getAudioFileFormat(files.get(i)));
				} catch (Exception e){
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println(afs.get(i).toString());
			}
			
			// Half note               =  120 / BPM
			// Quarter note            =   60 / BPM
			// Eighth note             =   30 / BPM
			// Sixteenth note          =   15 / BPM
			
			sixteenthLength = 15.0/bpm;
			System.out.println("Length of 16th note is " + sixteenthLength);
			
			// formulae for bytes to read:
			// bytes = seconds * sample rate * channels * (bits per sample / 8)
			// OR 
			// bytes = seconds * sample rate * frame size
			
			for(int i = 0; i < fileStreams.size(); i++){
			byteArraySize[i] = sixteenthLength * afs.get(i).getSampleRate() * afs.get(i).getFrameSize();
			while(byteArraySize[i] % 4.0 >= 0.5){
				byteArraySize[i] -= 0.5;
			}
			System.out.println("Byte array size for file " + i + " is " + (int)byteArraySize[i]);
			}
			
			boolean checkEOF = false;
			int[] n = new int[fileStreams.size()];
			
			for(int measure = 0; !checkEOF; measure++){ // !checkEOF
                NoteList tempList = new NoteList();
                noteLists.add(tempList);
				for(int lane = 0; lane < fileStreams.size() && lane < 8; lane++){
                    System.out.println("Measure " + measure + " in file " + lane);
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								chunk = new byte[(int)byteArraySize[lane]];
								//System.out.println("chunk size " + (int)byteArraySize[lane]);
                                try{
                                n[lane] = fileStreams.get(lane).read(chunk, 0, chunk.length);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    System.exit(1);
                                }
								if(!foundNotes[lane]){ // if it's the very first note in each lane
									if(getTotalAmp(chunk) >= 100000){
										foundNotes[lane] = true;
                                        addSignature(chunk, lane);
                                        tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                        System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                        lastNote[lane] = tempList.getList().size()-1;
                                        lastMeasure[lane] = measure;
                                    }
								}
								
								else{
									newChunkFirstHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
                                    //if (lane == 4 && measure >= 12 && measure <= 13) System.out.println("measure: " + measure + " lane: " + lane + " 16th interval: " + tokens + " ncfh: " + newChunkFirstHalf[lane] + " pcsh: " + prevChunkSecondHalf[lane] + " th: " + (int)(newChunkFirstHalf[lane] * (0.92)));
									// TODO: fix > threshold
                                    if (((newChunkFirstHalf[lane] * (0.92)) - prevChunkSecondHalf[lane]) > -(newChunkFirstHalf[lane] - prevChunkSecondHalf[lane])*0.15 && newChunkFirstHalf[lane] > 60000 ){
                                    	System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    	int sigcheck = compareSignatures(chunk);
                                        
                                    	if (sigcheck < 0){
                                    		System.out.println("Adding new signature");
                                    		addSignature(chunk, lane);
                                    		tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                    	}
                                    	else{
                                    		System.out.println("Signature match!");
                                    		tempList.addNote(new Note(tokens, lane, sigcheck+1));
                                    	}
                                    	lastNote[lane] = tempList.getList().size()-1;
                                    	lastMeasure[lane] = measure;
                                    	System.out.println("Previous tail complete!");
                                    	System.out.print("Tails for lane " + lane + ": ");
                                		for(boolean tailStatus : tailsCompletion.get(lane))
                                			System.out.print(tailStatus + " ");
                                    	tailsCompletion.get(lane).set(tailsCompletion.get(lane).size()-1, true);
                                    	
									}
                                    
                                    
                                    else{
                                    	if(newChunkFirstHalf[lane] != 0){
                                    		
                                    		int cutoff = Collections.indexOfSubList(Arrays.asList(chunk), Arrays.asList(new int[8])); // look for a series of 4 empty 16bit values
                                    		int sigMasterIndex =  noteLists.get(lastMeasure[lane]).getList().get(lastNote[lane]).getSample() - 1;
                                			int sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(sigMasterIndex));
                                    		
                                			System.out.print("Tails for lane " + lane + ": ");
                                			for(boolean tailStatus : tailsCompletion.get(lane))
                                				System.out.print(tailStatus + " ");
                                			
                                			//TODO: Fix tail detection
                                    		if (!tailsCompletion.get(lane).get(sigIndex)){
                                    			//byte[] tempChunk = Arrays.copyOfRange(chunk, 0, cutoff);
                                    			byte[] masterTail = tailsMaster.get(sigMasterIndex);
                                    			byte[] appendee;
                                    			System.out.println("Appending tail to signature " + sigMasterIndex + ". Cutoff: " + cutoff);
                                    			if (cutoff == -1){
                                    				System.out.println("Tail not yet complete");
                                    				appendee = chunk;
                                    			}
                                    			else{
                                    				appendee = Arrays.copyOf(chunk, cutoff);
                                        			System.out.println("Tail complete!");
                                        			tailsCompletion.get(lane).set(sigIndex, true);
                                    			}
                                    			byte[] newTail = combineArrays(masterTail, appendee);
                                    			
                                    			System.out.println("Tail is now " + newTail[0] + " " + newTail[newTail.length-1]);
                                    			tailsMaster.set(sigMasterIndex, newTail);
                                    			tails.get(lane).set(sigIndex, newTail);
                                    		}
                                    	}
                                    }
									
								}
								prevChunkSecondHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, (int)byteArraySize[lane]/2, (int)byteArraySize[lane]));
                                //if (prevChunkSecondHalf[lane] < 20000 && prevChunkSecondHalf[lane] > 5000 && lane == 2)
                                //    System.out.println("pcsh at this point is: " + prevChunkSecondHalf[lane]);
                                /*if (prevChunkSecondHalf < 20000 && prevChunkSecondHalf > 1000 && lane == 2){
                                    for (int z = 0; z < 4; z++){
                                        //System.out.println("Chunk length: " + chunk.length);
                                        //System.out.println("Position in chunk: " + (((int)byteArraySize[lane]/2) + (z*(int)byteArraySize[lane]/8)));
                                        System.out.println(getTotalAmp(Arrays.copyOfRange(chunk, chunk.length/2 + z*chunk.length/8, chunk.length/2 + (z+1)*chunk.length/8)));
                                    }
                                }*/
						}
					}
                }
                checkEOF = checkIfEnd(n, fileStreams.size());
            }
        //System.out.println("noteLists count: " + noteLists.size());
        return noteLists;
    }
    	
	public int getTotalAmp(byte[] b){
		int total = 0;
		//System.out.println("Chunk length: " + b.length);
		for(int i = 0; i < b.length; i++){
			//if (i<100) System.out.print(b[i] + " ");
			total += Math.abs(b[i]);
		}
		
		return total;
	}
	
	public byte[] combineArrays(byte[] arr1, byte[] arr2){
		byte[] newArr = new byte[arr1.length + arr2.length];
		
		System.arraycopy(arr1, 0, newArr, 0, arr1.length);
		System.arraycopy(arr2, 0, newArr, arr1.length, arr2.length);
		
		return newArr;
	}
	
	public boolean checkIfEnd(int[] n, int filenum){
		boolean x = true;
		for(int i = 0; i < filenum; i++){
			if (n[i] == -1)
				x = x && true;
			else
				x = x && false;
		}
		
		return x;
	}
	
	public ArrayList<ArrayList<byte[]>> getSignatures(){
		return this.signatures;
	}
	
	public ArrayList<byte[]> getSignaturesMaster(){
		return this.signaturesMaster;
	}
	
	public ArrayList<ArrayList<byte[]>> getTails(){
		return this.tails;
	}
	
	public ArrayList<byte[]> getTailsMaster(){
		return this.tailsMaster;
	}
	
	public void addSignature(byte[] chunk, int lane){
		//byte[] newSig = new byte[chunk.length];
        //System.arraycopy(chunk, 0, newSig, 0, chunk.length);
		byte[] newSig = Arrays.copyOf(chunk, chunk.length);
		byte[] emptySig = new byte[4];
        signatures.get(lane).add(newSig);
        signaturesMaster.add(newSig);
        tails.get(lane).add(emptySig);
        tailsMaster.add(emptySig);
    	tailsCompletion.get(lane).add(false);
	}
	
	public int compareSignatures(byte[] chunk){
		for(int i = 0; i < signatures.size(); i++){
			for (int j = 0; j < signatures.get(i).size(); j++){
				//System.out.println("Matching note with signature " + j + " in lane " + i);
				if(matchLPC(chunk, i, j))
					return i;
			}
		}
		return -1;
	}
	
	public boolean matchLPC(byte[] chunk1, byte[] chunk2){
		int lag = 60;
		double[] chunk1AC = new double[lag];
		double[] chunk2AC = new double[lag];
		double[] chunk1LPC = new double[lag-1];
		double[] chunk2LPC = new double[lag-1];
		double[] chunk1ref = new double[lag-1];
		double[] chunk2ref = new double[lag-1];
		
		LPC.autocorr(chunk1, chunk1AC, lag, chunk1.length);
		LPC.autocorr(chunk2, chunk2AC, lag, chunk2.length);
		
		LPC.wld(chunk1LPC, chunk1AC, chunk1ref, lag-1);
		LPC.wld(chunk2LPC, chunk2AC, chunk2ref, lag-1);
		
		double sum = 0;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunk1LPC[k] - chunk2LPC[k]), 2);
		}
		
		return sum < 0.01;
	}
	
	public boolean matchLPC(byte[] chunk, int i, int j){
		int lag = 60;
		double[] chunkAC = new double[lag];
		double[] sigAC = new double[lag];
		double[] chunkLPC = new double[lag-1];
		double[] sigLPC = new double[lag-1];
		double[] chunkref = new double[lag-1];
		double[] sigref = new double[lag-1];
		
		LPC.autocorr(chunk, chunkAC, lag, chunk.length);
		LPC.autocorr(signatures.get(i).get(j), sigAC, lag, signatures.get(i).get(j).length);
		
		LPC.wld(chunkLPC, chunkAC, chunkref, lag-1);
		LPC.wld(sigLPC, sigAC, sigref, lag-1);
		
		double sum = 0;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunkLPC[k] - sigLPC[k]), 2);
		}
		
		//System.out.println("Sum of LPC between current note and signature " + i + " is: " + sum);
		
		return (sum < 0.01);
	}
	
	/*
	public int compareSignatures(byte[] chunk){
		for(int i = 0; i < signatures.size(); i++){
			System.out.println("Matching note with signature " + i);
			if(matchDTW(chunk, i))
				return i;
		}
		return -1;
	}
	
	public boolean matchDTW(byte[] chunk, int sigIndex){
		int[][] distMatrix = new int[chunk.length/10][signatures.get(sigIndex).length/10];
		
		for(int i = 0; i < distMatrix.length; i++){
			int totalC = 0;
			
			for(int l = 0; l < 10; l++){
				totalC += chunk[i*10 + l];
			}
			
			for(int j = 0; j < distMatrix[0].length; j++){
				int totalS = 0;
				
				for(int k = 0; k < 10; k++){
					totalS += (signatures.get(sigIndex))[j*10 + k];
				}
				
				distMatrix[i][j] = (int) (Math.pow((totalC - totalS), 2));
			}
		}
		
		int shortest = getMin(distMatrix, 0, 0, 0, 0, 0);
		
		System.out.println("Shortest path: " + shortest);
		if (shortest < 5000) return true;
		return false;
	}
	
	public int getMin(int[][] distMatrix, int up, int right, int upright, int x, int y){
		int nextup = -1, nextright = -1, nextupright = -1;
		
		if (y < (distMatrix.length-1))
			nextup = getMin(distMatrix, up + distMatrix[y+1][x] - distMatrix[y][x], right, upright, x, y+1);
		if (x < (distMatrix[0].length-1))
			nextright = getMin(distMatrix, up, right + distMatrix[y][x+1] - distMatrix[y][x], upright, x+1, y);
		if (y < (distMatrix.length-1) && x < (distMatrix[0].length-1))
			nextupright = getMin(distMatrix, up, right, upright + distMatrix[y+1][x+1] - distMatrix[y][x], x+1, y+1);
		
		
		if (nextup == -1) return Math.min(nextright, nextupright);
		if (nextright == -1) return Math.min(nextup, nextupright);
		if (nextupright == -1) return Math.min(nextup, nextright);
		if (x == distMatrix[0].length-1 && y == distMatrix.length-1) return Math.min(up, Math.min(nextup,  nextright));
		return Math.min(nextup, Math.min(nextright, nextupright));
	}
	*/
    
    public void closefileStreams(){
        try{
        while(fileStreams.size() > 0)
			if (fileStreams.get(0) != null) fileStreams.remove(0).close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public AudioFileFormat getAFF(int lane){
    	return this.affs.get(lane);
    }
    
    public AudioInputStream getAIS(byte[] bArray, int lane){
    	ByteArrayInputStream bais = new ByteArrayInputStream(bArray);
    	AudioInputStream outputAIS = new AudioInputStream(bais, afs.get(lane),
                bArray.length / afs.get(lane).getFrameSize());
    	return outputAIS;
    }
    
}