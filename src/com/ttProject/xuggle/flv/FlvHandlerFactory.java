package com.ttProject.xuggle.flv;

import com.xuggle.xuggler.io.IURLProtocolHandler;
import com.xuggle.xuggler.io.IURLProtocolHandlerFactory;
import com.xuggle.xuggler.io.URLProtocolManager;

/**
 * xuggleのffmpeg動作の入出力URLにあらたなプロトコルを追加する動作
 * @author taktod
 */
public class FlvHandlerFactory implements IURLProtocolHandlerFactory {
	/** シングルトン動作させるためのインスタンス */
	private static FlvHandlerFactory instance = new FlvHandlerFactory();
	/** このFactoryが利用するプロトコル名 */
	public static final String DEFAULT_PROTOCOL = "flvFileInput";
	/**
	 * コンストラクタ
	 */
	private FlvHandlerFactory() {
		// URLプロトコル管理にこのFactory用のプロトコルを追加しておきます。
		URLProtocolManager manager = URLProtocolManager.getManager();
		manager.registerFactory(DEFAULT_PROTOCOL, this);
	}
	/**
	 * シングルトンのFactoryを参照
	 * @return このクラスのfactory
	 */
	public static FlvHandlerFactory getFactory() {
		if(instance == null) {
			throw new RuntimeException("no flv file input factory");
		}
		return instance;
	}
	/**
	 * このFactoryが利用する処理Handlerを応答します。
	 * xuggleが内部で利用する部分。
	 * 本来ならurl名からアクセスファイルをコントロールしたりすることができます。
	 */
	@Override
	public IURLProtocolHandler getHandler(String protocol, String url, int flags) {
		return new FlvHandler();
	}
}
