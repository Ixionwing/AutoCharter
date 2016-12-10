import java.io.*;
import javax.sound.sampled.*;
import java.util.*;

public class BMSWriter{
    private BufferedWriter writer;
    //private String bmsFileName;
    private ArrayList<String> audioFilenames;

    public BMSWriter(String fileName, ArrayList<String> audioFilenames){
        try{
            //this.bmsFileName = fileName;
            this.audioFilenames = audioFilenames;
            this.writer = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(fileName), "utf-8"));
        } catch (Exception ex){
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public void constructBaseBMS(ArrayList<String> info, int bpm){
		try {
			writer.newLine();
			writer.write("*---------------------- HEADER FIELD");
			writer.newLine();
			writer.newLine();
			writer.write("#PLAYER 1\n");
			writer.newLine();
			writer.write("#GENRE " + info.get(1));
			writer.newLine();
			writer.write("#TITLE " + info.get(2));
			writer.newLine();
			writer.write("#ARTIST " + info.get(3));
			writer.newLine();
			writer.write("#BPM " + bpm);
			writer.newLine();
			writer.write("#PLAYLEVEL " + info.get(4));
			writer.newLine();
			writer.write("#RANK 3");
			writer.newLine();
			writer.write("#STAGEFILE ");
			writer.newLine();
			writer.newLine();
			
			for(int i = 0; i < audioFilenames.size(); i++){
				writer.write("#WAV0" + (i+1) + " " + audioFilenames.get(i));
				writer.newLine();
			}
			
			for(int i = 0; i < 5; i++)
				writer.newLine();
				
			writer.write("*---------------------- MAIN DATA FIELD");
			
			for(int i = 0; i < 5; i++)
				writer.newLine();
			
			for(int j = 0; j < audioFilenames.size(); j++){
				writer.write("#00101:0" + (j+1));
				writer.newLine();
			}
			
		} catch (Exception ex) {
		  ex.printStackTrace();
		}
	
	}

    public void processAndNote(ArrayList<AudioInputStream> fileStreams, ArrayList<File> files, int bpm, int npIndex){
        SoundProcessor sp = new SoundProcessor(fileStreams, files, bpm, npIndex);
        ArrayList<NoteList> nls = sp.process();
        
        try {
            for(int measure = 0; measure < nls.size(); measure++){
                String[] tokenStringArray = nls.get(measure).compileNotes(fileStreams.size());
                for (int lane = 0; lane < fileStreams.size(); lane++){
                    if(tokenStringArray[lane] != null){
                        //System.out.println("Printing lane " + getID(lane) + " in measure " + (measure+1));
                        if (!tokenStringArray[lane].equals("")){
                            writer.write("#" + String.format("%03d",measure+1) + (getID(lane) < 10 ? "0" : "") + getID(lane) + ":" + tokenStringArray[lane]);
                            writer.newLine();
                        }
                    }
                }
                writer.newLine();
            }
            
            ArrayList<Signature> sigsMaster = sp.getSignaturesMaster();
            ArrayList<ArrayList<Signature>> signatures = sp.getSignatures();
            ArrayList<ArrayList<Signature>> tails = sp.getTails();
            
            for(int lane = 0; lane < signatures.size(); lane++){
            	ArrayList<Signature> sigList = signatures.get(lane);
            	ArrayList<Signature> tailList = tails.get(lane);
            	for(int num = 0; num < sigList.size(); num++){
            		System.out.println("Now writing to file from lane " + lane + " signature " + num + " with tail length " + tailList.size());
            		Signature combinedArr = new Signature(sigList.get(num).getFloats(), lane);
            		combinedArr.append(tailList.get(num));
            		AudioInputStream ais = sp.getAIS(combinedArr, lane);
            		AudioFileFormat.Type targetFileType = sp.getAFF(lane).getType();
            		int noteIndex = sigsMaster.indexOf(sigList.get(num));
            		String filename = Integer.toString(noteIndex + 36, 36);
            		File outputFile = new File(filename + ".wav");
            		AudioSystem.write(ais, targetFileType, outputFile);
            	}
            }
            
            writer.newLine();
            
            for (int i = 0; i < sigsMaster.size(); i++){
            	String s = Integer.toString(i+36, 36);
            	writer.write("#WAV" + s + " " + s + ".wav");
            	writer.newLine();
            }
            
			if (writer != null) writer.close();
            
		} catch (Exception ex) {
		  ex.printStackTrace();
          System.exit(1);
		}
	}

    public int getID(int lane){
		if (lane < 3) return lane+11;
		else if (lane == 3) return 16;
		else if (lane == 4 || lane == 5) return lane+10;
        else if (lane == 6 || lane == 7) return lane+12;
        else return 1;
	}
}