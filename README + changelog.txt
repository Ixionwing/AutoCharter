v1.0

- Core functionality get!

>>> Automatically appends to .bme format; it's not like LR2 really gives a crap about format because it reads them all
	the same.

>>> Only accepts .wav files for now, I'll probably add .mp3 functionality for 1.3 after optimizing everything else

>>> snares with too much fizz (???) at the end could generate unnecessary notes, something which I bruteforced into
	submission by implementing an absolutely minimum threshold of (half-chunk total amplitude/3) > 6500
	
	>>>>> halp how do i fix dis ;;;;;;;;;;;

* token serialization to be optimized next patch

* For example, use the following series of inputs:
	filename							for filename prompt
	dance								for genre prompt
	blah								for title prompt
	someguy								for artist prompt
	130								for bpm prompt (all included files are at 130BPM)
	6								for chart difficulty prompt (is NOT meant to affect
													generation process)
	mp3Kick.mp3 [OR wavKick.wav]					for lane 1 sound file prompt
	wavHH.wav							for lane 2 sound file prompt
	wavSnare.wav							for lane 3 sound file prompt
	wavCymbal.wav							for turntable sound file prompt
	0								to terminate sound file prompts and begin
										generation process


////////////////////////////////////////////////////////////////////////////////////////////////////////

v1.1

- Optimized token serialization and (very sloppy) mp3 support get!

>>> average file sizes should now be about half of what it was unless you're Skrillex

	>>>>> really inelegant un-mathy solution despite it being only 5 ifs, wat do

>>> moved chunk reinitialization to... somewhere where it actually makes sense.

>>> mp3 support is there via converting mp3 to wav with an JLayer

	>>>>> can't seem to delete temporary files without calling garbage collector 
	>>>>> works very very inconsistently for the first 5 runs or so, then consistently deletes the files for
		all subsequent runs

////////////////////////////////////////////////////////////////////////////////////////////////////////

v1.2

- Optimized half-chunk reading

>>> now copies less and calls the getTotalAmp function less

>>> the based neckbeard overlords must be proud of me Kappa /injoke

////////////////////////////////////////////////////////////////////////////////////////////////////////

