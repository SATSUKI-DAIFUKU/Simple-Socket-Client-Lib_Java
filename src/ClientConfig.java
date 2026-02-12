package com.example.simple_socket_client_lib_ver201;

/** ソケット通信を行う際の設定値を格納・保存するクラス
 *  ビルダーパターンを使用しているためインスタンスはConfigBuilderクラスを使用して生成する*/
public class ClientConfig
{
    private final String HOST;
    private final int PORT;
    private final int TIMEOUT;
    private final int RETRY_COUNT;
    private final long CONNECT_CHECK_CYCLE;
    private final String CONNECT_CHECK_CHAR;
    private final int MAX_READ_SIZE;

    private ClientConfig(ConfigBuilder builder)
    {
        this.HOST = builder.HOST;
        this.PORT = builder.PORT;
        this.TIMEOUT = builder.TIMEOUT;
        this.RETRY_COUNT = builder.RETRY_COUNT;
        this.CONNECT_CHECK_CYCLE = builder.CONNECT_CHECK_CYCLE;
        this.CONNECT_CHECK_CHAR = builder.CONNECT_CHECK_CHAR;
        this.MAX_READ_SIZE = builder.MAX_READ_BYTE_SIZE;
    }

    public String getHOST(){return this.HOST;}
    public int getPORT(){return this.PORT;}
    public int getTIMEOUT(){return this.TIMEOUT;}
    public int getRETRY_COUNT(){return this.RETRY_COUNT;}
    public long getCONNECT_CHECK_CYCLE(){return this.CONNECT_CHECK_CYCLE;}
    public String getCONNECT_CHECK_CHAR(){return this.CONNECT_CHECK_CHAR;}
    public int getMAX_READ_SIZE(){return this.MAX_READ_SIZE;}

    /** ClientConfigクラスに値を設定してインスタンスを生成するためのクラス
     *  ビルダーパターンを使用しているため最後に必ず.build()メソッドを呼ぶこと！！ */
    public static class ConfigBuilder
    {
        // 必須
        private final String HOST;
        private final int PORT;

        // オプション
        private int TIMEOUT = 3000;              // 3秒;
        private int RETRY_COUNT = 1;             // 1回;
        private long CONNECT_CHECK_CYCLE = 3000; // 3000ms(3秒);
        private String CONNECT_CHECK_CHAR = " "; // NULLバイト
        private int MAX_READ_BYTE_SIZE = 1024;   // サーバーからの受信データを一回でどれだけ読み取るか

        /**
         * ConfigBuilderのコンストラクタ
         * @param HOST：接続先サーバーのホスト名(IPアドレス)
         * @param PORT：接続先サーバーのポート番号
         */
        public ConfigBuilder(String HOST,int PORT)
        {
            this.HOST = HOST;
            this.PORT = PORT;
        }

        /**
         * サーバーとの通信を行う際のタイムアウト時間を設定する。
         * @param TIMEOUT：タイムアウト時間(ms) 初期値：3000ms
         */
        public ConfigBuilder setTimeout(int TIMEOUT){this.TIMEOUT = TIMEOUT; return this;}

        /**
         * サーバーとの通信・接続が失敗した場合のリトライ回数を設定する
         * @param RETRY_COUNT：再接続時の試行回数(回) 初期値：1回
         */
        public ConfigBuilder setRetryCount(int RETRY_COUNT){this.RETRY_COUNT = RETRY_COUNT; return this;}

        /**
         * サーバーとの接続監視を行う際の周期を設定する。ここで設定された周期ごとにサーバーへデータが送られる。
         * @param CONNECT_CHECK_CYCLE：サーバーとの接続監視周期(ms) 初期値：3000ms
         */
        public ConfigBuilder setConnectCheckCycle(long CONNECT_CHECK_CYCLE){this.CONNECT_CHECK_CYCLE = CONNECT_CHECK_CYCLE; return this;}

        /**
         * 周期的なサーバとの接続監視を行う際にサーバーへ送るデータを設定する
         * @param CONNECT_CHECK_CHAR：一定周期でサーバーへ送るデータ 初期値：NULL文字(0x00)
         */
        public ConfigBuilder setConnectCheckChar(String CONNECT_CHECK_CHAR){this.CONNECT_CHECK_CHAR = CONNECT_CHECK_CHAR; return this;}

        /**
         * サーバーからのデータ受信時に一度に何バイトを読み込むかを設定する
         * @param MAX_READ_SIZE：一度に読み込むバイト数 初期値：1024Byte(1KB)
         */
        public ConfigBuilder setMaxReadSize(int MAX_READ_SIZE){this.MAX_READ_BYTE_SIZE = MAX_READ_SIZE; return this;}

        /**
         * 設定値をもとにClientConfigクラスのインスタンスを生成する
         * @return：ClientConfigクラスのインスタンス
         */
        public ClientConfig build()
        {
            return new ClientConfig(this);
        }
    }
}
