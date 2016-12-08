import java.io.*;
import javax.sound.sampled.*;
import java.util.*;
import javazoom.jl.converter.*;

public class AutoCharter{

	public static void main(String[] args){
		ArrayList<String> audioFilenames = new ArrayList<String>();
		ArrayList<String> info = new ArrayList<String>();
		ArrayList<File> fileTemp = new ArrayList<File>();
		ArrayList<AudioInputStream> files = new ArrayList<AudioInputStream>(5);
		int bpm;
		String temp;
		Scanner scan = new Scanner(System.in);
        BMSWriter writer;
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
		
		System.out.println("Recommended note designation: ");
		System.out.println("Lane 1: File 1, bass/kick\nLane 2: File 2, high hat\nLane 3: File 3, snare/clap\nTurntable: File 4, cymbal/scratch");
		System.out.println("All lanes beyond 8 will be placed in the background.");
		System.out.println("Input up to 36 filenames, including extension (exit with 0): ");
		for(int i = 0; i<36; i++){
			temp = scan.nextLine();
			if(!(temp.equals("0"))){
				audioFilenames.add(temp);
			}
			else break;
		}
		
		try{
		System.out.println("Size is: " + audioFilenames.size());
		for(int j = 0; j < audioFilenames.size(); j++){
			System.out.println("Importing file " + audioFilenames.get(j));
			fileTemp.add(new File(audioFilenames.get(j)));
			
			if(audioFilenames.get(j).endsWith(".mp3")){
				converter.convert(fileTemp.get(j).getAbsolutePath(), fileTemp.get(j).getAbsolutePath().replace(".mp3", ".wav"));
				fileTemp.set(j, new File(audioFilenames.get(j).replace(".mp3", ".wav")));
				fileTemp.get(j).deleteOnExit();
			}
			files.add(AudioSystem.getAudioInputStream(fileTemp.get(j)));
			System.out.println("File imported!");
		}
		writer = new BMSWriter(info.get(0), audioFilenames);
		
		writer.constructBaseBMS(info, bpm);
		writer.processAndNote(files, fileTemp, bpm);
		
		System.out.println("Automatic generation complete! Now closing...");
		
		} catch (Exception ex) {
		  ex.printStackTrace();
		  System.exit(1);
		} finally {
			converter = null;
            System.gc(); // I know this is bad practice but I DON'T FUCKING KNOW
							 // YOU STUPID PROGRAM I SWEAR TO GOD I CLOSED ALL THE STREAMS
							 // RAHRAHRAH FUCK JAVA AND FUCK EXTERNAL LIBRARIES
							 // I'M GOING BACK TO ITALIA, ARRIVEDERCI
							 
							 // ...also, the program basically won't terminate if explicit gc calls are disabled
			
			for (int k = 0; k < audioFilenames.size(); k++){
				if(audioFilenames.get(k).endsWith(".mp3"))
					while(!fileTemp.get(k).delete());
			}
        }
		scan.close();
	}
	
}