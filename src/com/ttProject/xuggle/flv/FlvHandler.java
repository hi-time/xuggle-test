package com.ttProject.xuggle.flv;

import java.io.FileInputStream;

import com.xuggle.xuggler.io.IURLProtocolHandler;

/**
 * 今回はファイルからデータをよみとって送る形にしておく。
 * @author taktod
 */
public class FlvHandler implements IURLProtocolHandler {
	/** ファイルデータを読み込ませるためのstream */
	private FileInputStream fis = null;
	/** 処理済み位置の保持 */
	private int pos = 0;
	/**
	 * コンストラクタ
	 */
	public FlvHandler() {
		try {
			// ファイルの読み込み
			fis = new FileInputStream("/Users/xxxx/testinput.flv");
		}
		catch (Exception e) {
		}
	}
	/**
	 * 処理がおわったときに呼び出される動作
	 */
	@Override
	public int close() {
		// ファイルを開いていたら閉じておく。
		if(fis != null) {
			try {
				fis.close();
			}
			catch (Exception e) {
			}
			fis = null;
		}
		return 0;
	}
	/**
	 * ストリームであるか応答します。
	 * ストリーム動作であると仮定しておくのでtrueを返しておきます。
	 */
	@Override
	public boolean isStreamed(String url, int flags) {
		return true;
	}
	/**
	 * 開いているか応答します。
	 * とりあえず開いていると応答したいので、0を返します。
	 */
	@Override
	public int open(String url, int flags) {
		return 0;
	}
	/**
	 * 実際にflvデータを読み込む部分です。
	 * @param buf バイトデータをいれれば、ffmpegがそれを認識します。
	 * @param size ffmpegから要求されている読み込みデータ量になります。
	 * @return 応答は実際に読み込めたsize量になります。
	 */
	@Override
	public int read(byte[] buf, int size) {
		System.out.println("読み込みします。" + size); // 32768?固定？
		try {
			int length = fis.available() < size ? fis.available() : size;
			System.out.println(length);
			fis.read(buf, 0, length);
			pos += length;
			System.out.println(pos);
			return length;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	/**
	 * ファイルのシーク動作です。
	 * ストリーミングしているという仮定なので、-1を返します。(シークさせない。)
	 */
	@Override
	public long seek(long offset, int whence) {
		return -1;
	}
	/**
	 * 書き込み動作です。
	 * note:今回は読み込みにしか興味ないので-1のエラーを返しておきます。
	 * @param buf 書き込むべきバイトデータ
	 * @param size 書き込むべきデータ量
	 * @return 書き込めたデータ量
	 */
	@Override
	public int write(byte[] buf, int size) {
		return -1;
	}
}
