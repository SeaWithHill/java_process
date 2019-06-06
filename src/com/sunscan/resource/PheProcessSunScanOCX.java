package cn.com.bankit.phoenix.ui.swing.custom.gui;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.TCHAR;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.bankit.phoenix.notification.ISubject;
import cn.com.bankit.phoenix.notification.NotificationManager;

/**
 * 第二进程
 * 
 * @author
 */
public class PheProcessSunScanOCX extends Composite {

	public static final String CreateSunScan_XML = "<root><head><transcode>CREATETRANSCODE</transcode></head><body><appid>%s</appid><transcode>%s</transcode></body></root>";
	public static final String ShowSunScan_XML = "<root><head><transcode>SHOWTRANSCODE</transcode></head><body><param><![CDATA[%s]]></param><param><![CDATA[%s]]></param></body></root>";
	public static final String ShowSunScan_XML_FILE = "<root><head><transcode>SHOWTRANSCODE</transcode></head><body><param>%s</param><param>%s</param></body></root>";
	public static final String CommOcxFunction_XML = "<root><head><transcode>COMMOCXFUNCTION</transcode></head><body><param><![CDATA[%s]]></param></body></root>";
	public static final String CommOcxFunction_XML_FILE = "<root><head><transcode>COMMOCXFUNCTION</transcode></head><body><param>%s</param></body></root>";

	/**
	 * logger
	 */
	private static Logger logger = LoggerFactory
			.getLogger(PheProcessSunScanOCX.class);

	private static final String ENCODING = "GBK";
	private Composite parent;
	private int deputyWindowHandle = 0;
	private Process process;

	private int abcPort;
	private int sunScanPort;

	private ServerSocket server;

	private boolean visible = true;

	private String ip;

	private String port;

	private final AtomicBoolean isRunning = new AtomicBoolean(true);

	private final CountDownLatch ocxProcessCountDownLatch = new CountDownLatch(
			1);

	// private CountDownLatch countDownLatch;

	public int connectTimeout = 5000;
	public int timeout = 30 * 60 * 1000;

	private final AtomicReference<String> errMsg = new AtomicReference<String>();

	private AtomicReference<String> resultMsg = new AtomicReference<String>();

	public boolean isCreated = false;

	protected Socket sunServerSocket;

	// private Map<String, InnerOLEListener> listenerMap = new
	// ConcurrentHashMap<String, InnerOLEListener>();

	/**
	 * 异步调用结果通知的事件
	 */
	// private Map<String, String> asynEventMap = new ConcurrentHashMap<String,
	// String>();
	// private ExecutorService executor = Executors.newFixedThreadPool(10);
	private Lock singleInvokeLock = new ReentrantLock();

	// private GeneralFunctionTaskQueue taskQueue;

