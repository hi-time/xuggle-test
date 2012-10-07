package com.ttProject.xuggle;

import com.ttProject.xuggle.flv.FlvHandlerFactory;

/**
 * 変換動作のエントリー
 * @author taktod
 */
public class Entry {
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("プログラムを開始します。");

		FlvHandlerFactory.getFactory(); // factoryをあけておかないと、url登録ができないのでやっとく。

		// データが準備できているので、コンバートを開始します。
		Thread transcoder = new Thread(new Transcoder0());
		transcoder.start();
	}
}
