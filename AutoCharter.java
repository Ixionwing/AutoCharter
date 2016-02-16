import java.io.*;
import javax.sound.sampled.*;
import java.util.*;
import javazoom.jl.converter.*;

public class AutoCharter{

	public static void main(String[] args){
		ArrayList<String> filename = new ArrayList<String>();
		ArrayList<String> info = new ArrayList<String>();
		ArrayList<File> fileTemp = new ArrayList<File>();
		ArrayList<AudioInputStream> files = new ArrayList<AudioInputStream>(5);
		int bpm;
		String temp;
		Scanner scan = new Scanner(System.in);
		BufferedWriter writer = null;
		Converter converter = new Converter();
		
		System.out.print("Input the chart's to-be filename (program automatically includes .bme extension: ");
		info.add(scan.nextLine());
		info.set(0, info.get(0) + ".bme");
		System.out.println("Input the song's genre: ");
		info.add(scan.nextLine());
		System.out.println("Input the song's name: ");
		info.add(scan.nextLine());
		System.out.println("Input the song's artist: ");
		info.add(scan.nextLine());
		System.out.println("Input the song's BPM: ");
		bpm = scan.nextInt();
		scan.nextLine();
		System.out.println("Input the chart's tentative level: ");
		info.add(scan.nextLine());
		
		System.out.println("Note designation: ");
		System.out.println("Lane 1: File 1, bass/kick\nLane 2: File 2, high hat\nLane 3: File 3, snare/clap\nTurntable: File 4, cymbal/scratch\nLane 4: File 5, extra");
		System.out.println("Input up to 5 filenames, including extension (exit with 0): ");
		for(int i = 0; i < 5; i++){
			temp = scan.nextLine();
			if(!(temp.equals("0"))){
				filename.add(temp);
			}
			else break;
		}
		
		try{
		System.out.println("Size is: " + filename.size());
		for(int j = 0; j < filename.size(); j++){
			System.out.println("Importing file " + filename.get(j));
			fileTemp.add(new File(filename.get(j)));
			
			if(filename.get(j).endsWith(".mp3")){
				converter.convert(fileTemp.get(j).getAbsolutePath(), fileTemp.get(j).getAbsolutePath().replace(".mp3", ".wav"));
				fileTemp.set(j, new File(filename.get(j).replace(".mp3", ".wav")));
				fileTemp.get(j).deleteOnExit();
			}
			files.add(AudioSystem.getAudioInputStream(fileTemp.get(j)));
			System.out.println("File imported!");
		}
		writer = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(info.get(0)), "utf-8"));
		
		constructBaseBMS(info, filename, bpm, writer);
		processAndNote(info.get(0), files, bpm, writer);
		
