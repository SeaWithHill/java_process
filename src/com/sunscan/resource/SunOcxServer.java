package cn.com.agree.ab.custom.citic.client.widget;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.TCHAR;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.ole.win32.OLE;
import org.eclipse.swt.ole.win32.OleAutomation;
import org.eclipse.swt.ole.win32.OleControlSite;
import org.eclipse.swt.ole.win32.OleFrame;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;

import cn.com.agree.ab.custom.citic.client.FileUtil;
import cn.com.agree.ab.custom.citic.client.IOUtil;
import cn.com.agree.ab.exterior.controller.IController;
import cn.com.agree.commons.csv.CsvUtil;

/**
 * 第二进程
 * 
 * @author 
 */
@SuppressWarnings("restriction")
public class SunOcxServer extends Composite{

	public static final String CreateSunScan_XML = "<root><head><transcode>CREATETRANSCODE</transcode></head><body><appid>%s</appid><transcode>%s</transcode></body></root>";
	public static final String ShowSunScan_XML = "<root><head><transcode>SHOWTRANSCODE</transcode></head><body><param><![CDATA[%s]]></param><param><![CDATA[%s]]></param></body></root>";
	public static final String ShowSunScan_XML_FILE = "<root><head><transcode>SHOWTRANSCODE</transcode></head><body><param>%s</param><param>%s</param></body></root>";
	public static final String CommOcxFunction_XML = "<root><head><transcode>COMMOCXFUNCTION</transcode></head><body><param><![CDATA[%s]]></param></body></root>";
	public static final String CommOcxFunction_XML_FILE = "<root><head><transcode>COMMOCXFUNCTION</transcode></head><body><param>%s</param></body></root>";

	private static final Log logger = LogFactory.getLog(SunOcxServer.class);
	private static final String ENCODING = "GBK";
	private Composite parent;
	private IController controller;
	private int deputyWindowHandle = 0;
	private Process process;

	private int abcPort;
	private int sunScanPort;

	private ServerSocket server;

	private int minWidth = 660;

	private int minheight = 660;
	
	private boolean visible = true;

	private final AtomicBoolean isRunning = new AtomicBoolean(true);

	private final CountDownLatch ocxProcessCountDownLatch = new CountDownLatch(1);

	//	private CountDownLatch countDownLatch;

	public int connectTimeout = 5000;
	public int timeout = 60000;

	private final AtomicReference<String> errMsg = new AtomicReference<String>();

	private AtomicReference<String> resultMsg = new AtomicReference<String>();

	public boolean isCreated = false;

	private int[] childHandles = null;
	protected Socket sunServerSocket;