	public PheProcessSunScanOCX(Composite parent, final int style, String progId) {
		super(parent, style);
		this.parent = parent;

		try {
			// 创建控件

			init(progId);

			embed();

			// 设置影像控件参数

			// 设置目录参数

			// 启动任务处理\
			/*
			 * AsynTaskHandle handle = new AsynTaskHandle();
			 * handle.setSunScanPort(this.sunScanPort); taskQueue = new
			 * GeneralFunctionTaskQueue(handle); taskQueue.init();
			 * taskQueue.start(executor);
			 */

		} catch (Exception e) {

			logger.error(e.getMessage(), e);
		}

		// 启动回调函数
		addEventListener();

		parent.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent event) {
				dispose();
			}
		});
	}

	public void setVisbile(boolean visible) {
		logger.debug("setVisbile:" + visible);
		this.visible = visible;
		if (deputyWindowHandle != 0) {
			if (!visible) {
				OS.ShowWindow(deputyWindowHandle, OS.SW_HIDE);
			} else {
				OS.ShowWindow(deputyWindowHandle, OS.SW_SHOW);
			}

		}
	}

	public void dispose() {
		System.out.println("==");
		isRunning.set(false);

		if (process != null) {
			process.destroy();
		}
	}

	public void init(String ocx_id) throws Exception {
		try {
			// File configDir = new
			// File(Platform.getConfigurationLocation().getURL().getFile());
			// File driverDir = new File(configDir.getParent(), "driver");
			File cutDir = new File("");
			File driverDir = new File(cutDir.getAbsolutePath() + "\\DeviceHost");

			// #获取ABC第一进程端口
			// #建立ABC第一进程Socket服务
			server = new ServerSocket(0);
			server.setSoTimeout(10000);
			abcPort = server.getLocalPort();

			// #启动OCX第二进程
			String command = "\"" + driverDir + "\\SunOCXServer.exe " + abcPort;
			logger.debug("开始启动OCX第二进程，命令是：" + command);
			ProcessBuilder pb = new ProcessBuilder();
			pb.command(driverDir + "\\SunOCXServer.exe",
					String.valueOf(abcPort)).start();
			sunScanPort = getSunScanPort();
			if (ocxProcessCountDownLatch.await(3, TimeUnit.MINUTES)) {
				if (sunScanPort == 0) {
					throw new IOException("启动OCX第二进程失败，申请端口失败");
				}

				registerCallbackListener();

				final String err = errMsg.get();
				if (err != null && err.length() != 0) {
					logger.error("启动OCX第二进程失败，" + err);
					parent.getDisplay().asyncExec(new Runnable() {
						public void run() {
							MessageBox messageBox = new MessageBox(parent
									.getShell());
							messageBox.setText("异常");
							messageBox.setMessage("启动OCX第二进程失败，" + err);
							messageBox.open();
						}
					});
					throw new IOException("启动OCX第二进程失败，" + err);
				} else {
					logger.debug("启动OCX第二进程成功...");
					// #内嵌OCX第二进程
					while (deputyWindowHandle == 0) {
						deputyWindowHandle = OS.FindWindow(new TCHAR(0,
								"#32770", true), new TCHAR(0, "YingXiang"
								+ (sunScanPort), true));
					}
					Thread.sleep(700);
					/*
					 * int i = 0; int child = OS.GetWindow(deputyWindowHandle,
					 * OS.GW_CHILD); while (child != 0) { childHandles[i++] =
					 * child; logger.debug("child:" + child); child =
					 * OS.GetWindow(child, OS.GW_HWNDNEXT); }
					 */
					logger.debug("父句柄是：" + Integer.toHexString(parent.handle)
							+ ",独立进程窗口句柄是："
							+ Integer.toHexString(deputyWindowHandle));

					parent.getDisplay().syncExec(new Runnable() {
						public void run() {
							embed();
						}
					});
				}
			} else {
				logger.error("启动OCX第二进程超时");
				parent.getDisplay().asyncExec(new Runnable() {
					public void run() {
						MessageBox messageBox = new MessageBox(parent
								.getShell());
						messageBox.setText("异常");
						messageBox.setMessage("启动OCX第二进程超时");
						messageBox.open();
					}
				});
				throw new IOException("启动OCX第二进程超时");
			}
		} catch (IOException e) {
			throw e;
		}
	}

	private int getSunScanPort() throws IOException {
		Socket socket = null;
		DataInputStream in = null;
		try {
			socket = server.accept();
			in = new DataInputStream(socket.getInputStream());

			byte[] headBytes = new byte[6];
			in.read(headBytes);// 读取报文头
			int headLen = Integer.valueOf(new String(headBytes));// 将报文头字节数组转换成int
			byte[] contents = toByteArray(in);
			String retStr = new String(contents, ENCODING);
			final String[] responses = retStr.trim().split("&&&");

			logger.info(String.format("报文头长度：%s,实际长度：%s,内容:%s", headLen,
					retStr.getBytes(ENCODING).length, retStr));

			if (responses[0].equals("port")) {
				ocxProcessCountDownLatch.countDown();
				return Integer.valueOf(responses[1]);
			}
		} finally {
			closeQuietly(in);
			closeQuietly(socket);
		}
		return 0;
	}

	/**
	 * 询问独立进程OleListener是否有返回值
	 */
	private void registerCallbackListener() {
		final CountDownLatch latch = new CountDownLatch(1);

		Thread serverThread = new Thread(new Runnable() {
			public void run() {
				latch.countDown();

				try {
					server.setSoTimeout(10000);
				} catch (SocketException e) {
					logger.error(e.getMessage(), e);
				}
				while (isRunning.get()) {
					Socket socket = null;
					DataInputStream in = null;
					DataOutputStream out = null;
					ByteArrayOutputStream byteOut = null;
					try {
						try {
							socket = server.accept();
						} catch (SocketTimeoutException e) {
							continue;
						} catch (Exception ex) {
						}

						in = new DataInputStream(socket.getInputStream());
						out = new DataOutputStream(socket.getOutputStream());
						byteOut = new ByteArrayOutputStream();

						byte[] retBytes = new byte[6];
						in.read(retBytes);// 读取报文头
						int headLen = Integer.parseInt(new String(retBytes,
								ENCODING));// 将报文头字节数组转换成int
						logger.info("回调SOCKET收到OCX消息长度：" + headLen);

						int divisor = headLen / 4096; // 报文有多少个4096字节
						int remainder = headLen % 4096; // 报文除4096字节后剩余字节
						if (divisor > 0) {
							for (int i = 0; i < divisor; i++) {
								retBytes = new byte[4096];
								in.readFully(retBytes);
								byteOut.write(retBytes);
							}
						}
						if (remainder >= 0) {
							retBytes = new byte[remainder];
							in.readFully(retBytes);
							byteOut.write(retBytes);
						}

						byte[] contents = byteOut.toByteArray();
						String req = new String(contents, ENCODING);
						logger.info("回调SOCKET收到OCX进程消息：" + req);

						final String[] responses = req.trim().split("&&&");

						if (responses[0].equals("method")) {
							resultMsg.set(responses[1]);
						} else if (responses[0].equals("callback")) {
							eventListenerCallBack(responses[1]);
						}

						String back = "Read_Fully";
						out.write(back.getBytes(ENCODING));
						out.flush();
					} catch (IOException e) {
						logger.error("消息处理异常：" + e.getMessage(), e);
					} finally {
						if (byteOut != null)
							try {
								byteOut.close();
							} catch (Exception e) {
							}

						if (out != null)
							try {
								out.close();
							} catch (Exception e) {
							}
						if (in != null)
							try {
								in.close();
							} catch (Exception e) {
							}
						if (socket != null)
							try {
								socket.close();
							} catch (Exception e) {
							}
					}
				}
			}
		});
		serverThread.setName("SunScanThread_Server");
		serverThread.setDaemon(true);
		serverThread.start();

		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}

	private void logEmbedLevel(Composite p) {
		while (p.getParent() != null) {
			p = p.getParent();
		}
		// logger.error("当前内嵌层数是：" + num);
	}

	private Composite embedParent = null;
	private int embedRet = 0;

	/**
	 * 内嵌
	 */
	public void embed() {
		embedParent = parent;

		logger.debug("内嵌的父句柄是：" + Integer.toHexString(embedParent.handle));

		// 计算层数
		logEmbedLevel(embedParent);

		// 获取原窗口样式
		final int oldStyle = OS.GetWindowLong(deputyWindowHandle, OS.GWL_STYLE);
		// 内嵌

		if (OS.IsWindowVisible(parent.getParent().handle)) {
			embedRet = OS.SetParent(deputyWindowHandle,
					parent.getParent().handle);
		} else {
			parent.getDisplay().timerExec(500, new Runnable() {
				public void run() {
					embed();
				}
			});
			return;
		}

		if (embedRet == 0) {
			logger.debug("初次内嵌失败，子窗口句柄："
					+ Integer.toHexString(deputyWindowHandle) + ",父窗口句柄："
					+ Integer.toHexString(embedParent.handle));
			// 内嵌失败，恢复窗口边框和标题栏
			OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE, OS.WS_OVERLAPPED
					| OS.WS_POPUP | OS.WS_BORDER | OS.WS_VISIBLE
					| OS.WS_CAPTION);
		} else {
			logger.debug("初次内嵌成功，子窗口句柄："
					+ Integer.toHexString(deputyWindowHandle) + ",父窗口句柄："
					+ Integer.toHexString(embedParent.handle));
			// 内嵌成功，去除边框且显示窗口
			OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE,
					(oldStyle & ~OS.WS_BORDER) | OS.WS_VISIBLE);
			OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE,
					(oldStyle & ~OS.WS_BORDER) | OS.WS_VISIBLE
							| OS.WS_CLIPCHILDREN);

			if (!visible) {
				logger.debug("隐藏控件");
				OS.ShowWindow(deputyWindowHandle, OS.SW_HIDE);
			}
		}

		// 重新定位程序
		int x = 0, y = 0;
		/*
		 * for (Composite p = parent; p != embedParent; p = p.getParent()) {
		 * //if((p.getLayout()) instanceof BookCompositeLayout){ // x=0; //
		 * break; //} Point r = p.getLocation(); x += r.x; y += r.y; } if(x==0){
		 * y=0; }
		 */

		Rectangle rect = parent.getClientArea();
		if (rect.width < 200) {
			rect = parent.getParent().getClientArea();
			OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width,
					rect.height, OS.SWP_DRAWFRAME);
		} else {
			OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width,
					rect.height, OS.SWP_DRAWFRAME);
		}

		// 防止第一次点击切换焦点
		// OS.SetFocus(parent.handle);
		OS.SetFocus(deputyWindowHandle);

		// resize时重新定位
		Listener resizeListener = new Listener() {
			public void handleEvent(Event event) {
				logger.debug("执行resize");

				if (embedRet == 0) {
					embedRet = OS.SetParent(deputyWindowHandle, parent.handle);
					logger.debug("重新内嵌窗口结果：" + (embedRet != 0));
					if (embedRet != 0) {
						// 内嵌成功，去除边框且显示窗口
						OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE,
								(oldStyle & ~OS.WS_BORDER) | OS.WS_VISIBLE
										| OS.WS_CLIPCHILDREN);
						if (!visible) {
							logger.debug("隐藏控件");
							OS.ShowWindow(deputyWindowHandle, OS.SW_HIDE);
						}
					}
				}

				int x = 0, y = 0;
				for (Composite p = parent; p != embedParent; p = p.getParent()) {
					// if((p.getLayout()) instanceof BookCompositeLayout){
					// x=0;
					// break;
					// }

					Point r = parent.getLocation();
					x += r.x;
					y += r.y;
				}
				if (x == 0) {
					y = 0;
				}

				// Rectangle rect = parent.getClientArea();
				Rectangle rect = parent.getClientArea();
				if (rect.width < 200) {
					rect = parent.getParent().getClientArea();
					OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width,
							rect.height, OS.SWP_DRAWFRAME);
				} else {
					OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width,
							rect.height, OS.SWP_DRAWFRAME);
				}

				// OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width <
				// minWidth ? minWidth : rect.width, rect.height,
				// OS.SWP_NOZORDER | OS.SWP_NOACTIVATE);
			}
		};

		parent.addListener(SWT.Resize, resizeListener);
	}

	String createSunScanRun(String method, String param0, String param1) {
		String response = "";
		logger.debug("method:" + method + ",paramers[0]:" + param0
				+ ", parameters[1]:" + param1);
		try {
			response = CreateSunScan(param0, param1);
		} catch (Exception e) {

			e.printStackTrace();
		}

		return response;
	}

	String showSunScanRun(String method, String param0, String param1) {
		String response = "";
		logger.debug("method:" + method + ",paramers[0]:" + param0
				+ ", parameters[1]:" + param1);
		try {
			response = ShowSunScan(param0, param1);
		} catch (Exception e) {

			e.printStackTrace();
		}

		return response;
	}

	String commOcxFuncRun(String method, String param0) {
		String response = "";
		logger.debug("method:" + method + ",paramers[0]:" + param0);
		try {
			response = CommOcxFunction(param0);
		} catch (Exception e) {

			e.printStackTrace();
		}

		return response;
	}

	/**
	 * 向OCX第二进程发起调用
	 * 
	 * @param method
	 * @param parameters
	 * @return
	 */
	public String asynRemoteInvoke(String method, String[] parameters) {
		// String request = CsvUtil.stringArrayToCsv(new String[] { method,
		// CsvUtil.stringArrayToCsv(parameters) });

		String defaultResponse = "sending";
		try {
			if (method.equals("CreateSunAddin")) {

				// 异步线程执行方式,会导致同步的请求变成异步的，由于线程的执行时间不一不能保证多个请求的执行顺序
				final String param0 = parameters[0];
				final String param1 = parameters[1];
				Thread showThread = new Thread(new Runnable() {
					@Override
					public void run() {

						try {
							singleInvokeLock.lock();

							String response = "";
							response = createSunScanRun("CreateSunAddin",
									param0, param1);

							logger.debug("method: CreateSunAddin"
									+ ",paramers[0]:" + param0
									+ ", parameters[1]:" + param1
									+ ",response:" + response);
						} finally {
							singleInvokeLock.unlock();
						}

					}
				});
				showThread.start();

			} else if (method.equals("CommOcxFunction")) {

				final String param0 = parameters[0];
				Thread showThread = new Thread(new Runnable() {
					@Override
					public void run() {

						try {
							singleInvokeLock.lock();

							String response = "";
							response = commOcxFuncRun("CommOcxFunction", param0);

							logger.debug("method: CommOcxFunction"
									+ ",paramers[0]:" + param0 + ",response:"
									+ response);

						} finally {
							singleInvokeLock.unlock();
						}
					}
				});
				showThread.start();
			} else if (method.equals("ShowSunAddin")) {

				final String param0 = parameters[0];
				final String param1 = parameters[1];
				Thread showThread = new Thread(new Runnable() {
					@Override
					public void run() {

						try {
							singleInvokeLock.lock();

							String response = "";
							response = showSunScanRun("ShowSunAddin", param0,
									param1);

							logger.debug("method: ShowSunAddin"
									+ ",paramers[0]:" + param0
									+ ", parameters[1]:" + param1
									+ ",response:" + response);
						} finally {
							singleInvokeLock.unlock();
						}

					}
				});
				showThread.start();

			}
		} catch (Exception e) {
			logger.error("消息处理异常：" + e.getMessage(), e);
			defaultResponse = "fail," + e.getMessage();
		}

		return defaultResponse;
	}

	// public String asynRemoteInvoke(String method, String[] parameters,
	// final String eventName, final InnerOLEListener listener) {
	// // String request = CsvUtil.stringArrayToCsv(new String[] { method,
	// // CsvUtil.stringArrayToCsv(parameters) });
	//
	// String defaultResponse = "sending";
	// try {
	// if (method.equals("CreateSunAddin")) {
	//
	// Task task = taskQueue.new Task();
	// task.methodName = "CreateSunAddin";
	// task.param = parameters;
	// task.eventName = "";
	// task.listener = listener;
	// task.response = "";
	// taskQueue.add(task);
	//
	// } else if (method.equals("CommOcxFunction")) {
	//
	// Task task = taskQueue.new Task();
	// task.methodName = "CommOcxFunction";
	// task.param = parameters;
	// task.eventName = "";
	// task.listener = listener;
	// task.response = "";
	// taskQueue.add(task);
	//
	// } else if (method.equals("ShowSunAddin")) {
	//
	// Task task = taskQueue.new Task();
	// task.methodName = "ShowSunAddin";
	// task.param = parameters;
	// task.eventName = "";
	// task.listener = listener;
	// task.response = "";
	// taskQueue.add(task);
	//
	// }
	// } catch (Exception e) {
	// logger.error("消息处理异常：" + e.getMessage(), e);
	// defaultResponse = "fail," + e.getMessage();
	// }
	//
	// return defaultResponse;
	// }

	// public String remoteInvoke(String method, String[] parameters,
	// final String eventName, InnerOLEListener listener) {
	// // String request = CsvUtil.stringArrayToCsv(new String[] { method,
	// // CsvUtil.stringArrayToCsv(parameters) });
	//
	// String response = "";
	// try {
	// if (method.equals("CreateSunAddin")) {
	// response = createSunScanRun("CreateSunAddin", parameters[0],
	// parameters[1]);
	//
	// logger.debug("method: CreateSunAddin" + ",paramers[0]:"
	// + parameters[0] + ", parameters[1]:" + parameters[1]
	// + ",response:" + response);
	//
	// } else if (method.equals("CommOcxFunction")) {
	//
	// response = commOcxFuncRun("CommOcxFunction", parameters[0]);
	//
	// logger.debug("method: CommOcxFunction" + ",paramers[0]:"
	// + parameters[0] + ",response:" + response);
	//
	// } else if (method.equals("ShowSunAddin")) {
	//
	// response = showSunScanRun("ShowSunAddin", parameters[0],
	// parameters[1]);
	//
	// logger.debug("method: ShowSunAddin" + ",paramers[0]:"
	// + parameters[0] + ", parameters[1]:" + parameters[1]
	// + ",response:" + response);
	// }
	//
	// if (eventName != null && !eventName.trim().equals("")) {
	//
	// if (listener != null) {
	// String events[] = { response };
	// listener.handleEvent(events);
	// }
	// }
	// } catch (Exception e) {
	// logger.error("消息处理异常：" + e.getMessage(), e);
	// response = "fail," + e.getMessage();
	// }
	//
	// return response;
	// }
	//
	// public int registerEventListener(String eventID, InnerOLEListener
	// listener) {
	// listenerMap.put(eventID, listener);
	//
	// return listenerMap.size();
	// }

	/**
	 * 将oleListener的值返回交易
	 * 
	 * @param ret
	 */
	public void eventListenerCallBack(final String ret) {
		logger.debug("开始调用eventListenerCallBack，事件:" + ret);
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				logger.debug("接收消息：" + ret);
				if (ret.indexOf("<transcode>cb0001</transcode>") != -1) {// 采集完一张
					logger.debug("transcode=cb0001");
				}
				// 获取订阅器实例
				ISubject subject = NotificationManager.getSubject();
				// 发布消息
				subject.notifyObserver("eventListenerCallBack", ret);
			}
		});
	}

	private String socketSunOcxServer(String xmlContent) throws Exception {
		if (xmlContent == null || xmlContent.length() == 0)
			throw new IllegalArgumentException("非法参数：" + xmlContent);

		if (sunScanPort == 0)
			throw new IllegalStateException("影像控件未启动成功!");

		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;

		try {
			socket = new Socket();
			InetSocketAddress socketAddress = new InetSocketAddress(
					"127.0.0.1", sunScanPort);
			logger.info(String.format("建立通讯地址：%s,端口：%s,连接超时时间：%s, 超时时间：%s",
					socketAddress.getAddress(), socketAddress.getPort(),
					connectTimeout, timeout));
			socket.connect(socketAddress, connectTimeout);
			socket.setSoTimeout(timeout);

			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			out.write(String.format("%08d",
					xmlContent.getBytes(ENCODING).length).getBytes(ENCODING));
			out.write(xmlContent.getBytes(ENCODING));
			out.flush();

			byte[] headBytes = new byte[8];
			in.read(headBytes);// 读取报文头
			int headLen = Integer.valueOf(new String(headBytes));// 将报文头字节数组转换成int

			byte[] contents = toByteArray(in);

			String retStr = new String(contents, ENCODING);

			logger.info(String.format("报文头长度：%s,实际长度：%s,内容:%s", headLen,
					retStr.getBytes(ENCODING).length, retStr));

			return retStr.trim();
		} catch (Exception ex) {
			logger.error("方法调用异常", ex);
			throw ex;
		} finally {
			if (in != null) {
				closeQuietly(in);
			}

			if (out != null) {
				closeQuietly(out);
			}

			if (socket != null) {
				closeQuietly(socket);
			}
		}
	}

	public String CreateSunScan(String p1, String p2) throws Exception {
		String xmlContent = String.format(CreateSunScan_XML, p1, p2);

		logger.info("CreateSunScan参数：" + xmlContent);
		// System.out.println("参数：" + xmlContent);
		String retStr = socketSunOcxServer(xmlContent);
		logger.debug("CreateSunScan创建控件结果：" + retStr);
		return retStr.contains("创建控件成功") ? "0" : "1";
	}

	public String CommOcxFunction(String p1) throws Exception {
		String xmlContent = "";
		File commXml = null;
		if (p1.contains("![CDATA[")) {
			commXml = File.createTempFile("commXml_", ".xml");
			writeStringToFile(commXml, p1, ENCODING);
			xmlContent = String.format(CommOcxFunction_XML_FILE,
					commXml.getCanonicalPath());
			logger.info(String.format("CommOcxFunction参数：p1=%s", p1));
		} else {
			xmlContent = String.format(CommOcxFunction_XML, p1);
		}

		logger.info("参数：" + xmlContent);
		System.out.println("参数：" + xmlContent);
		String retStr = "";
		try {
			retStr = socketSunOcxServer(xmlContent);
		} finally {
			if (commXml != null)
				commXml.delete();
		}
		logger.info("返回：" + retStr);
		System.out.println("返回：" + retStr);
		return retStr;
	}

	public String ShowSunScan(String tradeInfo, String treeInfo)
			throws Exception {
		String xmlContent = "";
		File tradeInfoXml = null, treeXml = null;
		// 从配置文件中读取影像影像平台ip和端口
		tradeInfo = String.format(tradeInfo, port, ip);

		if (tradeInfo.contains("![CDATA[") || treeInfo.contains("![CDATA[")) {
			tradeInfoXml = File.createTempFile("tradeInfoXml_", ".xml");
			writeStringToFile(tradeInfoXml, tradeInfo, ENCODING);
			treeXml = File.createTempFile("treeXml_", ".xml");
			writeStringToFile(treeXml, treeInfo);
			xmlContent = String
					.format(ShowSunScan_XML_FILE,
							tradeInfoXml.getCanonicalPath(),
							treeXml.getCanonicalPath());
			logger.info(String.format("参数：tradeInfo=%s,treeInfo=%s", tradeInfo,
					treeInfo));
		} else {
			xmlContent = String.format(ShowSunScan_XML, tradeInfo, treeInfo);
		}

		// logger.info("ShowSunScan报文：" + xmlContent);
		// System.out.println("tradeInfo"+tradeInfo);

		String retStr = "";
		try {
			retStr = socketSunOcxServer(xmlContent);
		} finally {
			if (tradeInfoXml != null)
				tradeInfoXml.delete();
			if (treeXml != null)
				treeXml.delete();
		}

		return retStr;
	}

	public String addEventListener() {
		logger.info("addEventListener 1028 "+deputyWindowHandle);
		OS.SendMessage(deputyWindowHandle, 1028, 0, 0);
//		logger.info("addEventListener 1029");
//		OS.SendMessage(deputyWindowHandle, 1029, 0, 0);
//		logger.info("addEventListener 1030");
//		OS.SendMessage(deputyWindowHandle, 1030, 0, 0);

		return "";
	}

	public String removeEventListener() {
//		OS.SendMessage(deputyWindowHandle, 1028, 0, 0);
		OS.SendMessage(deputyWindowHandle, 1029, 0, 0);
//	    OS.SendMessage(deputyWindowHandle, 1030, 0, 0);
		return "";
	}

	public void getIP() {
		String oldip = "";
		// oldip =
		// Platform.getPreferencesService().getString(Activator.PLUGIN_ID,
		// "ImageIP", "", null);
		String[] localips = getAllLocalHostIP();
		String localip = "";
		if (localips != null && localips.length > 0)
			localip = localips[0];
		String key = localip.split("\\.")[0];
		if (oldip.indexOf(";") != -1) {
			String[] ips = oldip.split(";");
			Map sunimageIpMap = new ConcurrentHashMap();
			for (int i = 0; i < ips.length; i++) {
				String str1 = ips[i];
				if (str1.indexOf(":") != -1) {
					sunimageIpMap.put(str1.split(":")[0], str1.split(":")[1]);
				} else {
					logger.error("preference.properties配置文件中,ImageIP配置格式有误");
				}
			}
			ip = String.valueOf(sunimageIpMap.get(key));
		} else {
			ip = oldip;
		}

		String oldport = "";
		// oldport =
		// Platform.getPreferencesService().getString(Activator.PLUGIN_ID,
		// "ImagePort", "", null);
		if (oldport.indexOf(";") != -1) {
			String[] ports = oldport.split(";");
			Map sunimagePortMap = new ConcurrentHashMap();
			for (int i = 0; i < ports.length; i++) {
				String str1 = ports[i];
				if (str1.indexOf(":") != -1) {
					sunimagePortMap.put(str1.split(":")[0], str1.split(":")[1]);
				} else {
					logger.error("preference.properties配置文件中,ImagePort配置格式有误");
				}
			}
			port = String.valueOf(sunimagePortMap.get(key));
		} else {
			port = oldport;
		}

	}

	private static String[] getAllLocalHostIP() {
		String[] ret = null;
		try {
			String hostName = getLocalHostName();
			if (hostName.length() > 0) {
				InetAddress[] addrs;
				addrs = InetAddress.getAllByName(hostName);
				if (addrs.length > 0) {
					ret = new String[addrs.length];
					for (int i = 0; i < addrs.length; i++) {
						String str = addrs[i].getHostAddress();
						if (str.matches("0.0.0.*")) {// 筛除影像控件第一次安装时的虚拟网卡产生的IP
							continue;
						}
						ret[i] = str;
					}
				}
			}
		} catch (UnknownHostException e) {
			logger.error(e.getMessage(), e);
			ret = null;
		}
		return ret;
	}

	/**
	 * 获取本机hostname
	 * 
	 * @return 返回hostname
	 */
	private static String getLocalHostName() {
		String hostName;
		InetAddress addr;
		try {
			addr = InetAddress.getLocalHost();
			hostName = addr.getHostName();
		} catch (UnknownHostException e) {
			hostName = "";
			logger.error(e.getMessage(), e);
		}
		return hostName;
	}

	public byte[] toByteArray(DataInputStream in) {
		byte[] buffer = new byte[1024];
		int len = -1;
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		try {
			while ((len = in.read(buffer)) != -1) {
				byteOut.write(buffer, 0, len);
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return byteOut.toByteArray();
	}

	public void closeQuietly(Object in) {
		try {
			if (in == null)
				return;
			if (in instanceof DataInputStream)
				((DataInputStream) in).close();
			if (in instanceof Socket)
				((Socket) in).close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static void writeStringToFile(File file, String content) {
		// System.out.println(file.getAbsolutePath());
		// System.out.println(content);
		writeStringToFile(file, content, "GBK");
	}

	public static void writeStringToFile(File file, String content,
			String encode) {
		OutputStream os = null;
		try {
			os = new BufferedOutputStream(new FileOutputStream(file, false));
			os.write(content.getBytes(encode));
			os.flush();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
	}
}
