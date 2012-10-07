package com.ttProject.xuggle;

import com.ttProject.xuggle.flv.FlvHandlerFactory;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.ISimpleMediaFile;
import com.xuggle.xuggler.SimpleMediaFile;

/**
 * 実際の変換処理
 * ファイルの読み込みの動作のみ実行
 * @author taktod
 */
public class Transcoder0 implements Runnable {
	/** 入力コンテナ */
	private IContainer inputContainer;
	/** 処理中であるかフラグ */
	private boolean keepRunning = true;

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
			retval = inputContainer.readNextPacket(packet);
			if(retval < 0) {
				if("Resource temporarily unavailable".equals(IError.make(retval).getDescription())){
					// リソースが一時的にないだけなら、放置
					continue;
				}
				System.out.print("エラーが発生しました。パケット読み込み:");
				System.out.println(IError.make(retval).getDescription());
				break;
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
}
