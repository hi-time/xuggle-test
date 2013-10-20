package com.ttProject.xuggle.test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IAudioSamples.Format;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

/**
 * test for audioData
 * @author taktod
 */
public class AudioDataTest {
	private Logger logger = Logger.getLogger(AudioDataTest.class);
	/** counter for audio */
	private int audioCounter;
	/**
	 * make audioData for simple beep.
	 * @return
	 */
	private IAudioSamples samples() {
		int samplingRate = 44100;
		int tone = 440;
		int bit = 16;
		int channels = 2;
		int samplesNum = 1024;
		ByteBuffer buffer = ByteBuffer.allocate((int)samplesNum * bit * channels / 8);
		double rad = tone * 2 * Math.PI / samplingRate;
		double max = (1 << (bit - 2)) - 1;
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		long startPos = 1000 * audioCounter / 44100 * 1000;
		for(int i = 0;i < samplesNum / 8;i ++, audioCounter ++) {
			short data = (short)(Math.sin(rad * audioCounter) * max);
			for(int j = 0;j < channels;j ++) {
				buffer.putShort(data);
			}
		}
		buffer.flip();
		int snum = (int)(buffer.remaining() * 8/bit/channels);
		IAudioSamples samples = IAudioSamples.make(snum, channels, Format.FMT_S16);
		samples.getData().put(buffer.array(), 0, 0, buffer.remaining());
		samples.setComplete(true, snum, samplingRate, channels, Format.FMT_S16, 0);
		samples.setTimeStamp(startPos);
		samples.setPts(startPos);
		return samples;
	}
	/**
	 * play beep.
	 * @throws Exception
	 */
	@Test
	public void playTest() throws Exception {
		logger.info("start playTest...");
		SourceDataLine audioLine = null;
		try {
			for(audioCounter = 0;audioCounter < 44100;) {
				// get target samples for play
				IAudioSamples samples = samples();
				if(audioLine == null) {
					// if audioLine is not ready, setup.
					AudioFormat format = new AudioFormat((float) samples.getSampleRate(),
							(int)IAudioSamples.findSampleBitDepth(samples.getFormat()),
							samples.getChannels(),
							true,
							false);
					DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
					audioLine = (SourceDataLine) AudioSystem.getLine(info);
					audioLine.open(format);
					audioLine.start();
				}
				// append data to audioLine
				byte[] dataBytes = samples.getData().getByteArray(0, samples.getSize());
				audioLine.write(dataBytes, 0, samples.getSize());
			}
		}
		catch(Exception e) {
			logger.error(e);
			Assert.fail(e.getMessage());
		}
		finally {
			if(audioLine != null) {
				audioLine.drain();
				audioLine.close();
				audioLine = null;
			}
		}
		logger.info("playTest is done");
	}
	/**
	 * convertData to mp3.
	 */
	@Test
	public void convertTest() {
		logger.info("start convertTest");
		IContainer container = null;
		IStreamCoder encoder = null;
		try {
			container = IContainer.make();
			if(container.open("beep.mp3", IContainer.Type.WRITE, null) < 0) {
				throw new Exception("failed to open beep.mp3");
			}
			IStream stream = container.addNewStream(ICodec.ID.CODEC_ID_MP3);
			encoder = stream.getStreamCoder();
			encoder.setSampleRate(44100); // 44100 Hz
			encoder.setChannels(2); // stereo
			encoder.setBitRate(96000); // 96kbps
			if(encoder.open(null, null) < 0) {
				throw new Exception("failed to open encoder for mp3.");
			}
			if(container.writeHeader() < 0) { // maybe nothing to do.
				throw new Exception("failed to write Header for mp3.");
			}
			IPacket packet = IPacket.make();
			for(audioCounter = 0;audioCounter < 44100;) {
				// get target samples for play
				IAudioSamples samples = samples();
				int samplesConsumed = 0;
				while(samplesConsumed < samples.getNumSamples()) {
					int retVal = encoder.encodeAudio(packet, samples, samplesConsumed);
					if(retVal < 0) {
						throw new Exception("failed to encode samples");
					}
					samplesConsumed += retVal;
					if(packet.isComplete()) {
						if(container.writePacket(packet) < 0) {
							throw new Exception("failed to write samples to container.");
						}
					}
				}
			}
			if(container.writeTrailer() < 0) {
				throw new Exception("failed to twrite Trailer for mp3.");
			}
		}
		catch(Exception e) {
			logger.error(e);
			Assert.fail(e.getMessage());
		}
		finally {
			if(encoder != null) {
				encoder.close();
				encoder = null;
			}
			if(container != null) {
				container.close();
				container = null;
			}
		}
		logger.info("convertTest is done.");
	}
	/**
	 * convertData to mp3.
	 */
	@Test
	public void convertTestWithResample() {
		logger.info("start convertTestWithResample");
		IContainer container = null;
		IStreamCoder encoder = null;
		IAudioResampler resampler = null;
		try {
			container = IContainer.make();
			if(container.open("resampledBeep.mp3", IContainer.Type.WRITE, null) < 0) {
				throw new Exception("failed to open beep.mp3");
			}
			IStream stream = container.addNewStream(ICodec.ID.CODEC_ID_MP3);
			encoder = stream.getStreamCoder();
			ICodec codec = encoder.getCodec();
			for(Format format : codec.getSupportedAudioSampleFormats()) {
				logger.info(format.toString());
			}
			encoder.setSampleRate(44100); // 44100 Hz
			encoder.setChannels(2); // stereo
			encoder.setBitRate(96000); // 96kbps
			encoder.setSampleFormat(Format.FMT_FLT);
			if(encoder.open(null, null) < 0) {
				throw new Exception("failed to open encoder for mp3.");
			}
			if(container.writeHeader() < 0) { // maybe nothing to do.
				throw new Exception("failed to write Header for mp3.");
			}
			IPacket packet = IPacket.make();
			for(audioCounter = 0;audioCounter < 44100;) {
				// get target samples for play
				IAudioSamples samples = samples();
				// if format is different, need resample
				if(samples.getSampleRate() != encoder.getSampleRate()
				|| samples.getChannels() != encoder.getChannels()
				|| samples.getFormat() != encoder.getSampleFormat()) {
					if(resampler == null) {
						resampler = IAudioResampler.make(
								encoder.getChannels(), samples.getChannels(),
								encoder.getSampleRate(), samples.getSampleRate(),
								encoder.getSampleFormat(), samples.getFormat());
					}
					IAudioSamples resampledData = IAudioSamples.make(44100, 2, Format.FMT_FLT);
					int retVal = resampler.resample(resampledData, samples, samples.getNumSamples());
					if(retVal <= 0) {
						throw new Exception("failed to resample data.");
					}
					samples = resampledData;
				}
				int samplesConsumed = 0;
				while(samplesConsumed < samples.getNumSamples()) {
					int retVal = encoder.encodeAudio(packet, samples, samplesConsumed);
					if(retVal < 0) {
						throw new Exception("failed to encode samples");
					}
					samplesConsumed += retVal;
					if(packet.isComplete()) {
						if(container.writePacket(packet) < 0) {
							throw new Exception("failed to write samples to container.");
						}
					}
				}
			}
			if(container.writeTrailer() < 0) {
				throw new Exception("failed to twrite Trailer for mp3.");
			}
		}
		catch(Exception e) {
			logger.error(e);
			Assert.fail(e.getMessage());
		}
		finally {
			if(encoder != null) {
				encoder.close();
				encoder = null;
			}
			if(container != null) {
				container.close();
				container = null;
			}
		}
		logger.info("convertTest is done.");
	}
	/**
	 * convertData to mp3.
	 */
	@Test
	public void convertTestForVorbis() {
		logger.info("start convertTestForVorbis");
		IContainer container = null;
		IStreamCoder encoder = null;
		IAudioResampler resampler = null;
		try {
			container = IContainer.make();
			if(container.open("beep.ogg", IContainer.Type.WRITE, null) < 0) {
				throw new Exception("failed to open beep.ogg");
			}
			IStream stream = container.addNewStream(ICodec.ID.CODEC_ID_VORBIS);
			encoder = stream.getStreamCoder();
			ICodec codec = encoder.getCodec();
			for(Format format : codec.getSupportedAudioSampleFormats()) {
				logger.info(format.toString());
			}
			encoder.setSampleRate(44100); // 44100 Hz
			encoder.setChannels(2); // stereo
			encoder.setBitRate(96000); // 96kbps
			encoder.setSampleFormat(Format.FMT_FLT);
			if(encoder.open(null, null) < 0) {
				throw new Exception("failed to open encoder for vorbis.");
			}
			if(container.writeHeader() < 0) { // maybe nothing to do.
				throw new Exception("failed to write Header for vorbis.");
			}
			IPacket packet = IPacket.make();
			for(audioCounter = 0;audioCounter < 44100;) {
				// get target samples for play
				IAudioSamples samples = samples();
				// if format is different, need resample
				if(samples.getSampleRate() != encoder.getSampleRate()
				|| samples.getChannels() != encoder.getChannels()
				|| samples.getFormat() != encoder.getSampleFormat()) {
					if(resampler == null) {
						resampler = IAudioResampler.make(
								encoder.getChannels(), samples.getChannels(),
								encoder.getSampleRate(), samples.getSampleRate(),
								encoder.getSampleFormat(), samples.getFormat());
					}
					IAudioSamples resampledData = IAudioSamples.make(44100, 2, Format.FMT_FLT);
					int retVal = resampler.resample(resampledData, samples, samples.getNumSamples());
					if(retVal <= 0) {
						throw new Exception("failed to resample data.");
					}
					samples = resampledData;
				}
				int samplesConsumed = 0;
				while(samplesConsumed < samples.getNumSamples()) {
					int retVal = encoder.encodeAudio(packet, samples, samplesConsumed);
					if(retVal < 0) {
						throw new Exception("failed to encode samples");
					}
					samplesConsumed += retVal;
					if(packet.isComplete()) {
						if(container.writePacket(packet) < 0) {
							throw new Exception("failed to write samples to container.");
						}
					}
				}
			}
			if(container.writeTrailer() < 0) {
				throw new Exception("failed to twrite Trailer for vorbis.");
			}
		}
		catch(Exception e) {
			logger.error(e);
			Assert.fail(e.getMessage());
		}
		finally {
			if(encoder != null) {
				encoder.close();
				encoder = null;
			}
			if(container != null) {
				container.close();
				container = null;
			}
		}
		logger.info("convertTest is done.");
	}
}
