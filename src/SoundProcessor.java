import java.io.*;
import javax.sound.sampled.*;
import java.util.*;

//TODO: Convert byte arrays to float arrays in preparation for pitch detection

public class SoundProcessor{
    private ArrayList<AudioInputStream> fileStreams;
    private ArrayList<File> files;
    private int bpm;
    private ArrayList<Signature> signaturesMaster;
    private ArrayList<Signature> tailsMaster;
    private ArrayList<ArrayList<Signature>> signatures;
    private ArrayList<ArrayList<Signature>> tails;
    private ArrayList<AudioFormat> afs;
    private ArrayList<AudioFileFormat> affs;
    
    public SoundProcessor(ArrayList<AudioInputStream> fileStreams, ArrayList<File> files, int bpm){
        this.fileStreams = fileStreams;
        this.files = files;
        this.bpm = bpm;
        signatures = new ArrayList<ArrayList<Signature>>();
        tails = new ArrayList<ArrayList<Signature>>();
        for (int i = 0; i < 8; i++){
        	signatures.add(new ArrayList<Signature>(15));
        	tails.add(new ArrayList<Signature>(15));
        }
        
        signaturesMaster = new ArrayList<Signature>();
        tailsMaster = new ArrayList<Signature>();
        
        afs = new ArrayList<AudioFormat>();
        affs = new ArrayList<AudioFileFormat>();
    }
    
    public ArrayList<NoteList> process(){ // NoteList for 8 lanes, any number of NoteList arrays for any number of measures
            ArrayList<NoteList> noteLists = new ArrayList<NoteList>();
			double[] byteArraySize = new double[fileStreams.size()];
			byte[] rawChunk;
			float[] chunk;
			int[] newChunkFirstHalf = new int[fileStreams.size()];
			int[] prevChunkSecondHalf = new int[fileStreams.size()];
			double sixteenthLength;
			boolean[] foundNotes = new boolean[fileStreams.size()];
			int[] lastNote = new int[fileStreams.size()]; // keeps track of the index of the last note confirmed in each measure
			int[] lastMeasure = new int[fileStreams.size()]; // keeps track of the measure where the last note was confirmed lane
			
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
			while(byteArraySize[i] % 4.0 >= 0.5)
				byteArraySize[i] += 0.5;
			System.out.println("Byte array size for file " + i + " is " + (int)byteArraySize[i]);
			}
			
			boolean checkEOF = false;
			int[] n = new int[fileStreams.size()];
			
