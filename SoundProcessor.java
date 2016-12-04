import java.io.*;
import javax.sound.sampled.*;
import java.util.*;
import javazoom.jl.converter.*;

public class SoundProcessor{
    private ArrayList<AudioInputStream> files;
    private NoteList noteList;
    private int bpm;

    public SoundProcessor(ArrayList<AudioInputStream> files, int bpm){
        this.files = files;
        this.bpm = bpm;
        noteList = new NoteList();
    }
    
    public ArrayList<NoteList> process(){ // NoteList for 8 lanes, any number of NoteList arrays for any number of measures
            ArrayList<AudioFormat> af = new ArrayList<AudioFormat>();
            ArrayList<NoteList> noteLists = new ArrayList<NoteList>();
			double[] byteArraySize = new double[files.size()];
			byte[] chunk;
			int[] newChunkFirstHalf = {0,0,0,0,0};
			int[] prevChunkSecondHalf = {0,0,0,0,0};
			double sixteenthLength;
			
			for(int i = 0; i < files.size(); i++){
				af.add((files.get(i)).getFormat());
				System.out.println(af.get(i).toString());
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
			
			for(int i = 0; i < files.size(); i++){
			byteArraySize[i] = sixteenthLength * af.get(i).getSampleRate() * af.get(i).getFrameSize();
			System.out.println("Byte array size for file " + i + " is " + (int)byteArraySize[i]);
			}
			
			boolean checkEOF = false;
			int[] n = new int[files.size()];
			
			for(int measure = 0; !checkEOF; measure++){ // !checkEOF
                NoteList tempList = new NoteList();
				for(int lane = 0; lane < files.size(); lane++){
                    System.out.println("Measure " + measure + " in file " + lane);
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								chunk = new byte[(int)byteArraySize[lane]];
								//System.out.println("chunk size " + (int)byteArraySize[lane]);
                                try{
                                n[lane] = files.get(lane).read(chunk, 0, chunk.length);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    System.exit(1);
                                }
								if(tokens == 0 && measure == 0){ // if it's the very first note
									if(getTotalAmp(chunk) >= 100000){
                                        tempList.addNote(new Note(tokens, lane));
                                        System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    }
								}
								
								else{
									newChunkFirstHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
                                    //if (lane == 4 && measure >= 12 && measure <= 13) System.out.println("measure: " + measure + " lane: " + lane + " 16th interval: " + tokens + " ncfh: " + newChunkFirstHalf[lane] + " pcsh: " + prevChunkSecondHalf[lane] + " th: " + (int)(newChunkFirstHalf[lane] * (0.92)));
									// TODO: fix > threshold
                                    if (((newChunkFirstHalf[lane] * (0.92)) - prevChunkSecondHalf[lane]) > -(newChunkFirstHalf[lane] - prevChunkSecondHalf[lane])*0.15 && newChunkFirstHalf[lane] > 60000 ){
                                        tempList.addNote(new Note(tokens, lane));
                                        System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
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
                noteLists.add(tempList);
                checkEOF = checkIfEnd(n, files.size());
            }
        System.out.println("noteLists count: " + noteLists.size());
        return noteLists;
    }
    	
	public int getTotalAmp(byte[] b){
		int total = 0;
		for(int i = 0; i < b.length; i++)
			total += Math.abs(b[i]);
		
		return total;
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
    
    public void closeFiles(){
        try{
        while(files.size() > 0)
			if (files.get(0) != null) files.remove(0).close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}