		System.out.println("Automatic generation complete! Now closing...");
		
		} catch (Exception ex) {
		  ex.printStackTrace();
		  System.exit(1);
		} finally {
			try{
				if (writer != null) writer.close();
				
				converter = null;
				
				while(files.size() > 0)
					if (files.get(0) != null) files.remove(0).close();
				
				System.gc(); // I know this is bad practice but I DON'T FUCKING KNOW
							 // YOU STUPID PROGRAM I SWEAR TO GOD I CLOSED ALL THE STREAMS
							 // RAHRAHRAH FUCK JAVA AND FUCK EXTERNAL LIBRARIES
							 // I'M GOING BACK TO ITALIA, ARRIVEDERCI
							 
							 // ...also, the program basically won't terminate if explicit gc calls are disabled
				
				for (int k = 0; k < filename.size(); k++){
					if(filename.get(k).endsWith(".mp3"))
						while(!fileTemp.get(k).delete());
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			} 
		}
		
	}

	public static void constructBaseBMS(ArrayList<String> info, ArrayList<String> filenames, int bpm, BufferedWriter writer){
	

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
			
			for(int i = 0; i < filenames.size(); i++){
				writer.write("#WAV0" + (i+1) + " " + filenames.get(i));
				writer.newLine();
			}
			
			for(int i = 0; i < 5; i++)
				writer.newLine();
				
			writer.write("*---------------------- MAIN DATA FIELD");
			
			for(int i = 0; i < 5; i++)
				writer.newLine();
			
			for(int j = 0; j < filenames.size(); j++){
				writer.write("#00101:0" + (j+1));
				writer.newLine();
			}
			
		} catch (Exception ex) {
		  ex.printStackTrace();
		}
	
	}
	
	public static void processAndNote(String bmsfilename, ArrayList<AudioInputStream> files, int bpm, BufferedWriter writer){
		try {
			ArrayList<AudioFormat> af = new ArrayList<AudioFormat>(5);
			double[] byteArraySize = new double[files.size()];
			byte[] chunk;
			int newChunkFirstHalf = 0;
			int prevChunkSecondHalf = 0;
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
			
			String stringOfTokens = "";
			boolean checkEOF = false;
			int[] n = new int[files.size()];
			
			for(int measure = 1; measure < 6; measure++){ // !checkEOF
				for(int lane = 0; lane < files.size(); lane++){
					stringOfTokens = "";
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								chunk = new byte[(int)byteArraySize[lane]];
								n[lane] = files.get(lane).read(chunk, 0, chunk.length);
								if(tokens == 0 && measure == 1){ // if it's the very first note
									if(getTotalAmp(chunk) <= 10000)
									stringOfTokens = stringOfTokens.concat("00");
									else stringOfTokens = stringOfTokens.concat("06");
								}
								
								else{
									newChunkFirstHalf = getTotalAmp(Arrays.copyOfRange(chunk, 0, (int)byteArraySize[lane]/2 -1));
									if (newChunkFirstHalf/3 > prevChunkSecondHalf && newChunkFirstHalf/3 > 6500){
									stringOfTokens = stringOfTokens.concat("06");
									}
									else stringOfTokens = stringOfTokens.concat("00");
								}
								prevChunkSecondHalf = getTotalAmp(Arrays.copyOfRange(chunk, (int)byteArraySize[lane]/2, (int)byteArraySize[lane]));
						}
					}
				if((stringOfTokens.contains("06"))){
					if (stringOfTokens.equals("06000000000000000000000000000000"))
						stringOfTokens = "06";
					else if (stringOfTokens.equals("00000000000000000600000000000000"))
						stringOfTokens = "0006";
					else if (stringOfTokens.equals("06000000000000000600000000000000"))
						stringOfTokens = "0606";
					else if (stringOfTokens.equals("06000000060000000600000006000000"))
						stringOfTokens = "06060606";
					else if (stringOfTokens.equals("00000600000006000000060000000600"))
						stringOfTokens = "0006000600060006";
					writer.write("#" + String.format("%03d",measure) + getID(lane) + ":" + stringOfTokens);
					writer.newLine();
				}
				checkEOF = checkIfEnd(n, files.size());
				}
				writer.newLine();
			}
				
			
			
			// format: #[measure number][lane ID]:[tokens]
			// ex. #00113:2Q00002R000000000000000000000000
			// 		> measure 1, lane 3, sample 2Q at start of measure, skip two 16th notes, sample 2R, no more notes for this lane
			//
			// lane IDs:
			// 11: 1 (array index 0)
			// 12: 2 (array index 1)
			// 13: 3 (array index 2)
			// 14: 4 (array index 4)
			// 15: 5 (unused lane)
			// 16: SC (array index 3)
			// 18: 6 (unused lane)
			// 19: 7 (unused lane)
			
			
		} catch (Exception ex) {
		  ex.printStackTrace();
		} 
	}
	
	public static int getTotalAmp(byte[] b){
		int total = 0;
		for(int i = 0; i < b.length; i++)
			total += Math.abs(b[i]);
		
		return total;
	}
	
	public static int getID(int lane){
		if (lane < 3) return lane+11;
		else if (lane == 3) return 16;
		else return 14;
	}
	
	public static boolean checkIfEnd(int[] n, int filenum){
		boolean x = true;
		for(int i = 0; i < filenum; i++){
			if (n[i] == -1)
				x = x && true;
			else
				x = x && false;
		}
		
		return x;
	}
	
}