import java.io.*;
import javax.sound.sampled.*;
import java.util.*;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

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
    boolean debug = false;
    
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
			int[] ncfhEnergy = new int[fileStreams.size()];
			int[] pcshEnergy = new int[fileStreams.size()];
			int[] ncfhFreq = new int[fileStreams.size()];
			int[] pcshFreq = new int[fileStreams.size()];
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
									ncfhEnergy[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
									ncfhFreq[lane] = getFreq(Arrays.copyOfRange(chunk, 0, chunk.length/2-1));
									
									//ncfhEnergy[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length));
									//System.out.println("ncfh: " + ncfhEnergy[lane]);
                                    /*if (lane == 0) System.out.println("measure: " + measure + " lane: " + lane + " 16th interval: " + tokens +
                                    		" ncfh: " + ncfhEnergy[lane] + " pcsh: " + pcshEnergy[lane] +
                                    		" th: " + (int)(ncfhEnergy[lane] * (0.92) - pcshEnergy[lane]) + "/" +  (-(ncfhEnergy[lane] - pcshEnergy[lane])*0.15));
									*/
									System.out.println("m" + measure + "l" + lane + "p" + tokens + " ncfh: " + ncfhEnergy[lane] + " pcsh: " + pcshEnergy[lane] + " " + Math.abs((double)ncfhEnergy[lane]/(double)pcshEnergy[lane]));
									System.out.println("freqs: " + ncfhFreq[lane] + " " + pcshFreq[lane]);
									boolean cond1 = (pcshEnergy[lane] != 0 && ( ((double)ncfhEnergy[lane]/(double)pcshEnergy[lane]) > 1.55));
									boolean cond2 = (ncfhEnergy[lane] > 100 && pcshEnergy[lane] == 0);
									boolean cond3 = (ncfhEnergy[lane] > 50 && pcshEnergy[lane] > 25 && pcshFreq[lane] != 0 && ((double)ncfhFreq[lane]/(double)pcshFreq[lane]) >= 2.0);
									System.out.print("Cond 1: " + cond1);
									System.out.print(" Cond 2: " + cond2);
									System.out.println(" Cond 3: " + cond3);
									// TODO: fix thresholds
                                    //if (((ncfhEnergy[lane] * (0.92)) - pcshEnergy[lane]) > -(ncfhEnergy[lane] - pcshEnergy[lane])*0.15 && ncfhEnergy[lane] > 100 ){
                                    if (cond1 || cond2 || cond3){
                                    	System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    	int[] sigcheck = compareSignatures(chunk, lane);
                                        
                                    	if (sigcheck[0] < 0){
                                    		System.out.println("Adding new signature and cutting off previous signature");
                                        	tails.get(lane).get(tails.get(lane).size()-1).setComplete(true);
                                    		addSignature(chunk, lane);
                                    		System.out.println("???Adding note of sample " + signaturesMaster.size());
                                    		tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                    		lastNote[lane] = signaturesMaster.size();
                                    	}
                                    	else{
                                    		System.out.println("Signature match! Setting signature " + (signatures.get(lane).size()-1) + " to true");
                                        	tails.get(lane).get(tails.get(lane).size()-1).setComplete(true);
                                        	int trueIndex = signaturesMaster.indexOf(signatures.get(sigcheck[0]).get(sigcheck[1])) + 1;
                                        	lastNote[lane] = trueIndex;
                                        	System.out.println("added note index " + trueIndex);
                                    		tempList.addNote(new Note(tokens, lane, trueIndex));
                                    	}
                                    	lastMeasure[lane] = measure;
                                    	/*System.out.println("Tails for lane " + lane + ": ");
                                		for(Signature tailItem : tails.get(lane))
                                			System.out.println(tailItem.isComplete() + " ");
                                    	*/
									}
                                    
                                    else{
                                    	if(ncfhEnergy[lane] != 0){
                                    		int cutoff = Collections.indexOfSubList(Arrays.asList(chunk), Arrays.asList(new float[4])); // look for a series of 4 empty float values
	                                    	int sigIndex = 0;
	                                    	int sigMasterIndex = lastNote[lane]-1;
                                    		if (tempList.getList().size() > 0){
                                    			sigMasterIndex =  tempList.getList().get(tempList.getList().size()-1).getSample()-1;
	                                    		System.out.println("Last note of the lane is " + sigMasterIndex + " (lane " + signaturesMaster.get(sigMasterIndex).getLane() + ") of " + signaturesMaster.size() + "... now looking for it in signatures of lane " + lane + ", size " + signatures.get(lane).size());
	                                			sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(sigMasterIndex));
                                    		}
	                                    	else {
	                                    		sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(lastNote[lane]-1));
	                                    	}
                                			
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
								pcshEnergy[lane] = getTotalAmp(Arrays.copyOfRange(chunk, (int)(chunk.length/2), (int)chunk.length));
								pcshFreq[lane] = getFreq(Arrays.copyOfRange(chunk, (int)(chunk.length/2), (int)chunk.length));
								//pcshEnergy[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length));
                                
								
								//if (pcshEnergy[lane] < 20000 && pcshEnergy[lane] > 5000 && lane == 2)
                                //    System.out.println("pcsh at this point is: " + pcshEnergy[lane]);
                                /*if (pcshEnergy < 20000 && pcshEnergy > 1000 && lane == 2){
                                    for (int z = 0; z < 4; z++){
                                        //System.out.println("Chunk length: " + chunk.length);
                                        //System.out.println("Position in chunk: " + (((int)byteArraySize[lane]/2) + (z*(int)byteArraySize[lane]/8)));
                                        System.out.println(getTotalAmp(Arrays.copyOfRange(chunk, chunk.length/2 + z*chunk.length/8, chunk.length/2 + (z+1)*chunk.length/8)));
                                    }
                                }*/
						}
					}
                }
                checkEOF = checkIfEnd(byteArraySize, fileStreams.size());
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
	
	public int getFreq(byte[] b){
		int total = 0;
		
		for(int i = 0; i < b.length-1; i++){
			if((b[i] > 0 && b[i] > b[i+1]) || (b[i] < 0 && b[i] < b[i+1]) )
				total++;
		}
		return total;
	}
	
	public int getFreq(float[] f){
		int total = 0;
		boolean mode = (f[0] < f[1]);
		
		for(int i = 0; i < f.length-1; i++){
			/*if((f[i] > 0 && f[i] > f[i+1]) || (f[i] < 0 && f[i] < f[i+1]) )
				total++;
			*/
			if ((mode && f[i] > f[i+1] && f[i] > 0) || (!mode && f[i] < f[i+1] && f[i] < 0)){
				total++;
				mode = !mode;
			}
		}
		return total; 
	}
	
	public boolean checkIfEnd(double[] byteArraySize, int filenum){
		boolean x = false;
		for(int i = 0; i < filenum; i++){
			try{
				//System.out.println("byte array size " + byteArraySize[i] + " available " + fileStreams.get(i).available());
				if (fileStreams.get(i).available() < byteArraySize[i])
					x = x || true;
				else
					x = x || false;
				} catch (Exception e){
					e.printStackTrace();
					System.exit(1);
			}
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
	
	
	// TODO: Detect pitch with Tarsos YIN
	// TODO: Account for drift?
	public boolean matchLPC(float[] chunk1, float[] chunk2){
		int lag = 16;
		float threshold = 1.0f;
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
		
		boolean result = sum<threshold;
		System.out.println("Sum of LPC is: " + sum + ", returns " + (result));
		if (!result){
			for (int k = 0; k < lag-1; k++){
				System.out.print(chunk1LPC[k] + "|");
			}
			System.out.println();
			for (int k = 0; k < lag-1; k++){
				System.out.print(chunk2LPC[k] + "|");
			}
			System.out.println();
		}

		return result;
	}
	
	public boolean matchLPC(float[] chunk, int i, int j){
		int lag = 16;
		float threshold = 1.0f;
		float[] chunkAC = new float[lag];
		float[] sigAC = new float[lag];
		float[] chunkLPC = new float[lag-1];
		float[] sigLPC = new float[lag-1];
		float[] chunkref = new float[lag-1];
		float[] sigref = new float[lag-1];
		
		Lpc.autocorr(chunk, chunkAC, lag, chunk.length);
		Lpc.autocorr(signatures.get(i).get(j).getFloats(), sigAC, lag, signatures.get(i).get(j).getFloats().length);
		
		float chunkMSE = Lpc.wld(chunkLPC, chunkAC, chunkref, lag-1);
		float sigMSE = Lpc.wld(sigLPC, sigAC, sigref, lag-1);
		
		float sum = 0;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunkLPC[k] - sigLPC[k]), 2);
		}
		
		boolean result = sum<threshold;
		
		System.out.println("Sum of LPC between current note and signature (" + i + "," + j + ") is: " + sum + ", returns " + (result));
		if (debug){
			System.out.println("chunkMSE: " + chunkMSE);
			System.out.println("sigMSE: " + sigMSE);
			if (!result){
				for (int k = 0; k < lag-1; k++){
					System.out.print(chunkLPC[k] + "|");
				}
				System.out.println();
				for (int k = 0; k < lag-1; k++){
					System.out.print(sigLPC[k] + "|");
				}
				System.out.println();
			}
		}
		return (result);
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