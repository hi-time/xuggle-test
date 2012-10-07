package com.ttProject.xuggle;

import com.ttProject.xuggle.flv.FlvHandlerFactory;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.SimpleMediaFile;

/**
 * 実際の変換処理
 * ファイルを読み込んでデコード処理まで実行
 * @author taktod
 */
public class Transcoder1 implements Runnable {
	/** 入力コンテナ */
	private IContainer inputContainer;
	/** 処理中であるかフラグ */
	private boolean keepRunning = true;

	/** 音声のデコード用コーダー */
	private IStreamCoder inputAudioCoder;
	/** 映像のデコード用コーダー */
	private IStreamCoder inputVideoCoder;
	/** 音声のストリームindex番号保持 */
	private int audioStreamId = -1;
	/** 映像のストリームindex番号保持 */
	private int videoStreamId = -1;

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
	/**
	 * コンテナを開きます
	 */
	private void openContainer() {
		// ffmpeg -f flv -i hoge.flv 
		String url;
		int retval = -1;
		url = FlvHandlerFactory.DEFAULT_PROTOCOL + ":test"; // urlをつくって、ファイルオープンとflvHandlerFactoryを結びつける。
		ISimpleMediaFile inputInfo = new SimpleMediaFile();
		inputInfo.setURL(url); // -i hoge.flvの部分に相当する動作
		inputContainer = IContainer.make();
		IContainerFormat inputFormat = IContainerFormat.make();
		inputFormat.setInputFormat("flv"); // -f flvに相当する動作
		retval = inputContainer.open(url, IContainer.Type.READ, inputFormat, true, false);
		if(retval < 0) {
			throw new RuntimeException("入力用のURLを開くことができませんでした。");
		}
		System.out.println("入力コンテナを開くことができました。");
	}
	/**
	 * 変換処理の実体
	 */
	private void transcode() {
		// データの入れ物のパケットをつくります。
		IPacket packet = IPacket.make();
		while(keepRunning) {
			int retval = -1;
			retval = inputContainer.readNextPacket(packet); // packetに処理するパケットデータが書き込まれます。
			if(retval < 0) {
				if("Resource temporarily unavailable".equals(IError.make(retval).getDescription())){
					// リソースが一時的にないだけなら、放置
					continue;
				}
				System.out.print("エラーが発生しました。パケット読み込み:");
				System.out.println(IError.make(retval).getDescription());
				break;
			}
			// 入力コーダーを確認します。
			if(!checkInputCoder(packet)) {
				// 処理すべきデータではないので、スキップ
				continue;
			}
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
						System.out.println("aデコードに失敗");
						continue;
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
						return;
//						continue;
					}
					offset += retval;
					
					if(inPicture.isComplete()) {
						System.out.println("ビデオパケットでコード完了。");
					}
				}
			}
			try {
				Thread.sleep(10);
			}
			catch (Exception e) {
			}
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
		
	}
	/**
	 * パケットに付随しているコーダーが利用中であるか確認して、利用中でないなら開く
	 * またstreamIndexを保持しておくことで、transcode上で音声処理か映像処理か判定する。
	 * @param packet
	 * @return
	 */
	private boolean checkInputCoder(IPacket packet) {
		IStream stream = inputContainer.getStream(packet.getStreamIndex());
		if(stream == null) {
			System.out.println("ストリームが取得できない。");
			return false;
		}
		IStreamCoder coder = stream.getStreamCoder();
		if(coder == null) {
			System.out.println("コーダーが取得できない。");
			return false;
		}
		if(coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
			if(inputAudioCoder == null) {
				System.out.println("音声コーダー追加");
			}
			else if(inputAudioCoder.hashCode() == coder.hashCode()){
				// すでに開いているコーダー
				return true;
			}
			else {
				System.out.println("音声コーダー開き直し");
				inputAudioCoder.close();
				inputAudioCoder = null;
			}
			audioStreamId = packet.getStreamIndex();
			if(coder.open() < 0) {
				throw new RuntimeException("audio入力用のデコーダを開くのに失敗したよん");
			}
			inputAudioCoder = coder;
		}
		else if(coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
			if(inputVideoCoder == null) {
				System.out.println("映像コーダー追加");
			}
			else if(inputVideoCoder.hashCode() == coder.hashCode()) {
				// すでに開いているコーダー
				return true;
			}
			else {
				System.out.println("映像コーダー開き直し");
				inputVideoCoder.close();
				inputVideoCoder = null;
			}
			videoStreamId = packet.getStreamIndex();
			if(coder.open() < 0) {
				throw new RuntimeException("video入力用のデコーダーを開くのに失敗したよん");
			}
			inputVideoCoder = coder;
		}
		else {
			return false;
		}
		return true;
	}
}
