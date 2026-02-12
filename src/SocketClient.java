package com.example.simple_socket_client_lib_ver201;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** ソケット通信のクライアントの処理を提供する汎用クラス */
@SuppressWarnings("rawtypes")
public class SocketClient
{
/// ---- ソケット通信に直接関係する変数 ---- ///
    /** SocketEventListenerインターフェースが実装されたクラスのインスタンス */
    private final ClientEventListener listener;
    /** 通信先などの情報を設定したClientConfigクラスのインスタンス */
    private final ClientConfig config;
    /** Tcpクライアント(ソケット通信)を実装するためのクラス */
    private Socket socket;
    /** クライアントからサーバーへのデータ送信を行うためのクラス */
    private OutputStream outputStream;
    /** サーバーから受信したデータを受け取るためのクラス */
    private InputStream inputStream;
    //////////////////////////////////////////

    /// --- エラー処理用 --- ///
    /** 現状考慮可能な例外クラスとそのSummaryメッセージ */
    private static final Map<Class<? extends Exception>, String> ERROR_MAP = Map.ofEntries(
            Map.entry(UnknownHostException.class, "指定されたホスト名が解決できません。"),
            Map.entry(ConnectException.class, "指定されたサーバーへ接続が出来ません。"),
            Map.entry(SocketTimeoutException.class, "接続がタイムアウトしました。"),
            Map.entry(NoRouteToHostException.class, "サーバーへのルートが見つかりません。"),
            Map.entry(BindException.class, "指定されたポートはすでに使用されています。"),
            Map.entry(SocketException.class, "ソケットがすでに閉じているか切断されています。"),
            Map.entry(EOFException.class, "サーバーが接続を終了しました。"),
            Map.entry(UTFDataFormatException.class, "不正な文字データを受信しました。"),
            Map.entry(IOException.class, "入出力エラーが発生しました。")
    );
    ///////////////////////////

    /** --- 処理ステータス --- */
    public enum Phase
    {
        CONNECT,
        SEND,
        RECEIVE,
        DISCONNECT,
        RETRY
    }
    Phase commonPhase;              // クラスで共通のPhase
    ////////////////////////////////////////////

    /// ---- 各処理を非同期で行うためのスレッド ----///
    /** サーバーへの接続、データの送信、データの受信を行うスレッドをまとめたもの */
    private ExecutorService threadPool;

    /** 一定周期で行うサーバーとの接続確認用 */
    private ScheduledExecutorService connectCheckScheduler;
    /** 再接続用 */
    private ScheduledExecutorService retryConnectScheduler;
    //////////////////////////////////////////////

    /// --- Scheduleによる定期処理の終了用 --- ///
    /** 接続確認用 */
    private ScheduledFuture connectCheckFuture;
    /** 再接続用 */
    private ScheduledFuture retryFuture;

    /// --- スレッドのループ回数制御用変数 --- ///
    private int loopCount = 0;

    /// --- データ受信スレッドの開始・停止を判断するFlag --- ///
    private boolean dataReceiveFlag = false;
    ////////////////////////////////////////////////////

    /// --- サーバーとの接続処理が完了するまで待機させるためのもの --- ///
    private final CountDownLatch latch;
    /////////////////////////////////////////////


    /**
     * コンストラクタ
     * @param listener:ClientListenerインターフェースを実装したクラス
     * @param config:ClientConfigクラス
     */
    public SocketClient(ClientEventListener listener, ClientConfig config)
    {
        this.listener = listener;
        this.config = config;
        latch = new CountDownLatch(1);
        InitThread();
    }

