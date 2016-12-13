import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;

import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import be.tarsos.dsp.io.jvm.JVMAudioInputStream;

import javax.sound.sampled.*;
public class ACPitchDetector implements PitchDetectionHandler {
	private PitchEstimationAlgorithm[] algos;
	private AudioDispatcher dispatcher;
	private boolean mode;
	private float res1, res2;
	
	public ACPitchDetector(){
		algos = new PitchEstimationAlgorithm[]{PitchEstimationAlgorithm.YIN, PitchEstimationAlgorithm.MPM, PitchEstimationAlgorithm.AMDF};
	}
	
	@Override
	public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent){
		if (!mode){
			res1 = pitchDetectionResult.getPitch();
			mode = true;
		}
		
		else res2 = pitchDetectionResult.getPitch();
	}
	
	public float[] getPitchDiff(AudioInputStream chunk1, AudioInputStream chunk2, AudioFormat af1, AudioFormat af2){
		res1 = -1.0f;
		res2 = -1.0f;
		mode = false;
		try{
			int bs1 = chunk1.available()/4;
			int bs2 = chunk2.available()/4;
			PitchProcessor x;
			JVMAudioInputStream audioStream = new JVMAudioInputStream(chunk1);
			for(int i = 0; i < algos.length && (res1 == -1.0f || res1 >= 20000); i++){
				dispatcher = new AudioDispatcher(audioStream, bs1, 0);
				x = new PitchProcessor(algos[i], (int)(af1.getSampleRate()), bs1, this);
				dispatcher.addAudioProcessor(x);
				dispatcher.run();
				dispatcher.removeAudioProcessor(x);
			}
			audioStream = new JVMAudioInputStream(chunk2);
			for(int i = 0; i < algos.length && (res2 == -1.0f || res2 >= 20000); i++){
				dispatcher = new AudioDispatcher(audioStream, bs2, 0);
				x = new PitchProcessor(algos[i], (int)(af2.getSampleRate()), bs2, this);
				dispatcher.addAudioProcessor(x);
				dispatcher.run();
				dispatcher.removeAudioProcessor(x);
			}
		} catch (Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		return new float[]{res1, res2, Math.abs(res1-res2)};
	}
	
	
}