			for(int measure = 0; !checkEOF; measure++){ // !checkEOF
                NoteList tempList = new NoteList();
                noteLists.add(tempList);
				for(int lane = 0; lane < fileStreams.size() && lane < 35; lane++){
                    System.out.println("Measure " + measure + " in file " + lane);
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								rawChunk = new byte[(int)byteArraySize[lane]];
								//System.out.println("chunk size " + (int)byteArraySize[lane]);
                                try{
                                n[lane] = fileStreams.get(lane).read(rawChunk, 0, rawChunk.length);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    System.exit(1);
                                }
                                chunk = NumConverter.bytesToFloat(rawChunk, afs.get(lane).isBigEndian(), afs.get(lane).getSampleSizeInBits());
								if(!foundNotes[lane]){ // if it's the very first note in each lane
									int ta = getTotalAmp(chunk);
									System.out.println("Total amp for first note in lane " + lane + ": " + ta);
									if(ta >= 80){
										foundNotes[lane] = true;
                                        addSignature(chunk, lane);
                                        System.out.println("Adding note of sample " + signaturesMaster.size());
                                        tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                        System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                        lastNote[lane] = tempList.getList().size()-1;
                                        lastMeasure[lane] = measure;
                                    }
								}
								
								else{
									newChunkFirstHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
									//newChunkFirstHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length));
									//System.out.println("ncfh: " + newChunkFirstHalf[lane]);
                                    if (lane == 1) System.out.println("measure: " + measure + " lane: " + lane + " 16th interval: " + tokens +
                                    		" ncfh: " + newChunkFirstHalf[lane] + " pcsh: " + prevChunkSecondHalf[lane] +
                                    		" th: " + (int)(newChunkFirstHalf[lane] * (0.92) - prevChunkSecondHalf[lane]) + "/" +  (-(newChunkFirstHalf[lane] - prevChunkSecondHalf[lane])*0.15));
									// TODO: fix > threshold
                                    if (((newChunkFirstHalf[lane] * (0.92)) - prevChunkSecondHalf[lane]) > -(newChunkFirstHalf[lane] - prevChunkSecondHalf[lane])*0.15 && newChunkFirstHalf[lane] > 100 ){
                                    	System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    	int[] sigcheck = compareSignatures(chunk, lane);
                                        
                                    	if (sigcheck[0] < 0){
                                    		System.out.println("Adding new signature and cutting off previous signature");
                                        	tails.get(lane).get(tails.get(lane).size()-1).setComplete(true);
                                    		addSignature(chunk, lane);
                                    		System.out.println("???Adding note of sample " + signaturesMaster.size());
                                    		tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                    	}
                                    	else{
                                    		System.out.println("Signature match! Setting signature " + (signatures.get(lane).size()-1) + " to true");
                                        	tails.get(lane).get(tails.get(lane).size()-1).setComplete(true);
                                        	int trueIndex = signaturesMaster.indexOf(signatures.get(sigcheck[0]).get(sigcheck[1]));
                                    		tempList.addNote(new Note(tokens, lane, trueIndex+1));
                                    	}
                                    	lastNote[lane] = tempList.getList().size()-1;
                                    	lastMeasure[lane] = measure;
                                    	/*System.out.println("Tails for lane " + lane + ": ");
                                		for(Signature tailItem : tails.get(lane))
                                			System.out.println(tailItem.isComplete() + " ");
                                    	*/
									}
                                    
                                    else{
                                    	if(newChunkFirstHalf[lane] != 0){
                                    		int cutoff = Collections.indexOfSubList(Arrays.asList(chunk), Arrays.asList(new byte[8])); // look for a series of 4 empty 16bit values
                                    		int sigMasterIndex =  tempList.getList().get(tempList.getList().size()-1).getSample()-1;
                                    		System.out.println("Last note of the lane is " + sigMasterIndex + " (lane " + signaturesMaster.get(sigMasterIndex).getLane() + ") of " + signaturesMaster.size() + "... now looking for it in signatures of lane " + lane + ", size " + signatures.get(lane).size());
                                			int sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(sigMasterIndex));
                                    		
                                			//TODO: If sigIndex is -1, it has found a false positive in another lane.
                                			
                                			System.out.println(sigIndex);
                                    		if (!tails.get(lane).get(sigIndex).isComplete()){
                                    			//byte[] tempChunk = Arrays.copyOfRange(chunk, 0, cutoff);
                                    			Signature masterTail = tailsMaster.get(sigMasterIndex);
                                    			Signature appendee;
                                    			System.out.println("Appending tail to signature " + sigMasterIndex + ". Cutoff: " + cutoff);
                                    			if (cutoff == -1){
                                    				//System.out.println("Tail not yet complete");
                                    				appendee = new Signature(chunk, lane);
                                    			}
                                    			else{
                                    				appendee = new Signature(Arrays.copyOf(chunk, cutoff), lane);
                                        			//System.out.println("Tail complete!");
                                        			masterTail.setComplete(true);
                                    			}
                                    			masterTail.append(appendee);
                                    			
                                    			//System.out.println("Tail is now " + newTail[0] + " " + newTail[newTail.length-1]);
                                    			tailsMaster.set(sigMasterIndex, masterTail);
                                    			tails.get(lane).set(sigIndex, masterTail);
                                    			//System.out.println("Indices " + sigMasterIndex + " " + sigIndex + " are now set to " + tailsMaster.get(sigMasterIndex).isComplete() + " " + tails.get(lane).get(sigIndex).isComplete());
                                    		}
                                    	}
                                    }
									
								}
								prevChunkSecondHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, (int)(chunk.length/2), (int)chunk.length));
								//prevChunkSecondHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length));
                                
								
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
			if((b[i] > 0 && b[i] > b[i+1]) || (b[i] < 0 && b[i] < b[i+1]) )
				total += Math.abs(b[i]);
		}
		//System.out.println("Total amp is: " + total);
		return total;
	}
	
	public int getTotalAmp(float[] f){
		float total = 0;
		
		for(int i = 0; i < f.length-1; i++){
			if((f[i] > 0 && f[i] > f[i+1]) || (f[i] < 0 && f[i] < f[i+1]) )
				total += Math.abs(f[i]);
		}
		//System.out.println("Total amp is: " + (int)total);
		return (int)total;
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
	
	public ArrayList<ArrayList<Signature>> getSignatures(){
		return this.signatures;
	}
	
	public ArrayList<Signature> getSignaturesMaster(){
		return this.signaturesMaster;
	}
	
	public ArrayList<ArrayList<Signature>> getTails(){
		return this.tails;
	}
	
	public ArrayList<Signature> getTailsMaster(){
		return this.tailsMaster;
	}
	
	public void addSignature(float[] chunk, int lane){
		//byte[] newSig = new byte[chunk.length];
        //System.arraycopy(chunk, 0, newSig, 0, chunk.length);
		Signature newSig = new Signature(Arrays.copyOf(chunk, chunk.length), lane);
		Signature emptySig = new Signature(null, lane);
        signatures.get(lane).add(newSig);
        signaturesMaster.add(newSig);
        tails.get(lane).add(emptySig);
        tailsMaster.add(emptySig);
	}
	
	public int[] compareSignatures(float[] chunk, int lane){
		//for(int i = 0; i < signatures.size(); i++){
			for (int j = 0; j < signatures.get(lane).size(); j++){
				//System.out.println("Matching note with signature " + j + " in lane " + i);
				if(matchLPC(chunk, lane, j))
					return new int[]{lane,j};
			}
		//}
		return new int[]{-1,-1};
	}
	
	public boolean matchLPC(float[] chunk1, float[] chunk2){
		int lag = 16;
		float[] chunk1AC = new float[lag];
		float[] chunk2AC = new float[lag];
		float[] chunk1LPC = new float[lag-1];
		float[] chunk2LPC = new float[lag-1];
		float[] chunk1ref = new float[lag-1];
		float[] chunk2ref = new float[lag-1];
		
		Lpc.autocorr(chunk1, chunk1AC, lag, chunk1.length);
		Lpc.autocorr(chunk2, chunk2AC, lag, chunk2.length);
		
		Lpc.wld(chunk1LPC, chunk1AC, chunk1ref, lag-1);
		Lpc.wld(chunk2LPC, chunk2AC, chunk2ref, lag-1);
		
		float sum = 0;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunk1LPC[k] - chunk2LPC[k]), 2);
		}
		

		System.out.println("Sum of LPC is: " + sum + ", returns " + (sum<1));
		
		//TODO: Check pitch if 0.5 < sum < 1
		
		return sum < 1;
	}
	
	public boolean matchLPC(float[] chunk, int i, int j){
		int lag = 16;
		float[] chunkAC = new float[lag];
		float[] sigAC = new float[lag];
		float[] chunkLPC = new float[lag-1];
		float[] sigLPC = new float[lag-1];
		float[] chunkref = new float[lag-1];
		float[] sigref = new float[lag-1];
		
		Lpc.autocorr(chunk, chunkAC, lag, chunk.length);
		Lpc.autocorr(signatures.get(i).get(j).getFloats(), sigAC, lag, signatures.get(i).get(j).getFloats().length);
		
		Lpc.wld(chunkLPC, chunkAC, chunkref, lag-1);
		Lpc.wld(sigLPC, sigAC, sigref, lag-1);
		
		/*for(int x = 0; x < lag-1; x++)
			System.out.print(chunkLPC[x] + "|");
		
		System.out.println();
		
		for(int x = 0; x < lag-1; x++)
			System.out.print(sigLPC[x] + "|");
		*/
		System.out.println();
		
		float sum = 0;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunkLPC[k] - sigLPC[k]), 2);
		}
		
		System.out.println("Sum of LPC between current note and signature (" + i + "," + j + ") is: " + sum + ", returns " + (sum<1));
		
		//TODO: Check pitch if 0.005 < sum < 0.01
		
		return (sum < 1);
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
    
    public AudioInputStream getAIS(Signature sig, int lane){
    	byte[] bytes = NumConverter.floatsToByte(sig.getFloats(), afs.get(lane).isBigEndian(), afs.get(lane).getSampleSizeInBits());
    	ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    	AudioInputStream outputAIS = new AudioInputStream(bais, afs.get(lane), bytes.length / afs.get(lane).getFrameSize());
    	return outputAIS;
    }
    
}