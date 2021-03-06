import java.io.*;
import javax.sound.sampled.*;
import java.util.*;

public class SoundProcessor{
    private ArrayList<AudioInputStream> fileStreams;
    private ArrayList<File> files;
    private int bpm;
    private int npIndex;
    private ArrayList<Signature> signaturesMaster;
    private ArrayList<Signature> tailsMaster;
    private ArrayList<ArrayList<Signature>> signatures;
    private ArrayList<ArrayList<Signature>> tails;
    private ArrayList<AudioFormat> afs;
    private ArrayList<AudioFileFormat> affs;
	private ACPitchDetector pd;
    boolean debug = false;
    
    public SoundProcessor(ArrayList<AudioInputStream> fileStreams, ArrayList<File> files, int bpm, int npIndex){
        this.fileStreams = fileStreams;
        this.files = files;
        this.bpm = bpm;
        this.npIndex = npIndex;
        this.pd = new ACPitchDetector();
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
			float[][] prevChunk = new float[fileStreams.size()][];
			int[] ncfhEnergy = new int[fileStreams.size()];
			int[] pcshEnergy = new int[fileStreams.size()];
			int[] ncfhDensity = new int[fileStreams.size()];
			int[] pcshDensity = new int[fileStreams.size()];
			double sixteenthLength;
			boolean[] foundNotes = new boolean[fileStreams.size()];
			int[] lastNote = new int[fileStreams.size()]; // keeps track of the index of the last note confirmed in each measure
			int[] lastMeasure = new int[fileStreams.size()]; // keeps track of the measure where the last note was confirmed lane
			boolean[] delay = new boolean[fileStreams.size()];
			boolean delayFlip = false;
			boolean[] fileStatus = new boolean[fileStreams.size()];
			boolean allEOF = false;
			float[] pitchdiffres = new float[]{0,0,0};
			
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
			System.out.println("Length of 16th note is " + sixteenthLength + "s");
			
			// formulae for bytes to read:
			// bytes = seconds * sample rate * channels * (bits per sample / 8)
			// OR 
			// bytes = seconds * sample rate * frame size
			
			for(int i = 0; i < fileStreams.size(); i++){
			byteArraySize[i] = sixteenthLength * afs.get(i).getSampleRate() * afs.get(i).getFrameSize();
			byteArraySize[i] += 4.0 - (byteArraySize[i]%4.0);
			System.out.println("Byte array size for file " + i + " is " + (int)byteArraySize[i]);
			}
			
			int[] n = new int[fileStreams.size()];
			
			for(int measure = 0; !allEOF; measure++){ // !checkEOF
                NoteList tempList = new NoteList();
                noteLists.add(tempList);
				for(int lane = 0; lane < fileStreams.size() && lane < 35; lane++){
                    System.out.println("Measure " + measure + " in file " + lane);
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								delayFlip = false;
								rawChunk = new byte[(int)byteArraySize[lane]];
                                try{
                                n[lane] = fileStreams.get(lane).read(rawChunk, 0, rawChunk.length);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    System.exit(1);
                                }
                                chunk = NumConverter.bytesToFloat(rawChunk, afs.get(lane).isBigEndian(), afs.get(lane).getSampleSizeInBits());
								if(!foundNotes[lane]){ // if it's the very first note in each lane
									int ta = getTotalEnergy(chunk);
									if(ta >= 80){
										foundNotes[lane] = true;
                                        addSignature(chunk, lane);
                                        tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                        lastNote[lane] = tempList.getList().size()-1;
                                        prevChunk[lane] = Arrays.copyOf(chunk, chunk.length);
                                        lastMeasure[lane] = measure;
                                    }
								}
								
								else{
									ncfhEnergy[lane] = getTotalEnergy(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
									if (lane < npIndex){
										ncfhDensity[lane] = getDensity(Arrays.copyOfRange(chunk, 0, chunk.length/2-1));
										//System.out.println("Densities: " + ncfhDensity[lane] + " " + pcshDensity[lane]);
									}
									
									boolean cond1, cond2, cond3;
									if (lane < npIndex){
										cond1 = (pcshEnergy[lane] != 0 && ( ((double)ncfhEnergy[lane]/(double)pcshEnergy[lane]) > 1.55));
										cond2 = (ncfhEnergy[lane] > 100 && pcshEnergy[lane] == 0);
										cond3 = (ncfhEnergy[lane] > 50 && pcshEnergy[lane] > 25 && pcshDensity[lane] != 0 && ((double)ncfhDensity[lane]/(double)pcshDensity[lane]) >= 2.0);
									}
									else{
										cond1 = (ncfhEnergy[lane] > 50 && pcshEnergy[lane] != 0 && ( ((double)ncfhEnergy[lane]/(double)pcshEnergy[lane]) > 2.0));
										cond2 = (ncfhEnergy[lane] > 60 && pcshEnergy[lane] == 0);
										pitchdiffres = pd.getPitchDiff(getAIS(chunk, lane), getAIS(prevChunk[lane], lane), afs.get(lane), afs.get(lane));
										//System.out.println("Pitch diff: " + pitchdiffres[0] + " " + pitchdiffres[1] + " " + pitchdiffres[2]);
										
										cond3 = (pitchdiffres[0] < 5000 && pitchdiffres[0] != -1.0f && pitchdiffres[2] > (pitchdiffres[0]/34.4));
										if (delay[lane]) cond3 = true;
										if (pitchdiffres[0] == -1.0f && (ncfhEnergy[lane] > pcshEnergy[lane]) && !delay[lane]){
											delay[lane] = true;
											delayFlip = true;
											prevChunk[lane] = Arrays.copyOf(chunk, chunk.length);
											if (debug)System.out.println("Found delay, setting previous signature tail to true");
											tails.get(lane).get(tails.get(lane).size() - 1).setComplete(true);
										}
									}
									if (debug){
										System.out.print("Cond 1: " + cond1);
										System.out.print(" Cond 2: " + cond2);
										System.out.println(" Cond 3: " + cond3);
									}
									
                                    if (cond1 || cond2 || cond3){
                                    	if (debug)
                                    		System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    	if (lane < npIndex) {
                                    		int[] sigcheck = compareSignatures(chunk, lane);
                                    		if (sigcheck[0] < 0) {
                                    			tails.get(lane).get(tails.get(lane).size() - 1).setComplete(true);
                                    			addSignature(chunk, lane);
                                    			tempList.addNote(new Note(tokens, lane, signaturesMaster.size()));
                                    			lastNote[lane] = signaturesMaster.size();
                                    		} else {
                                    			tails.get(lane).get(tails.get(lane).size() - 1).setComplete(true);
                                    			int trueIndex = signaturesMaster
                                    					.indexOf(signatures.get(sigcheck[0]).get(sigcheck[1])) + 1;
                                    			lastNote[lane] = trueIndex;
                                    			tempList.addNote(new Note(tokens, lane, trueIndex));
                                    		} 
                                    	}
                                    	else{
                                    		if (delayFlip)
                                    			continue;
                                    		
                                    		int[] sigcheck1, sigcheck2;
                                    		if (delay[lane]){
                                    			sigcheck1 = comparePitches(prevChunk[lane], lane);
                                    			sigcheck2 = compareSignatures(prevChunk[lane], lane);
                                    		}
                                    		else{ 
                                    			sigcheck1 = comparePitches(chunk, lane);
                                    			sigcheck2 = compareSignatures(chunk, lane);
                                    		}
                                    		if(debug)System.out.println("sigcheck1: " + sigcheck1[0] + "|" + sigcheck1[1] + " sigcheck2: " + sigcheck2[0] + "|" + sigcheck2[1]);
                                    		if (sigcheck1[0] < 0 || sigcheck2[0] < 0){
	                                    		tails.get(lane).get(tails.get(lane).size() - 1).setComplete(true);
	                                    		if (delay[lane]){
	                                    			addSignature(prevChunk[lane], lane);
	                                    			Signature mt = tailsMaster.get(signaturesMaster.size()-1);
		                                    		Signature ap = new Signature(Arrays.copyOf(chunk, chunk.length), lane);
	                                    			//mt.setComplete(true);
	                                    			mt.append(ap);
		                                    		tailsMaster.set(signaturesMaster.size()-1, mt);
	                                    			tails.get(lane).set(tails.get(lane).size()-1, mt);
	                                    		}
	                                    		else addSignature(chunk, lane);
	                                    		tempList.addNote(new Note((delay[lane] ? tokens-1 : tokens), lane, signaturesMaster.size()));
	                                    		lastNote[lane] = signaturesMaster.size();
                                    		}
                                    		else{
                                    			tails.get(lane).get(tails.get(lane).size() - 1).setComplete(true);
                                    			int trueIndex = signaturesMaster.indexOf(signatures.get(sigcheck1[0]).get(sigcheck1[1])) + 1;
                                    			lastNote[lane] = trueIndex;
                                    			tempList.addNote(new Note((delay[lane] ? tokens-1 : tokens), lane, trueIndex));
                                    		}
                                    		delay[lane]=false;
                                    	}
                                    	if (!delay[lane])
											prevChunk[lane] = Arrays.copyOf(chunk, chunk.length);
                                    	lastMeasure[lane] = measure;
									}
                                    
                                    else{
                                    	if(ncfhEnergy[lane] != 0){
                                    		int cutoff = Collections.indexOfSubList(Arrays.asList(chunk), Arrays.asList(new float[4])); // look for a series of 4 empty float values
	                                    	int sigIndex = 0;
	                                    	int sigMasterIndex = lastNote[lane]-1;
                                    		if (tempList.getList().size() > 0){
                                    			sigMasterIndex =  tempList.getList().get(tempList.getList().size()-1).getSample()-1;
                                    			if (signaturesMaster.get(sigMasterIndex).getLane() != lane)
                                    				sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(lastNote[lane]-1));
                                    			else
	                                				sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(sigMasterIndex));
                                    		}
	                                    	else {
	                                    		sigIndex = signatures.get(lane).indexOf(signaturesMaster.get(lastNote[lane]-1));
	                                    	}
                                    		
                                    		if (!tails.get(lane).get(sigIndex).isComplete()){
                                    			Signature masterTail = tailsMaster.get(sigMasterIndex);
                                    			Signature appendee;
                                    			if (cutoff == -1){
                                    				appendee = new Signature(chunk, lane);
                                    			}
                                    			else{
                                    				appendee = new Signature(Arrays.copyOf(chunk, cutoff), lane);
                                        			masterTail.setComplete(true);
                                    			}
                                    			masterTail.append(appendee);
                                    			
                                    			tailsMaster.set(sigMasterIndex, masterTail);
                                    			tails.get(lane).set(sigIndex, masterTail);
                                    		}
                                    	}
                                    }
									
								}
								pcshEnergy[lane] = getTotalEnergy(Arrays.copyOfRange(chunk, (int)(chunk.length/2), (int)chunk.length));
								if (lane < npIndex)
									pcshDensity[lane] = getDensity(Arrays.copyOfRange(chunk, (int)(chunk.length/2), (int)chunk.length));
						}
					}
                }
                allEOF = checkIfEnd(byteArraySize, fileStreams.size(), fileStatus);
            }
        return noteLists;
    }
    	
	public int getTotalEnergy(byte[] b){
		int total = 0;
		for(int i = 0; i < b.length; i++){
			if((b[i] > 0 && b[i] > b[i+1]) || (b[i] < 0 && b[i] < b[i+1]) )
				total += Math.abs(b[i]);
		}
		return total;
	}
	
	public int getTotalEnergy(float[] f){
		float total = 0;
		
		for(int i = 0; i < f.length-1; i++){
			if((f[i] > 0 && f[i] > f[i+1]) || (f[i] < 0 && f[i] < f[i+1]) )
				total += Math.abs(f[i]);
		}
		return (int)total;
	}
	
	public int getDensity(byte[] b){
		int total = 0;
		
		for(int i = 0; i < b.length-1; i++){
			if((b[i] > 0 && b[i] > b[i+1]) || (b[i] < 0 && b[i] < b[i+1]) )
				total++;
		}
		return total;
	}
	
	public int getDensity(float[] f){
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
	
	public boolean checkIfEnd(double[] byteArraySize, int filenum, boolean[] fileStatus){
		for(int i = 0; i < filenum; i++){
			try{
				
				if (fileStreams.get(i).available() < byteArraySize[i])
					fileStatus[i] = true;
				else
					fileStatus[i] = fileStatus[i] || false;
				} catch (Exception e){
					e.printStackTrace();
					System.exit(1);
			}
		}
		for (int i = 0; i < fileStatus.length; i++){
			if (fileStatus[i] == false)
				return false;
		}
		return true;
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
				if(matchLPC(chunk, lane, j))
					return new int[]{lane,j};
				else if (lane < npIndex) {
					if (debug)System.out.println("Comparing current chunk to signature " + j + " in lane " + lane);
					boolean nr = matchDTW(chunk, signatures.get(lane).get(j).getFloats());
					if(nr) return new int[]{lane, j};
				}
			}
		//}
		return new int[]{-1,-1};
	}
	
	public int[] comparePitches(float[] chunk, int lane){
		for (int j = 0; j < signatures.get(lane).size(); j++){
			float[] pitchdiffres = pd.getPitchDiff(getAIS(chunk, lane), getAIS(signatures.get(lane).get(j).getFloats(), lane), afs.get(lane), afs.get(lane));
			if (pitchdiffres[2] < (pitchdiffres[0]/34.4))
				return new int[]{lane, j};
		}
		
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
		
		float chunk1MSE = Lpc.wld(chunk1LPC, chunk1AC, chunk1ref, lag-1);
		float chunk2MSE = Lpc.wld(chunk2LPC, chunk2AC, chunk2ref, lag-1);
		
		float sum = 0;
		float threshold = 1.0f;
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunk1LPC[k] - chunk2LPC[k]), 2);
		}
		
		boolean result = sum<threshold;
		if (debug){
			System.out.println("chunk1: " + chunk1MSE);
			System.out.println("chunk2: " + chunk2MSE);
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
		}

		return result;
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
		
		float chunkMSE = Lpc.wld(chunkLPC, chunkAC, chunkref, lag-1);
		float sigMSE = Lpc.wld(sigLPC, sigAC, sigref, lag-1);
		
		float sum = 0;
		float threshold = (i < npIndex ? 1.0f : 1.5f);
		
		for (int k = 0; k < lag-1; k++){
			sum += Math.pow((chunkLPC[k] - sigLPC[k]), 2);
		}
		
		boolean result = sum<threshold;
		
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
		
		return result;
	}
	
	public boolean matchDTW(float[] chunkAC, float[] sigAC){
		float shortest = getMin(chunkAC, sigAC);
		if (shortest < 0.002f) return true;
		return false;
	}
	
	public float getMin(float[] chunkAC, float[] sigAC){
		float[][] distMatrix = new float[chunkAC.length][sigAC.length];
		
		for(int i = 0; i < chunkAC.length; i++){
			for(int j = 0; j < sigAC.length; j++){
				distMatrix[i][j] = (float)(Math.pow((sigAC[j]-chunkAC[i]), 2));
			}
		}
		
		float[][] accumDist = new float[distMatrix.length][distMatrix[0].length];
		float sum = 0;
		
		accumDist[0][0] = distMatrix[0][0];
		for (int i = 1; i < distMatrix[0].length; i++){
			accumDist[0][i] = distMatrix[0][i] + distMatrix[0][i-1];
		}
		
		for (int i = 1; i < distMatrix.length; i++){
			accumDist[i][0] = distMatrix[i][0] + distMatrix[i-1][0];
		}
		
		for (int i = 1; i < distMatrix.length; i++){
			for(int j = 1; j < distMatrix[0].length; j++){
				accumDist[i][j] = Math.min(accumDist[i-1][j-1], Math.min(accumDist[i-1][j], accumDist[i][j-1])) + distMatrix[i][j];
			}
		}
		
		ArrayList<Integer> pathX = new ArrayList<Integer>((int)Math.sqrt(accumDist.length * accumDist[0].length));
		ArrayList<Integer> pathY = new ArrayList<Integer>((int)Math.sqrt(accumDist.length * accumDist[0].length));
		
		int i = accumDist.length-1;
		int j = accumDist[0].length-1;
		
		pathX.add(j);
		pathY.add(i);
		
		while(i > 0 || j > 0){
			if (i==0) j--;
			else if (j==0) i--;
			else{
				float minf = Math.min(accumDist[i-1][j-1], Math.min(accumDist[i-1][j], accumDist[i][j-1]));
				if (accumDist[i-1][j] == minf) i--;
				else if (accumDist[i][j-1] == minf) j--;
				else{
					i--;
					j--;
				}
			}
			pathY.add(i);
			pathX.add(j);
		}
		
		pathX.add(0);
		pathY.add(0);
		
		for(i = 0; i < pathX.size(); i++){
			sum+=distMatrix[pathY.get(i)][pathX.get(i)];
		}
		
		if (debug)System.out.println("Sum: " +sum+ " Path length: " + pathX.size() + "returning " + sum/pathX.size());
		
		return sum/pathX.size();
	}
	
    
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
    
    public AudioInputStream getAIS(float[] fs, int lane){
    	byte[] bytes = NumConverter.floatsToByte(fs, afs.get(lane).isBigEndian(), afs.get(lane).getSampleSizeInBits());
    	ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    	AudioInputStream outputAIS = new AudioInputStream(bais, afs.get(lane), bytes.length / afs.get(lane).getFrameSize());
    	return outputAIS;
    }
    
}