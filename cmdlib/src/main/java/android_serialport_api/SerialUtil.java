package android_serialport_api;



import com.ebanswers.cmdlib.callback.SerialPortConnectedListener;
import com.ebanswers.cmdlib.utils.HexUtils;
import com.ebanswers.cmdlib.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author Snail
 * Date 2019/5/22
 * Email yuesnail@gmail.com
 */

public class SerialUtil {
    private static final String TAG = "SerialUtil";
    private static final int BAUD_RATE = 9600;
    private String TTY = "/dev/ttyS1";
    private static final int FLAG = 0;
    private SerialPort serialPort = null;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private SerialPotReadCallBack readCallBack;
    private String errorMsg;
    public boolean isOpen = false;
    private Thread readThread = null;
    private SerialPortConnectedListener listener;
    private volatile long receiveCmdTime = System.currentTimeMillis();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    public SerialUtil() {
    }

    public SerialUtil(String tty, int baudRate, int flag) {
        this.openSerial(tty, baudRate, flag);
    }

    public boolean openSerial(String tty, int baudRate, int flag) {
        TTY = tty;
        try {
            this.serialPort = new SerialPort(new File(tty), baudRate, flag);
            this.mInputStream = this.serialPort.getInputStream();
            this.mOutputStream = this.serialPort.getOutputStream();
            LogUtils.d(TAG, "open success:" + tty);
        } catch (IOException var5) {
            LogUtils.e(TAG, "open failed:" + var5.toString());
            this.errorMsg = var5.toString();
            this.isOpen = false;
            return false;
        } catch (SecurityException var6) {
            LogUtils.e(TAG, "open failed:have no read/write permission to the serial port");
            this.isOpen = false;
            this.errorMsg = var6.toString();
            return false;
        }
        checkIsConneted();
        this.isOpen = true;
        this.errorMsg = null;
        return true;
    }

    public String getErrorMsg() {
        return this.errorMsg;
    }

    public void sendCommands(byte[] cmds) {
        LogUtils.d(TAG, "sendCommands: " + HexUtils.bytesToHexString(cmds));
        if (null != this.mOutputStream) {
            try {
                this.mOutputStream.write(cmds);
                this.mOutputStream.flush();
            } catch (IOException var3) {
                LogUtils.e(TAG, "sendCommands failed:" + var3.toString());
                this.errorMsg = var3.toString();
            }
        } else {
            LogUtils.e(TAG, "sendCommands failed:null");
        }

    }

    public void initReadThread(SerialPotReadCallBack callBack) {
        this.readCallBack = callBack;
        if (null != SerialUtil.this.mInputStream) {
            this.readThread = new Thread() {
                @Override
                public void run() {
                    byte[] data = new byte[100];
                    while (!this.isInterrupted()) {
                        try {
                            if (null != SerialUtil.this.mInputStream && null != data) {
                                int len = SerialUtil.this.mInputStream.read(data);
                                if (len > 0) {
                                    if (SerialUtil.this.readCallBack != null) {
                                        SerialUtil.this.readCallBack.readData(len, data);
                                        receiveCmdTime = System.currentTimeMillis();
                                    }
                                }
                            }
                        } catch (IOException var3) {
                            this.interrupt();
                            LogUtils.e(TAG, "readData error:" + var3.toString());
                        }
                    }

                }
            };
            this.readThread.start();
        } else {
            LogUtils.e(TAG, "initReadThread failed:null");
        }
    }

    public void stopReadThread() {
        if (null != this.readThread) {
            this.readThread.interrupt();
            this.readThread = null;
        }
    }

    public void closeSerialPort() {
        try {
            if (null != this.mInputStream) {
                this.mInputStream.close();
                this.mInputStream = null;
            }

            if (null != this.mOutputStream) {
                this.mOutputStream.close();
                this.mOutputStream = null;
            }

            if (null != this.serialPort) {
                this.serialPort.close();
                this.serialPort = null;
            }

            this.isOpen = false;
        } catch (IOException var2) {
            this.isOpen = false;
            LogUtils.e(TAG, "closeSerialPort failed:" + var2.toString());
        }

    }

    /**
     * 检测通讯是否正常
     * 每5s检测一次串口是否通讯正常
     */
    public void checkIsConneted() {
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                boolean status = System.currentTimeMillis() - receiveCmdTime < 2000 ? true : false;
                if (status && null != listener) {
                    listener.connected();
                }
                if (!status && null != listener) {
                    listener.disConnected();
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void setConnectedListener(SerialPortConnectedListener listener) {
        this.listener = listener;
    }

    public void resetReceiveTime() {
        this.receiveCmdTime = System.currentTimeMillis();
    }

    public interface SerialPotReadCallBack {
        void readData(int var1, byte[] var2);
    }
}
