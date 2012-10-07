Xuggleの動作確認をするために、ぱぱっとつくったプログラム
eclipse上で動作確認しています。

■つくった理由
flvのh.264形式のファイル読み込みプログラムがどうしてもうまくうごかないので、デバッグしたいなと思いました。
１つ１つ動作を分解しつつ実験したので、公開しておけば、他の人の役にたつのでは？とおもったので、公開してみました。

■やったこと。
1:eclipseの環境変数にデータを追加
DYLD_LIBRARY_PATH:/Users/xxxx/xuggletest/xuggle/java/xuggle-xuggler/dist/stage/usr/local/lib
LinuxならLD_LIBRARY_PATH、windowsの場合は不明です。
自分のxuggleのインストールにあわせて設定してください。
なお、ローカルで確認したので、インストールしているxuggleはgoogle codeにのっている方のものになります。

2:必要なjarファイルの追加
Add External Jarsでライブラリをいくつか追加
追加したファイルは
commons-cli.jar
logback-classic.jar
logback-core.jar
slf4j-api.jar
xuggle-xuggler.jar
すべて/Users/xxxx/xuggletest/xuggle/java/xuggle-xuggler/dist/stage/usr/local/share/java/jarsにはいっていました。

3:このプログラムのEntryを実行する。
Transcoderを適宜いれかえていろいろな動作させてみてください。

■ファイルの説明
com.ttProject.xuggle.Entry.java
 導入部今回のプログラムでは、本当はThreadを使う理由はないのですが、
 rtmpからのダウンロードデータをまわすみたいなことをする場合は、
 xuggleのcontainerを開く動作とかがthreadを停止させたりしてしまうので
 慣例的に別スレッドで動作するようにしています。

com.ttProject.xuggle.Transcoder.java
 変換動作の中心的なことをやってるプログラム
 いろいろ追加していくとプログラムがどんどんおおきくなってしまう。

com.ttProject.xuggle.flv.FlvHandlerFactory.java
 ffmpeg -i input.flv http://hoge.com/output.cgi
 コマンドのときに入力や出力にファイルやurlをいれますが、任意のurlをいれることができますが、
 IURLProtocolHandlerFactoryを登録しておくと、ここに任意の新規urlを追加することができます。
 今回はDEFAULT_PROTOCOLで設定しているflvFileInputを一時的に登録してやることで次のurlを渡すことで
 独自のflvHandlerが処理につかわれるようにしています。
 flvFileInput:test

com.ttProject.xuggle.flv.FlvHandler.java
 FlvHandlerFactoryが入出力のコントロールなら、こちらのファイルは実際の読み込み書き込み動作を実施する部分です。
 読み込み動作では、対象コンテナのファイルの形としてデータを応答。
 書き込み動作では、対象コンテナのファイルの形としてデータを受け取ります。