	public SunOcxServer(IController controller, Composite parent,final int style) {
		super(parent, style);
		this.controller = controller;
		this.parent = parent;
		//增加初始化界面  来帐交易直接显示影像 ，非初始化交易
		Map map = controller.getDomain().getImportVars();
		try {
			//创建控件
			logger.debug("CommOcxFunctionParams:"+(String)map.get("#CommOcxFunctionParams"));
			logger.debug("tradeInfo:"+(String)map.get("#tradeInfo"));
			logger.debug("Treeinfo:"+(String)map.get("#Treeinfo"));
		    if(map.get("#CommOcxFunctionParams")!=null&&(String)map.get("#tradeInfo")!=null&&(String)map.get("#Treeinfo")!=null){
		    	init("SUNSCAN.SunScanCtrl.2");
		    	CreateSunScan("ABCS", "ABCS");
		    	//设置影像控件参数
		    	String[] strArray=CsvUtil.csvToStringArray((String)map.get("#CommOcxFunctionParams"));
		    	for(int i=0;i<strArray.length;i++){
		    		CommOcxFunction(strArray[i]);
		    	}
		    //设置目录参数
		    	ShowSunScan((String)map.get("#tradeInfo"), (String)map.get("#Treeinfo"));
		    }
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
		isRunning.set(false);

		if (process != null) {
			process.destroy();
		}
	}

	public void init(String ocx_id) throws Exception {
		try {
			File configDir = new File(Platform.getConfigurationLocation().getURL().getFile());
			File driverDir = new File(configDir.getParent(), "driver");

			// #获取ABC第一进程端口
			// #建立ABC第一进程Socket服务
			server = new ServerSocket(0);
			server.setSoTimeout(10000);
			abcPort = server.getLocalPort();

			// #启动OCX第二进程
			String command = "\"" + driverDir + "\\SunOCXServer.exe\" " + abcPort;
			logger.debug("开始启动OCX第二进程，命令是：" + command);
			process = Runtime.getRuntime().exec(command);

			sunScanPort = getSunScanPort();			
			
			if (ocxProcessCountDownLatch.await(3, TimeUnit.MINUTES)) {
				if(sunScanPort == 0) {
					throw new IOException("启动OCX第二进程失败，申请端口失败");
				}
            
            registerCallbackListener();
				
				final String err = errMsg.get();
				if (err != null && err.length() != 0) {
					logger.error("启动OCX第二进程失败，" + err);
					parent.getDisplay().asyncExec(new Runnable() {
						public void run() {
							MessageBox messageBox = new MessageBox(parent.getShell());
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
						deputyWindowHandle = OS.FindWindow(new TCHAR(0, "#32770", true), new TCHAR(0, "YingXiang"
								+ (sunScanPort), true));
					}
					Thread.sleep(700);
					childHandles = new int[4];
					int i = 0;
					int child = OS.GetWindow(deputyWindowHandle, OS.GW_CHILD);
					while (child != 0) {
						childHandles[i++] = child;
						logger.debug("child:" + child);
						child = OS.GetWindow(child, OS.GW_HWNDNEXT);
					}
					logger.debug("父句柄是：" + Integer.toHexString(parent.handle) + ",独立进程窗口句柄是："
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
						MessageBox messageBox = new MessageBox(parent.getShell());
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
			byte[] contents = IOUtil.toByteArray(in);
			String retStr = new String(contents, ENCODING);
			final String[] responses = retStr.trim().split("&&&");

			logger.info(String.format("报文头长度：%s,实际长度：%s,内容:%s", headLen, retStr.getBytes(ENCODING).length, retStr));

			if (responses[0].equals("port")) {
				ocxProcessCountDownLatch.countDown();
				return Integer.valueOf(responses[1]);
			}
		} finally {
			IOUtil.closeQuietly(in);
			IOUtil.closeQuietly(socket);
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
						int headLen = Integer.parseInt(new String(retBytes, ENCODING));// 将报文头字节数组转换成int
						logger.info("收到OCX消息长度：" + headLen);

						int divisor = headLen / 4096; //报文有多少个4096字节
						int remainder = headLen % 4096; //报文除4096字节后剩余字节
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
						logger.info("收到OCX进程消息：" + req);

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
		int num = 1;
		while (p.getParent() != null) {
			num++;
			p = p.getParent();
		}
		logger.error("当前内嵌层数是：" + num);
	}

	private Composite embedParent = null;
	private int embedRet = 0;

	/**
	 * 内嵌
	 */
	public void embed() {
		embedParent = controller.getDomain().getContentPane();

		logger.debug("内嵌的父句柄是：" + Integer.toHexString(embedParent.handle));

		// 计算层数
		logEmbedLevel(embedParent);

		// 获取原窗口样式
		final int oldStyle = OS.GetWindowLong(deputyWindowHandle, OS.GWL_STYLE);
		// 内嵌

		if (OS.IsWindowVisible(embedParent.handle)) {
			embedRet = OS.SetParent(deputyWindowHandle, embedParent.handle);
		} else {
			parent.getDisplay().timerExec(500, new Runnable() {
				public void run() {
					embed();
				}
			});
			return;
		}

		if (embedRet == 0) {
			logger.debug("初次内嵌失败，子窗口句柄：" + Integer.toHexString(deputyWindowHandle) + ",父窗口句柄："
					+ Integer.toHexString(embedParent.handle));
			// 内嵌失败，恢复窗口边框和标题栏
			OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE, OS.WS_OVERLAPPED | OS.WS_POPUP | OS.WS_BORDER
					| OS.WS_VISIBLE | OS.WS_CAPTION);
		} else {
			logger.debug("初次内嵌成功，子窗口句柄：" + Integer.toHexString(deputyWindowHandle) + ",父窗口句柄："
					+ Integer.toHexString(embedParent.handle));
			// 内嵌成功，去除边框且显示窗口
			OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE, (oldStyle & ~OS.WS_BORDER) | OS.WS_VISIBLE);
			if (!visible) {
				logger.debug("隐藏控件");
				OS.ShowWindow(deputyWindowHandle, OS.SW_HIDE);
			}
		}

		// 重新定位程序
		int x = 0, y = 0,width=0,height=0;
		for (Composite p = parent; p != embedParent; p = p.getParent()) {
			Point r = p.getLocation();
			x += r.x;
			y += r.y;
		}
		if(x==0){
			y=0;
		}
		Rectangle rect = parent.getClientArea();
		width=rect.width;
		height=rect.height;
		if(rect.width==64&&rect.height==64){
			rect = parent.getParent().getClientArea();
			width=rect.width/2;
			height=rect.height;
		}
		System.out.println(width+"=="+height);
		OS.SetWindowPos(deputyWindowHandle, 0, x, y, width, height,
				OS.SWP_NOZORDER | OS.SWP_NOACTIVATE);

		// 防止第一次点击切换焦点
		OS.SetFocus(parent.handle);

		// resize时重新定位
		Listener resizeListener = new Listener() {
			public void handleEvent(Event event) {
				logger.debug("执行resize");

				if (embedRet == 0) {
					embedRet = OS.SetParent(deputyWindowHandle, parent.handle);
					logger.debug("重新内嵌窗口结果：" + (embedRet != 0));
					if (embedRet != 0) {
						// 内嵌成功，去除边框且显示窗口
						OS.SetWindowLong(deputyWindowHandle, OS.GWL_STYLE, (oldStyle & ~OS.WS_BORDER) | OS.WS_VISIBLE);
						if (!visible) {
							logger.debug("隐藏控件");
							OS.ShowWindow(deputyWindowHandle, OS.SW_HIDE);
						}
					}
				}

				int x = 0, y = 0,width=0,height=0;
				for (Composite p = parent; p !=embedParent; p = p.getParent()) {
					Point r = p.getLocation();
					x += r.x;
					y += r.y;
//					Rectangle rect = p.getClientArea();
//					System.out.println(r.x+"==r.y="+r.y);
				}
				Rectangle rect = parent.getClientArea();
//				Rectangle rect = parent.getParent().getClientArea();
				if(x==0){
					y=0;
				}
				width=rect.width;
				height=rect.height;
				if(rect.width==64&&rect.height==64){//自适应的拿不到控件的大小，取外框大小取一半
					Rectangle prect = parent.getParent().getClientArea();
					width=prect.width/2;
					height=prect.height;
				}
				System.out.println(x+"==xy="+y);
//				System.out.println(width+"==222222="+height);
//				System.out.println( parent.getParent().getClientArea().width+"==222="+ parent.getParent().getClientArea().height);
				OS.SetWindowPos(deputyWindowHandle, 0, x, y, width, height,
						OS.SWP_NOZORDER | OS.SWP_NOACTIVATE);

//				OS.SetWindowPos(deputyWindowHandle, 0, x, y, rect.width < minWidth ? minWidth : rect.width, rect.height,
//						OS.SWP_NOZORDER | OS.SWP_NOACTIVATE);
			}
		};

		parent.addListener(SWT.Resize, resizeListener);
		controller.getDomain().getContentPane().setData("SunScan2ProcessPanel", "T");
		controller.getDomain().getContentPane().addListener(SWT.Resize, resizeListener);
	}

	/**
	 * 向OCX第二进程发起调用
	 * 
	 * @param method
	 * @param parameters
	 * @return
	 */
	public String remoteInvoke(String method, String... parameters) {
		String request = CsvUtil.stringArrayToCsv(new String[] { method, CsvUtil.stringArrayToCsv(parameters) });

		logger.debug("request: " + request);

		String response = "";
		try {
			if (method.equals("CreateSunScan")) {
				response = CreateSunScan(parameters[0], parameters[1]);
			} else if (method.equals("commOcxFunction")) {
				response = CommOcxFunction(parameters[0]);
			} else if (method.equals("showSunScan")) {
				response = ShowSunScan(parameters[0], parameters[1]);
			}
		} catch (Exception e) {
			logger.error("消息处理异常：" + e.getMessage(), e);
			response = "fail," + e.getMessage();
		}

		logger.debug("resopnse: " + response);

		return response;
	}

	/**
	 * 将oleListener的值返回交易
	 * 
	 * @param ret
	 */
	public void eventListenerCallBack(final String ret) {
		logger.debug("开始调用eventListenerCallBack");
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				logger.debug("接收消息：" + ret);
				String event = ret;
				controller.getDomain().queueMessageTask("SunScan_callback", event);
				logger.debug("回调成功");
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
			InetSocketAddress socketAddress = new InetSocketAddress("127.0.0.1", sunScanPort);
			logger.info(String.format("建立通讯地址：%s,端口：%s,连接超时：%s, 超时：%s", socketAddress.getAddress(),
					socketAddress.getPort(), connectTimeout, timeout));
			socket.connect(socketAddress, connectTimeout);
			socket.setSoTimeout(timeout);

			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			out.write(String.format("%08d", xmlContent.getBytes(ENCODING).length).getBytes(ENCODING));
			out.write(xmlContent.getBytes(ENCODING));
			out.flush();

			byte[] headBytes = new byte[8];
			in.read(headBytes);// 读取报文头
			int headLen = Integer.valueOf(new String(headBytes));// 将报文头字节数组转换成int

			byte[] contents = IOUtil.toByteArray(in);

			String retStr = new String(contents, ENCODING);

			logger.info(String.format("报文头长度：%s,实际长度：%s,内容:%s", headLen, retStr.getBytes(ENCODING).length, retStr));

			return retStr.trim();
		} catch (Exception ex) {
			logger.error("方法调用异常", ex);
			throw ex;
		} finally {
			IOUtil.closeQuietly(in);
			IOUtil.closeQuietly(out);
			IOUtil.closeQuietly(socket);
		}
	}

	public String CreateSunScan(String p1, String p2) throws Exception {
		String xmlContent = String.format(CreateSunScan_XML, p1, p2);

		logger.info("参数：" + xmlContent);
		System.out.println("参数：" + xmlContent);
		String retStr = socketSunOcxServer(xmlContent);
		System.out.println("创建控件成功：" + retStr);
		return retStr.contains("创建控件成功") ? "0" : "1";
	}

	public String CommOcxFunction(String p1) throws Exception {
		String xmlContent = "";
		File commXml = null;
		if (p1.contains("![CDATA[")) {
			commXml = File.createTempFile("commXml_", ".xml");
			FileUtil.writeStringToFile(commXml, p1, ENCODING);
			xmlContent = String.format(CommOcxFunction_XML_FILE, commXml.getCanonicalPath());
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

	public String ShowSunScan(String tradeInfo, String treeInfo) throws Exception {
		String xmlContent = "";
		File tradeInfoXml = null, treeXml = null;
		if (tradeInfo.contains("![CDATA[") || treeInfo.contains("![CDATA[")) {
			tradeInfoXml = File.createTempFile("tradeInfoXml_", ".xml");
			FileUtil.writeStringToFile(tradeInfoXml, tradeInfo, ENCODING);
			treeXml = File.createTempFile("treeXml_", ".xml");
			FileUtil.writeStringToFile(treeXml, treeInfo);
			xmlContent = String.format(ShowSunScan_XML_FILE, tradeInfoXml.getCanonicalPath(), treeXml.getCanonicalPath());
			logger.info(String.format("参数：tradeInfo=%s,treeInfo=%s", tradeInfo, treeInfo));
		} else {
			xmlContent = String.format(ShowSunScan_XML, tradeInfo, treeInfo);
		}

		logger.info("参数：" + xmlContent);
		System.out.println("tradeInfo"+tradeInfo);

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
		OS.SendMessage(deputyWindowHandle, 1028, 0, 0);
		return "";
	}

	public String removeEventListener() {
		OS.SendMessage(deputyWindowHandle, 1029, 0, 0);
		return "";
	}
}