    /** スレッドの初期化・再生成を行う関数 */
    private void InitThread()
    {
        /* スレッドの最小数は0，最大数は3,処理終了後のスレッドの寿命は60秒,処理を入れるためのQueue */
        if(threadPool == null || threadPool.isShutdown()) threadPool = new ThreadPoolExecutor(0,3,60L, TimeUnit.SECONDS,new SynchronousQueue<>());

        if(connectCheckScheduler == null || connectCheckScheduler.isShutdown()) connectCheckScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /** 外部から呼び出す用のサーバーとの接続関数 */
    public void connect()
    {
        // 現在のStatusを設定
        Phase phase = Phase.CONNECT;

        InitThread();

        // 再接続試行回数をリセット
        loopCount = 0;

        // サーバーとの接続関数を非同期スレッドで開始
        threadPool.execute(()->
        {
            try
            {
                connection();                       // 接続関数の呼び出し
                NotifyConnected();                  // 接続が正常完了したことをコールバック関数へ通知
            }
            catch(Exception e)
            {
                // 処理中のエラーなどで接続が失敗した場合　↓

                if(config.getRETRY_COUNT() <= 0)
                {
                    NotifyError(e,phase);
                }
                else
                {
                    DisConnect();                       // サーバーとの接続を全て切断

                    // サーバーとの再接続処理を非同期スレッドで実行
                    startReConnect();
                }
            }
        });
    }

    /** サーバーとの接続確立関数 */
    private synchronized void connection() throws Exception
    {
        // 各処理用のスレッドクラスを初期化
        InitThread();

        socket = new Socket();
        socket.connect(new InetSocketAddress(config.getHOST(),
                config.getPORT()),config.getTIMEOUT());             // 設定されたIPとPORTのサーバーへ接続要求
        socket.setSoTimeout(config.getTIMEOUT());
        inputStream = socket.getInputStream();                      // 接続が出来たらそのサーバーとのInputStreamを取得
        outputStream = socket.getOutputStream();                    // サーバーへのOutputStreamの取得
    }

    /** 再接続スレッドを開始する関数 */
    private synchronized void startReConnect()
    {
        // 再接続用スレッドを初期化or再生成
        if(retryConnectScheduler == null || retryConnectScheduler.isShutdown()) retryConnectScheduler = Executors.newSingleThreadScheduledExecutor();

        // スレッドを開始
        if(retryFuture == null || retryFuture.isCancelled())
            retryFuture = retryConnectScheduler.scheduleWithFixedDelay(
                    this::RetryConnection,0,config.getTIMEOUT(), TimeUnit.MILLISECONDS);
    }

    /** 設定された回数分サーバーへの接続処理をリトライする関数 */
    private void RetryConnection()
    {
        Phase phase = Phase.RETRY;
        commonPhase = Phase.RETRY;
        try
        {
            NotifyRetryStarted();

            // 再接続を行った回数が設定した回数以上か？
            if(loopCount >= config.getRETRY_COUNT())
            {
                NotifyError(new Exception("ReTryException:over the retry count"),phase);
                retryFuture.cancel(false);          // 設定以上なら再接続処理を止める
                DisConnect();
            }
            else
            {
                loopCount++;                            // 再接続試行回数カウントを増やす

                connection();                           // サーバーとの接続処理を実行する

                NotifyConnected();                      // サーバーとの接続処理に成功すれば通知コールバックを呼び出す
                retryFuture.cancel(false);           // 接続成功なので再接続処理を止める
            }
        }
        catch(Exception e)
        {
            // 再接続処理失敗時の処理　↓

            // 再接続回数が設定回数以上か？
            if(loopCount >= config.getRETRY_COUNT())
            {
                retryFuture.cancel(false);          // 設定回数以上なら再接続処理を止める

                DisConnect();

                NotifyError(new Exception("ReTryException:over the retry count"),phase);                  // エラーコールバック関数を呼び出す
            }
        }
    }

    /**
     * 外部からデータ送信を行うための関数
     * @param data:サーバーへ送信したいデータをバイト配列にしたもの
     */
    public void sendMessage(byte[] data){
        if(threadPool == null || threadPool.isShutdown()) threadPool = new ThreadPoolExecutor(0,3,60L, TimeUnit.SECONDS,new SynchronousQueue<>());
        threadPool.execute(() -> sendMessageInternal(data));
    }
    /** サーバーへのデータ送信関数 */
    private void sendMessageInternal(byte[] data)
    {
        Phase phase = Phase.SEND;
        try
        {
            boolean timeoutFlag = latch.await(config.getTIMEOUT(),TimeUnit.MILLISECONDS);

            if(timeoutFlag)
            {
                if (socket != null && socket.isConnected())
                {
                    outputStream.write(data);
                    outputStream.flush();
                }
                else
                {
                    NotifyError(new Exception("Socket not connected : サーバーと接続されていません。"),phase);
                }
            }
            else
            {
                NotifyError(new Exception("Socket Connect TimeOut:サーバーと接続されませんでした。"),phase);
            }
        }
        catch (Exception e)
        {
            NotifyError(e,phase);
        }
    }

    /** 外部からデータ受信を開始するための関数 */
    private void startDataReceive(){
        if(dataReceiveFlag) return;     // 受信スレッドの多重起動防止
        dataReceiveFlag = true;
        threadPool.execute(this::receiveMessage);
    }
    /** データ受信処理を停止するための関数 */
    private void stopDataReceive() {
        dataReceiveFlag = false;
    }
    /** サーバーからのデータ受信関数(一度に読み取れるのは最大1024バイト) */
    private void receiveMessage()
    {
        Phase phase = Phase.RECEIVE;

        while(dataReceiveFlag && socket != null && !socket.isClosed())
        {
            try
            {
                // ここの処理は変える必要は無い
                // 最初と最後を判定するのはアプリ側の実装

                byte[] buffer;

                if(config.getMAX_READ_SIZE() != 1024) buffer = new byte[config.getMAX_READ_SIZE()];
                else buffer = new byte[1024];

                int bytesRead;

                bytesRead = inputStream.read(buffer);

                if(bytesRead == -1)
                {
                    DisConnect();
                    startReConnect();
                    break;
                }

                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                NotifyDataReceive(data);
            }
            catch(SocketTimeoutException ignored) {}
            catch(Exception ex)
            {
                NotifyError(ex,phase);
            }
        }
    }


    /** 外部から定周期の接続監視を開始する関数 */
    private void startConnectCheck(){
        if(connectCheckFuture == null || connectCheckFuture.isCancelled()) connectCheckFuture = connectCheckScheduler.scheduleWithFixedDelay(this::ConnectionCheck,0,config.getCONNECT_CHECK_CYCLE(), TimeUnit.MILLISECONDS);
    }
    /** 一定周期でのデータ送信による接続チェック関数(データ送信による接続チェック処理のみ) */
    private void ConnectionCheck()
    {
        try
        {
            if(socket != null && socket.isConnected())
            {
                if(config.getCONNECT_CHECK_CHAR().equals(" ")) outputStream.write(0);
                else
                {
                    byte[] sendData = config.getCONNECT_CHECK_CHAR().getBytes(StandardCharsets.UTF_8);
                    outputStream.write(sendData);
                }

                outputStream.flush();
            }
        }
        catch(Exception e)
        {
            DisConnect();

            connectCheckFuture.cancel(false);

            startReConnect();
        }
    }


    /** 外部からの接続の正常終了やサーバーからのEOF(通信切断)の接続終了および接続終了コールバックを呼び出す関数 */
    public synchronized void disconnect()
    {
        Phase phase = Phase.DISCONNECT;
        try
        {
            // データ受信処理の停止(Whileの停止)
            stopDataReceive();

            // 各スレッドの停止
            if(connectCheckFuture != null && !connectCheckFuture.isCancelled()) connectCheckFuture.cancel(false);
            if(retryFuture != null && !retryFuture.isCancelled()) retryFuture.cancel(false);
            if(threadPool != null && !threadPool.isShutdown()) threadPool.shutdown();
            if(connectCheckScheduler != null && !connectCheckScheduler.isShutdown()) connectCheckScheduler.shutdown();
            if(retryConnectScheduler != null && !retryConnectScheduler.isShutdown()) retryConnectScheduler.shutdown();

            if (socket != null) try { socket.close(); } catch (IOException ignored) {}
            if (inputStream != null) try { inputStream.close(); } catch (IOException ignored) {}
            if (outputStream != null) try { outputStream.close(); } catch (IOException ignored) {}

            socket = null;
            inputStream = null;
            outputStream = null;

            NotifyDisConnected();
        }
        catch(Exception e)
        {
            NotifyError(e,phase);
        }
    }

    /** 通信切断コールバックを呼ばず外部からも呼び出させない(異常終了やエラー時のリトライ時に使用される) */
    private synchronized void DisConnect()
    {
        Phase phase = Phase.DISCONNECT;
        try
        {
            // データ受信処理の停止(Whileの停止)
            stopDataReceive();

            // 各スレッドの停止
            if(connectCheckFuture != null && !connectCheckFuture.isCancelled()) connectCheckFuture.cancel(false);
            if(retryFuture != null && !retryFuture.isCancelled()) retryFuture.cancel(false);
            if(threadPool != null && !threadPool.isShutdown()) threadPool.shutdown();
            if(connectCheckScheduler != null && !connectCheckScheduler.isShutdown()) connectCheckScheduler.shutdown();
            if(retryConnectScheduler != null && !retryConnectScheduler.isShutdown()) retryConnectScheduler.shutdown();

            if (socket != null) try { socket.close(); } catch (IOException ignored) {}
            if (inputStream != null) try { inputStream.close(); } catch (IOException ignored) {}
            if (outputStream != null) try { outputStream.close(); } catch (IOException ignored) {}

            socket = null;
            inputStream = null;
            outputStream = null;
        }
        catch(Exception e)
        {
            NotifyError(e,phase);
        }
    }


    /** 例外の種類を分けてErrorInfoクラスに例外情報を格納して返す関数 */
    private ErrorInfo CheckErrorType(Exception e, Phase phase)
    {
        ErrorInfo eInfo = new ErrorInfo();

        eInfo.exception = e;
        eInfo.eMessage = e.getMessage();
        if (config != null) {
            eInfo.host = config.getHOST();
            eInfo.port = config.getPORT();
        }
        eInfo.phase = phase;

        eInfo.summary = phase.name()+" : "+ERROR_MAP.getOrDefault(e.getClass(),"不明なエラーが発生しました。");

        return eInfo;
    }

    /// --- イベント通知関数 --- ///

    /**
     * エラーの発生時にコールバック関数を呼び出す関数
     * @param e:発生した例外
     * @param phase:現在処理中の処理フェーズ
     */
    private void NotifyError(Exception e, Phase phase) {
        ErrorInfo info = CheckErrorType(e,phase);
        if (listener != null) listener.onErrorReceived(info);
    }

    /**
     * サーバーからのデータ受信時にコールバック関数を呼び出す関数
     * @param data:サーバーから受信したデータ
     */
    private void NotifyDataReceive(byte[] data) {
        if (listener != null) listener.onDataReceived(data);
    }
    /** サーバーとの接続確立時にコールバック関数を呼び出す関数
     *  接続成功時に周期的な接続監視とデータ受信のスレッドも開始する */
    private void NotifyConnected() {
        latch.countDown();
        startConnectCheck();
        startDataReceive();
        if (listener != null) listener.onConnected();
    }
    /** エラーの発生時にコールバック関数を呼び出す関数 */
    private void NotifyDisConnected(){
        if(listener != null) listener.onDisConnected();
    }
    /** サーバーへの再接続試行開始時にコールバックを呼び出す関数 */
    private void NotifyRetryStarted(){if(listener != null) listener.onRetryStarted();}
    ///////////////////////////////////////////


    /** コールバック関数が定義されたインターフェース */
    public interface ClientEventListener
    {
        /** サーバーからデータ受信時に呼ぶコールバック関数
         * @param data　受信したデータ　*/
        void onDataReceived(byte[] data);
        /** エラー発生時に呼ぶコールバック関数
         * @param e　発生したエラー　*/
        void onErrorReceived(ErrorInfo e);
        /** 接続確立時に呼ぶコールバック関数　*/
        default void onConnected() {}
        /** 正常に接続が切断されたときに呼ぶコールバック関数 */
        void onDisConnected();
        /** 接続リトライのスタート時におyばれるコールバック関数 */
        default void onRetryStarted(){}
    }

    /** --- 例外発生時に例外情報を格納するクラス --- */
    public static class ErrorInfo
    {
        // --- 格納データ ---
        private Exception exception;    // 実際の例外オブジェクト
        private String eMessage;        // 例外メッセージまたは要約
        private String host;            // 通信先ホスト名
        private int port;               // 通信先ポート
        private Phase phase;            // 発生フェーズ（例 :  "CONNECT", "SEND", "RECEIVE"）
        private String summary;         // 発生したエラーの要約("connect : 指定されたホスト名がかいけつできません。etc)


        // --- 格納したデータのGetter ---

        /**
         * @return : 発生したExceptionのメッセージ
         */
        public String getMessage() { return eMessage; }

        /**
         * @return : 発生したException
         */
        public Exception getException() { return exception; }

        /**
         * @return : 接続しているor仕様としたサーバーのホスト名(IPアドレス)
         */
        public String getHost() { return host; }

        /**
         * @return : 接続しているor仕様としたサーバーのポート番号
         */
        public int getPort() { return port; }

        /**
         * @return : 例外発生時の処理フェーズ
         */
        public Phase getPhase() { return phase; }

        /**
         * @return : 発生した例外の簡易区分および要約(例 : 指定されたホスト名が解決できません。etc)
         */
        public String getSummary() { return summary; }
    }
}
