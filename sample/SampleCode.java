import com.example.simple-socket-client-lib.jar;

public class Sample
{
    private SampleInterface sampleInterface;
    private ClientConfig config;
    private SocketClient client;

    public static void main(String[] args) 
    {
        sampleInterface = new SampleInterface();

        String host = "example.com";
        int port = 10000;

        // サーバーとの通信先を設定,.set～関数は省略可
        // .build関数は必須！！
        config = new ClientConfig.ConfigBuilder(host,port).setTimeOut(5000)             // 接続,データ受信時のタイムアウト時間(ms)
                                                          .setRetryCount(5)             // サーバーとの接続失敗、切断時に再接続を試行する回数(回)
                                                          .setMaxReadSize(512)           // サーバーからのデータ受信時に一度に読み取れるデータのバイト数(Byte)
                                                          .setConnectCheckCycle(5000)   // サーバーとの接続監視周期(ms)
                                                          .setConnectCheckChar("ff")    // 接続監視時にサーバーへ送信される文字列：UTF-8でbyte[]に変換して送信されます。
                                                          .build()                      // 設定された情報を反映したClientConfigクラスのインスタンスを生成する

        // SocketClientクラスのインスタンスを作成
        client = new SocketClient(sampleInterface,config);

        // サーバーとの接続を行う、
        // 接続の確立が成功したらデータ受信スレッドと
        // 定周期の接続監視スレッドが自動的に開始される
        client.connect();

        string messege = "HelloWorld";

        // HelloWorldをbyte[]に変換
        byte[] sendBytes = message.getBytes(StandardCharsets.UTF_8);

	// サーバーに対して"HelloWorld"を送信
        client.sendMessage(sendBytes);
    }
}

// ClientEventListenerインターフェースを継承したクラス
public class SampleInterface implements SocketClient.ClientEventListener
{
    /*
    superメソッドがオーバーライドされている関数は
    defaultで定義しているので必ず実装する必要は無い。
    */

    @Override
    public onConnected()
    {
        SocketClient.ClientEventListener.super.onConnected();

        // サーバーとの接続成功時に呼び出される
    }

    @Override
    public void onDataReceived(byte[] data)
    {
        // データ受信時に受け取る
    }

    @Override
    public void onErrorReceived(ErrorInfo e)
    {
        // エラー発生時に例外を受け取る
        // 例外データの格納クラスを受け取る
    }

    @Override
    public onDisConnected()
    {
        SocketClient.ClientEventListener.super.onDisConnected();

        // サーバーとの接続が正常に終了した際に呼び出される
    }

    @Override
    public void onRetryStarted()
    {
        SocketClient.ClientEventListener.super.onRetryStarted();

        // 例外やエラーによるサーバーとの切断時の再接続開始時に呼び出される
    }
}
