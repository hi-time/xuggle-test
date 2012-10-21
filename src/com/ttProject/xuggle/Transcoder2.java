package com.ttProject.xuggle;

import java.io.FileInputStream;

import com.flazr.util.Utils;
import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
//import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;

/**
 * 実際の変換処理
 * ファイルを読み込んでデコード処理まで実行
 * @author taktod
 */
public class Transcoder2 implements Runnable {
	/** 入力コンテナ */
//	private IContainer inputContainer;
	/** 処理中であるかフラグ */
	private boolean keepRunning = true;

	/** 音声のデコード用コーダー */
	private IStreamCoder inputAudioCoder;
	/** 映像のデコード用コーダー */
	private IStreamCoder inputVideoCoder;
	/** 音声のストリームindex番号保持 */
	private int audioStreamId = 1;
	/** 映像のストリームindex番号保持 */
	private int videoStreamId = 0;

	/**
	 * コンバートの実処理
	 */
	@Override
	public void run() {
		try {
			// 読み込みコンテナオープン
			openContainer();
			// 変換を実行
			transcode();
		}
		catch (Exception e) {
			// 例外がおきた場合
			e.printStackTrace();
		}
		finally {
			// 終了処理
			closeAll();
		}
	}
	private FileInputStream fis = null;
	private long position = 0;
	/**
	 * コンテナを開きます
	 */
	private void openContainer() {
		// コンテナを開くかわりに自力で読み込みます。
		// ffmpeg -f flv -i hoge.flv 
		// 読み込みターゲットファイル
		try {
			FileInputStream fis = new FileInputStream("mario3.flv");
			byte[] header = new byte[13]; // flvのheaderって13バイトだっけ？
			fis.read(header);
			System.out.println(Utils.toHex(header));
			position += 13;
			this.fis = fis;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("動作エラーが発生しました。");
		}
	}
	/**
	 * 次のパケットを読み込みます。
	 * @param packet
	 */
	private void readNextPacket(IPacket packet) {
		try {
			boolean isMediaRead = false;
			while(!isMediaRead) {
				byte[] data = new byte[11];
				fis.read(data);
				position += 11;
				System.out.println(Utils.toHex(data));
				int size = (((data[1] & 0xFF) << 16) + ((data[2] & 0xFF) << 8) + (data[3] & 0xFF));
				System.out.println(size);
				int timestamp = (((data[4] & 0xFF) << 16) + ((data[5] & 0xFF) << 8) + (data[6] & 0xFF) + ((data[7] & 0xFF) << 24));
				if(data[0] == 0x09 || data[0] == 0x08) { // audioかvideoデータだった場合・・・
					isMediaRead = true;
					if(data[0] == 0x09) {
						readNextVideoPacket(packet, size, timestamp);
					}
					else {
						readNextAudioPacket(packet, size, timestamp);
					}
				}
				else {
					fis.skip(size);
					position += size;
					fis.skip(4);
					position += 4;
					try {
						System.out.println("不明パケット発見 meta?");
						Thread.sleep(1000);
					}
					catch (Exception e) {
						// TODO: handle exception
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException("パケット読み込み中におわらす。");
		}
	}
	private void readNextVideoPacket(IPacket packet, int size, int timestamp) {
		// データを読み込んでどのようなパケットか調べておく
		try {
			byte data = (byte)fis.read();
			byte[] mediaData;
			// フォーマット確認
			switch((data & 0x0F)) {
			case 1: // jpeg
			case 2: // h263
			case 3: // screen
			case 6: // screen v2
			case 4: // ON2VP6
			case 5: // ON2VP6_Alpha
				// 上記５つのフォーマットは、ここ以降がメディアデータ
				position += 1;
				size -= 1;
				break;
			case 7: // AVC
				setupVideoCoder(ICodec.ID.CODEC_ID_H264);
				// AVCの場合は、さらに4バイトデータがある。(がとりあえず無視しておく。)
				fis.skip(4);
				size -= 5;
				position += 5;
				break;
			default:
				throw new RuntimeException("判定できないコーデックのデータをみつけました。");
			}
			mediaData = new byte[size];
			fis.read(mediaData);
			IBuffer bufData = IBuffer.make(null, mediaData, 0, size);
			packet.setData(bufData); // データもいれとく。
			packet.setPosition(position);
			position += size;
			packet.setComplete(true, size);
			// キーフレームの確認
			if((data & 0x10) != 0x00) {
				packet.setKeyPacket(true);
			}
			else {
				packet.setKeyPacket(false);
			}
			packet.setFlags(1);
			packet.setStreamIndex(0); // 動画は0いれとく。
			packet.setDts(timestamp);
			packet.setPts(timestamp);
			packet.setTimeBase(IRational.make(1, 1000));
			position += 4;
			fis.skip(4); // 終端のデータ
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("よくわからんエラーが発生");
		}
	}
	private void readNextAudioPacket(IPacket packet, int size, int timestamp) {
		// データを読み込んでどのようなパケットか調べておく
		try {
			byte data = (byte)fis.read();
			byte[] mediaData;
			// フォーマット確認
			int sampleRate;
			int channels;
			switch((data & 0x0C) >>> 2) {
			default:
			case 0:
				sampleRate = 5500;
				break;
			case 1:
				sampleRate = 11025;
				break;
			case 2:
				sampleRate = 22050;
				break;
			case 3:
				sampleRate = 44100;
				break;
			}
			if((data & 0x01) == 1) {
				channels = 2;
			}
			else {
				channels = 1;
			}
			switch((data & 0xF0) >>> 4) {
			case 0: // Raw PCM (opensourceflashのflv記述より)
			case 1: // Adpcm
			case 2: // Mp3
				setupAudioCoder(ICodec.ID.CODEC_ID_MP3, channels, sampleRate);
			case 3: // Pcm
			case 4: // Nelly_16
			case 5: // Nelly_8
			case 6: // Nelly
			case 7: // G711_a
			case 8: // G711_u
			case 9: // reserved
			case 11: // Speex
			case 13:
			case 14: // Mp3_8
			case 15: // Device_specific
				position += 1;
				size -= 1;
				break;
			case 10: // AAC
				fis.skip(1);
				position += 2;
				size -= 2;
				break;
			default:
				throw new RuntimeException("判定できないコーデックのデータをみつけました。");
			}
			mediaData = new byte[size];
			fis.read(mediaData);
			IBuffer bufData = IBuffer.make(null, mediaData, 0, size);
			packet.setData(bufData); // データもいれとく。
			packet.setPosition(position);
			position += size;
			packet.setComplete(true, size);
			packet.setFlags(1);
			packet.setStreamIndex(1); // 音声は1いれとく。
			packet.setDts(timestamp);
			packet.setPts(timestamp);
			packet.setTimeBase(IRational.make(1, 1000));
			position += 4;
			fis.skip(4); // 終端のデータ
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("よくわからんエラーが発生");
		}
	}
	private void setupVideoCoder(ICodec.ID id) {
		if(inputVideoCoder == null) {
			inputVideoCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING);
			inputVideoCoder.setWidth(640);
			inputVideoCoder.setHeight(360);
			inputVideoCoder.setBitRate(820000);
			inputVideoCoder.setFrameRate(IRational.make(58.82));
			inputVideoCoder.setCodec(id);
			int retval = inputVideoCoder.open();
			if(retval < 0) {
				System.out.println("videoエラーになった・・・orz");
			}
		}
	}
	private void setupAudioCoder(ICodec.ID id, int channels, int sampleRate) {
		if(inputAudioCoder == null) {
			inputAudioCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING);
//			inputAudioCoder.setBitRate(64000); // bitrateを調べる方法がないので、とりあえずほっとく。
			inputAudioCoder.setCodec(id);
			inputAudioCoder.setChannels(channels);
			inputAudioCoder.setSampleRate(sampleRate);
			inputAudioCoder.setTimeBase(IRational.make(1, 1000));
			int retval = inputAudioCoder.open();
			if(retval < 0) {
				System.out.println("エラーになった・・・orz");
			}
		}
	}
	/**
	 * 変換処理の実体
	 */
	private void transcode() {
		// データの入れ物のパケットをつくります。
		while(keepRunning) {
			int retval = -1;
			IPacket packet = IPacket.make();
			readNextPacket(packet);
			try {
				System.out.println(packet.toString());
				IBuffer buffer = packet.getData();
				byte[] data;
				int length = buffer.getSize() > 32 ? 32 : buffer.getSize();
				data = buffer.getByteArray(0, length);
				System.out.println("video:" + Utils.toHex(data));
			}
			catch (Exception e) {
				e.printStackTrace();
			}// */
//			throw new RuntimeException("おわりだ");
			// 入力コーダーを確認します。
/*			if(!checkInputCoder(packet)) {
				// 処理すべきデータではないので、スキップ
				continue;
			}*/
			// 処理するパケットが音声であるか、映像であるか判定して切り分ける。
			int index = packet.getStreamIndex();
			if(index == audioStreamId) {
				// packetと同様に処理用の入れ物audioSampleを作成します。
				IAudioSamples inSamples = IAudioSamples.make(1024, inputAudioCoder.getChannels());
				int offset = 0;
				while(offset < packet.getSize()) {
					// データのデコード処理を実施し、inSamplesに処理済みデータをいれておきます。
					retval = inputAudioCoder.decodeAudio(inSamples, packet, offset);
					if(retval <= 0) {
						System.out.println(IError.make(retval));
						System.out.println("aデコードに失敗");
//						throw new RuntimeException("audioのデコードに失敗した。");
						break;
					}
					offset += retval;
					if(inSamples.isComplete()) {
						System.out.println("オーディオパケットデコード完了");
					}
				}
			}
			else if(index == videoStreamId) {
				// packetと同様に処理用の入れ物videoPictureを作成します。
				IVideoPicture inPicture = IVideoPicture.make(inputVideoCoder.getPixelType(), inputVideoCoder.getWidth(), inputVideoCoder.getHeight());
				int offset = 0;
				while(offset < packet.getSize()) {
					// データのデコード処理を実施し、inPictureに処理済みデータをいれておきます。
					retval = inputVideoCoder.decodeVideo(inPicture, packet, offset);
					if(retval <= 0) {
						System.out.println("vデコード失敗");
//						return;
//						continue;
						break;
					}
					offset += retval;
					
					if(inPicture.isComplete()) {
						System.out.println("ビデオパケットでコード完了。");
					}
				}
			}// */
			try {
				Thread.sleep(100);
			}
			catch (Exception e) {
			}// */
		}
	}
	/**
	 * 処理を停止させます。
	 */
	public void close() {
		keepRunning = false;
	}
	/**
	 * 内部の処理をすべて停止します。
	 */
	private void closeAll() {
		if(fis != null) {
			try {
				fis.close();
			}
			catch (Exception e) {
			}
			fis = null;
		}
	}
}
