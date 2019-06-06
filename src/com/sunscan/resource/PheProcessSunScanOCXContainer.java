package cn.com.bankit.phoenix.ui.swing.custom.gui;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.UIManager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.bankit.phoenix.ui.core.MObject;
import cn.com.bankit.phoenix.ui.core.PHE;
import cn.com.bankit.phoenix.ui.datatype.Variant;
import cn.com.bankit.phoenix.ui.event.Event;
import cn.com.bankit.phoenix.ui.listener.Listener;
import cn.com.bankit.phoenix.ui.swing.custom.base.IControl;
import cn.com.bankit.phoenix.ui.swing.custom.gui.PheProcessBrowser.MessageType;
import cn.com.bankit.phoenix.ui.swing.util.ByteUtil;
import cn.com.bankit.phoenix.ui.swing.util.ProcessBridge;
import cn.com.bankit.phoenix.ui.swing.util.SWTWidgetManager;
import cn.com.bankit.phoenix.ui.swing.util.SwtUIThread;
import cn.com.bankit.phoenix.ui.swing.util.VariantSerializeUtil;

/**
 * 进程山西农信SCAN OCX
 * 
 * @author 林立
 * 
 */
public class PheProcessSunScanOCXContainer extends PheComposite implements
		IControl {

	/**
	 * SerialVersionUID
	 */
	private static final long serialVersionUID = 8588551726994823416L;

	/**
	 * logger
	 */
	private static Logger logger = LoggerFactory
			.getLogger(PheProcessSunScanOCXContainer.class);

	/**
	 * mControl
	 */
	protected MObject mControl;

	/**
	 * 嵌入了OCX控件的AWT画布
	 */
	private Canvas canvas;

	/**
	 * 生成能够嵌入swing的SWT Shell
	 */
	private Shell shell;

	/**
	 * 字符编码
	 */
	private static String CHARSET_NAME = "UTF-8";

	/**
	 * 返回值篮子
	 */
	private Map<Byte, String> retBasket = new HashMap<Byte, String>();

	/**
	 * 存放监听器
	 */
	private Map<String, Listener> mListeners = new HashMap<String, Listener>();
	/**
	 * 同步锁
	 */
	private Lock msgLock = new ReentrantLock();

	/**
	 * 同步condition
	 */
	private Condition msgCondition = msgLock.newCondition();

	/**
	 * 种子
	 */
	private byte seed = 0;

	/**
	 * time
	 */
	private long timeout = 30000;

	/**
	 * process
	 */
	private ProcessBridge processBridge;

	/**
	 * 浏览器进程
	 */
	private Process process;

	/**
	 * 是否销毁
	 */
	private boolean isDisposed = false;

	/**
	 * 是否嵌入
	 */
	private boolean embed = false;

	/**
	 * 当前句柄
	 */
	private int hwnd = -1;

	/**
	 * 父亲容器句柄
	 */
	private int oldParentHandle = -1;

	/**
	 * 嵌入边距
	 */
	private int embedMargin = 3;

	/**
	 * 是否加载成功
	 */
	private boolean isLoadSuccess = false;

	/**
	 * SWT UI线程
	 */
	private SwtUIThread swtUIThread;

	/**
	 * program ID
	 */
	private String progId;

	/**
	 * 标题
	 */
	private String title;

	PheProcessSunScanOCX processSunScanOCX;

	/**
	 * 构造函数
	 */
	public PheProcessSunScanOCXContainer() {
		System.setProperty("sun.awt.xembedserver", "true");
		// 标志未销毁
		isDisposed = false;
		// 获取SWT组件管理器
		SWTWidgetManager swtWidgetManager = SWTWidgetManager.getInstance();
		// 注册
		swtWidgetManager.registSWTWidget(this);
		// 获取SWT UI线程
		swtUIThread = swtWidgetManager.getSwtUIThread();
		// 启动UI线程
		swtUIThread.start();
		// 等待UI线程启动完成
		swtUIThread.join();
	}

	/**
	 * 绑定模型UI
	 */
	public void bindMControl(MObject mControl) {
		this.mControl = mControl;
	}

	/**
	 * 获取绑定的模型UI
	 */
	public MObject mControl() {
		return this.mControl;
	}

	/**
	 * 设置OCX ID
	 * 
	 * @param progID
	 */
	public void setProgID(String progID) {
		this.progId = progID;
	}

	/**
	 * 取得OCX id
	 * 
	 * @param progID
	 * @return
	 */
	public String getProgID(String progID) {
		return this.progId;
	}

	/**
	 * 初始化
	 */
	public void load(final String progId) {
		// 记录program标志
		this.progId = progId;
		// 获取display
		final Display currentDisplay = swtUIThread.getDisplay();
		if (canvas == null) {
			// 嵌入了SWT的 AWT画布
			canvas = new Canvas();
			this.setLayout(new BorderLayout());
			add(canvas, BorderLayout.CENTER);
			this.doLayout();
			currentDisplay.syncExec(new Runnable() {
				/**
				 * run
				 */
				public void run() {
					// 生成能够嵌入swing的SWT Shell
					shell = SWT_AWT.new_Shell(currentDisplay, canvas);
					// 设置标题
					shell.setText("ocx");
					// 设置fill布局
					shell.setLayout(new FillLayout());

					embed = embed();
				}
			});
		}
	}

	/**
	 * 嵌入窗口
	 * 
	 * @param filePath
	 *            系统启动文件路径
	 * @param windowHanlder
	 *            窗口句柄
	 */
	public boolean embed() {
		org.eclipse.swt.widgets.Composite comp1 = new org.eclipse.swt.widgets.Composite(
				shell, SWT.NONE);
		comp1.setLayout(new FillLayout());

		org.eclipse.swt.widgets.Composite comp2 = new org.eclipse.swt.widgets.Composite(
				comp1, SWT.NONE);
		comp2.setLayout(new FillLayout());
		processSunScanOCX = new PheProcessSunScanOCX(comp2, SWT.NONE, progId);
		// 加入监听器
		canvas.addComponentListener(new ComponentAdapter() {
			/**
			 * resize
			 */
			public void componentResized(ComponentEvent e) {
				// 获取display
				Display currentDisplay = swtUIThread.getDisplay();
				// 如果display已经销毁，不作处理
				if (currentDisplay == null || currentDisplay.isDisposed()) {
					return;
				}
				currentDisplay.syncExec(new Runnable() {
					/**
					 * run
					 */
					public void run() {
						Dimension size = canvas.getSize();
						shell.setBounds(0, 0, size.width, size.height);
					}
				});
			}
		});
		// 设置shell大小
		Dimension size = canvas.getSize();
		shell.setBounds(0, 0, size.width, size.height);
		shell.layout();

		return true;
	}

	/**
	 * Invokes a method on the OLE Object; the method has no parameters.
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @return the result of the method or null if the method failed to give
	 *         result information
	 */
	public Variant invoke(int dispIdMember) {
		return this.invoke(dispIdMember, null, null);
	}

	/**
	 * Invokes a method on the OLE Object; the method has no optional
	 * parameters.
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @return the result of the method or null if the method failed to give
	 *         result information
	 */
	public Variant invoke(final int dispIdMember, final Variant[] rgvarg) {
		return this.invoke(dispIdMember, rgvarg, null);
	}

	/**
	 * Invokes a method on the OLE Object; the method has optional parameters.
	 * It is not necessary to specify all the optional parameters, only include
	 * the parameters for which you are providing values.
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @param rgdispidNamedArgs
	 *            an array of identifiers for the arguments specified in rgvarg;
	 *            the parameter IDs must be in the same order as their
	 *            corresponding values; all arguments must have an identifier -
	 *            identifiers can be obtained using OleAutomation.getIDsOfNames
	 * 
	 * @return the result of the method or null if the method failed to give
	 *         result information
	 */
	public Variant invoke(int dispIdMember, Variant[] rgvarg,
			int[] rgdispidNamedArgs) {
		try {
			if (!this.embed) {
				return null;
			}
			logger.debug("invoke["+dispIdMember+"]");
			String ret = null;
			if (dispIdMember == 1) {
				ret = processSunScanOCX.CreateSunScan(rgvarg[0].getString(),
						rgvarg[1].getString());
			} else if (dispIdMember == 2) {
				ret = processSunScanOCX.CommOcxFunction(rgvarg[0].getString());
			} else if (dispIdMember == 3) {
				ret = processSunScanOCX.ShowSunScan(rgvarg[0].getString(),
						rgvarg[1].getString());
			}
			Variant variant = new Variant(ret);
			Display currentDisplay = swtUIThread.getDisplay();
			// 如果display已经销毁，不作处理
			if (currentDisplay == null || currentDisplay.isDisposed()) {
				return variant;
			}
			currentDisplay.asyncExec(new Runnable() {
				public void run() {
					Dimension size = canvas.getSize();
					shell.setBounds(0, 0, size.width, size.height);
					shell.layout();
				}
			});
			return variant;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}

	}

	/**
	 * Invokes a method on the OLE Object; the method has no parameters. In the
	 * early days of OLE, the IDispatch interface was not well defined and some
	 * applications (mainly Word) did not support a return value. For these
	 * applications, call this method instead of calling
	 * <code>public void invoke(int dispIdMember)</code>.
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @exception org.eclipse.swt.SWTException
	 *                <ul>
	 *                <li>ERROR_ACTION_NOT_PERFORMED when method invocation
	 *                fails
	 *                </ul>
	 */
	public void invokeNoReply(final int dispIdMember) {
		this.invokeNoReply(dispIdMember, null, null);
	}

	/**
	 * Invokes a method on the OLE Object; the method has no optional
	 * parameters. In the early days of OLE, the IDispatch interface was not
	 * well defined and some applications (mainly Word) did not support a return
	 * value. For these applications, call this method instead of calling
	 * <code>public void invoke(int dispIdMember, Variant[] rgvarg)</code>.
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @exception org.eclipse.swt.SWTException
	 *                <ul>
	 *                <li>ERROR_ACTION_NOT_PERFORMED when method invocation
	 *                fails
	 *                </ul>
	 */
	public void invokeNoReply(final int dispIdMember, final Variant[] rgvarg) {
		this.invokeNoReply(dispIdMember, rgvarg, null);
	}

	/**
	 * Invokes a method on the OLE Object; the method has optional parameters.
	 * It is not necessary to specify all the optional parameters, only include
	 * the parameters for which you are providing values. In the early days of
	 * OLE, the IDispatch interface was not well defined and some applications
	 * (mainly Word) did not support a return value. For these applications,
	 * call this method instead of calling
	 * <code>public void invoke(int dispIdMember, Variant[] rgvarg, int[] rgdispidNamedArgs)</code>
	 * .
	 * 
	 * @param dispIdMember
	 *            the ID of the method as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @param rgdispidNamedArgs
	 *            an array of identifiers for the arguments specified in rgvarg;
	 *            the parameter IDs must be in the same order as their
	 *            corresponding values; all arguments must have an identifier -
	 *            identifiers can be obtained using OleAutomation.getIDsOfNames
	 * 
	 * @exception org.eclipse.swt.SWTException
	 *                <ul>
	 *                <li>ERROR_ACTION_NOT_PERFORMED when method invocation
	 *                fails
	 *                </ul>
	 */
	public void invokeNoReply(final int dispIdMember, final Variant[] rgvarg,
			final int[] rgdispidNamedArgs) {
		try {
			if (!this.embed) {
				return;
			}
			// // -1代表不需要返回值
			// byte invokeId = -1;
			//
			// ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			// byteOut.write(MessageType.INVOKE);
			// byteOut.write(invokeId);
			// // 写入调用方法名称
			// byte[] bytes = "invokeNoReply".getBytes(CHARSET_NAME);
			// byte[] lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// // 写入参数dispIdMember
			// byteOut.write(ByteUtil.int2byteArray(dispIdMember));
			// // 写入参数rgvarg
			// if (rgvarg != null) {
			// byteOut.write(ByteUtil.int2byteArray(rgvarg.length));
			// for (int i = 0; i < rgvarg.length; i++) {
			// String data = VariantSerializeUtil.encode(rgvarg[i]);
			// bytes = data.getBytes(CHARSET_NAME);
			// lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// }
			// } else {
			// byteOut.write(ByteUtil.int2byteArray(0));
			// }
			// // 写入参数rgdispidNamedArgs
			// if (rgdispidNamedArgs != null) {
			// byteOut.write(ByteUtil.int2byteArray(rgdispidNamedArgs.length));
			// for (int i = 0; i < rgdispidNamedArgs.length; i++) {
			// byteOut.write(ByteUtil.int2byteArray(rgdispidNamedArgs[i]));
			// }
			// } else {
			// byteOut.write(ByteUtil.int2byteArray(0));
			// }
			//
			// byte[] buffer = byteOut.toByteArray();
			// // 发送消息
			// this.sendMessage(invokeId, buffer, this.timeout);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Returns the value of the property specified by the dispIdMember.
	 * 
	 * @param dispIdMember
	 *            the ID of the property as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @return the value of the property specified by the dispIdMember or null
	 */
	public Variant getProperty(final int dispIdMember) {
		return this.getProperty(dispIdMember, null, null);
	}

	/**
	 * Returns the value of the property specified by the dispIdMember.
	 * 
	 * @param dispIdMember
	 *            the ID of the property as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @return the value of the property specified by the dispIdMember or null
	 */
	public Variant getProperty(final int dispIdMember, final Variant[] rgvarg) {
		return this.getProperty(dispIdMember, rgvarg, null);
	}

	/**
	 * Returns the value of the property specified by the dispIdMember.
	 * 
	 * @param dispIdMember
	 *            the ID of the property as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * 
	 * @param rgvarg
	 *            an array of arguments for the method. All arguments are
	 *            considered to be read only unless the Variant is a By
	 *            Reference Variant type.
	 * 
	 * @param rgdispidNamedArgs
	 *            an array of identifiers for the arguments specified in rgvarg;
	 *            the parameter IDs must be in the same order as their
	 *            corresponding values; all arguments must have an identifier -
	 *            identifiers can be obtained using OleAutomation.getIDsOfNames
	 * */
	public Variant getProperty(final int dispIdMember, final Variant[] rgvarg,
			final int[] rgdispidNamedArgs) {
		try {
			if (!this.embed) {
				return null;
			}

			// byte invokeId = this.generateId();
			//
			// ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			// byteOut.write(MessageType.INVOKE);
			// byteOut.write(invokeId);
			// // 写入调用方法名称
			// byte[] bytes = "getProperty".getBytes(CHARSET_NAME);
			// byte[] lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// // 写入参数dispIdMember
			// byteOut.write(ByteUtil.int2byteArray(dispIdMember));
			// // 写入参数rgvarg
			// if (rgvarg != null) {
			// byteOut.write(ByteUtil.int2byteArray(rgvarg.length));
			// for (int i = 0; i < rgvarg.length; i++) {
			// String data = VariantSerializeUtil.encode(rgvarg[i]);
			// bytes = data.getBytes(CHARSET_NAME);
			// lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// }
			// } else {
			// byteOut.write(ByteUtil.int2byteArray(0));
			// }
			// // 写入参数rgdispidNamedArgs
			// if (rgdispidNamedArgs != null) {
			// byteOut.write(ByteUtil.int2byteArray(rgdispidNamedArgs.length));
			// for (int i = 0; i < rgdispidNamedArgs.length; i++) {
			// byteOut.write(ByteUtil.int2byteArray(rgdispidNamedArgs[i]));
			// }
			// } else {
			// byteOut.write(ByteUtil.int2byteArray(0));
			// }
			//
			// byte[] buffer = byteOut.toByteArray();
			// // 发送消息
			// String ret = this.sendMessage(invokeId, buffer, this.timeout);
			//
			// Variant variant = VariantSerializeUtil.decode(ret);
			return null;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Sets the property specified by the dispIdMember to a new value.
	 * 
	 * @param dispIdMember
	 *            the ID of the property as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * @param rgvarg
	 *            the new value of the property
	 * 
	 * @return true if the operation was successful
	 */
	public boolean setProperty(final int dispIdMember, final Variant rgvarg) {
		boolean res = this.setProperty(dispIdMember, new Variant[] { rgvarg });
		return res;
	}

	/**
	 * Sets the property specified by the dispIdMember to a new value.
	 * 
	 * @param dispIdMember
	 *            the ID of the property as specified by the IDL of the ActiveX
	 *            Control; the value for the ID can be obtained using
	 *            OleAutomation.getIDsOfNames
	 * @param rgvarg
	 *            the new value of the property
	 * 
	 * @return true if the operation was successful
	 */
	public boolean setProperty(final int dispIdMember, final Variant[] rgvarg) {
		try {
			if (!this.embed) {
				return false;
			}

			// byte invokeId = this.generateId();
			//
			// ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			// byteOut.write(MessageType.INVOKE);
			// byteOut.write(invokeId);
			// // 写入调用方法名称
			// byte[] bytes = "setProperty".getBytes(CHARSET_NAME);
			// byte[] lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// // 写入参数dispIdMember
			// byteOut.write(ByteUtil.int2byteArray(dispIdMember));
			// // 写入参数rgvarg
			// if (rgvarg != null) {
			// byteOut.write(ByteUtil.int2byteArray(rgvarg.length));
			// for (int i = 0; i < rgvarg.length; i++) {
			// String data = VariantSerializeUtil.encode(rgvarg[i]);
			// bytes = data.getBytes(CHARSET_NAME);
			// lenBytes = ByteUtil.int2byteArray(bytes.length);
			// byteOut.write(lenBytes);
			// byteOut.write(bytes);
			// }
			// } else {
			// byteOut.write(ByteUtil.int2byteArray(0));
			// }
			//
			// byte[] buffer = byteOut.toByteArray();
			// // 发送消息
			// String ret = this.sendMessage(invokeId, buffer, this.timeout);
			return true;
			// return "true".equalsIgnoreCase(ret);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Returns the positive integer values (IDs) that are associated with the
	 * specified names by the IDispatch implementor. If you are trying to get
	 * the names of the parameters in a method, the first String in the names
	 * array must be the name of the method followed by the names of the
	 * parameters.
	 * 
	 * @param names
	 *            an array of names for which you require the identifiers
	 * 
	 * @return positive integer values that are associated with the specified
	 *         names in the same order as the names where provided; or null if
	 *         the names are unknown
	 */
	public int[] getIDsOfNames(final String[] methodName) {
		try {
			if (!this.embed) {
				return null;
			}
			int[] methodIds = new int[methodName.length];
			for (int i = 0; i < methodName.length; i++) {
				logger.debug("getIDsOfNames["+methodName[i]+"]");
				if ("CreateSunScan".equals(methodName[i])) {
					methodIds[i] = 1;
				} else if ("CommOcxFunction".equals(methodName[i])) {
					methodIds[i] = 2;
				} else if ("ShowSunScan".equals(methodName[i])) {
					methodIds[i] = 3;
				} else {
					methodIds[i] = -1;
				}
			}
			return methodIds;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * 判断是否可见
	 */
	public boolean isOcxVisible() {
		return super.isVisible();
	}

	/**
	 * 设置是否可见
	 */
	public void setOcxVisible(boolean visible) {
		super.setVisible(visible);
		if (!this.embed || this.isDisposed) {
			return;
		}

		if (!visible) {
			final Boolean[] disposed = new Boolean[1];
			// 获取display
			final Display currentDisplay = swtUIThread.getDisplay();
			// 去掉窗口
			currentDisplay.syncExec(new Runnable() {

				/**
				 * run
				 */
				public void run() {
					disposed[0] = shell.isDisposed();
					if (!disposed[0]) {
						// 解除parent关系，不然会有问题
						OS.SetParent(hwnd, oldParentHandle);
					}
				}
			});

			if (!disposed[0]) {
				try {
					for (int i = 0; i < 2; i++) {
						byte invokeId = this.generateId();
						ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
						byteOut.write(MessageType.INVOKE);
						byteOut.write(invokeId);
						// 写入调用方法名称
						byte[] bytes = "hide".getBytes(CHARSET_NAME);
						byte[] lenBytes = ByteUtil.int2byteArray(bytes.length);
						byteOut.write(lenBytes);
						byteOut.write(bytes);
						byte[] buffer = byteOut.toByteArray();
						// 发送消息
						String res = this.sendMessage(invokeId, buffer,
								this.timeout);
						if ("true".equals(res)) {
							break;
						}

						// 等待200毫秒，再尝试
						Thread.sleep(200);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		} else {
			try {
				for (int i = 0; i < 2; i++) {
					byte invokeId = this.generateId();
					ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
					byteOut.write(MessageType.INVOKE);
					byteOut.write(invokeId);
					// 写入调用方法名称
					byte[] bytes = "show".getBytes(CHARSET_NAME);
					byte[] lenBytes = ByteUtil.int2byteArray(bytes.length);
					byteOut.write(lenBytes);
					byteOut.write(bytes);
					byte[] buffer = byteOut.toByteArray();
					// 发送消息
					String res = this.sendMessage(invokeId, buffer,
							this.timeout);
					if ("true".equals(res)) {
						break;
					}

					// 等待200毫秒，再尝试
					Thread.sleep(200);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			// 嵌入了SWT的 AWT画布
			if (canvas == null || !canvas.isDisplayable()) {
				canvas = new Canvas();
				this.setLayout(new BorderLayout());
				add(canvas, BorderLayout.CENTER);
				this.doLayout();
			}

			// 获取display
			final Display currentDisplay = swtUIThread.getDisplay();
			currentDisplay.syncExec(new Runnable() {
				/**
				 * run
				 */
				public void run() {
					if (shell.isDisposed()) {
						// 生成能够嵌入swing的SWT Shell
						shell = SWT_AWT.new_Shell(currentDisplay, canvas);
						// 设置标题
						shell.setText("ocx");
						// 设置fill布局
						shell.setLayout(new FillLayout());
						// 创建嵌入了OCX控件的swt复合容器
					}
				}

			});
		}
	}

	/**
	 * dispose
	 */
	public void dispose() {
		// 获取SWT组件管理器
		SWTWidgetManager swtWidgetManager = SWTWidgetManager.getInstance();
		// 注销
		swtWidgetManager.unRegistSWTWidget(this);
		// 退出进程
		this.exitProcess();
		// 获取display
		final Display currentDisplay = swtUIThread.getDisplay();
		// 去掉窗口
		currentDisplay.syncExec(new Runnable() {

			/**
			 * run
			 */
			public void run() {
				// 解除parent关系，不然会有问题
				OS.SetParent(hwnd, oldParentHandle);

				// 销毁shell
				if (shell != null && shell.isDisposed()) {
					shell.dispose();
				}
			}
		});
		// 标志已经销毁
		isDisposed = true;
		// 关闭SWT UI 线程
		this.swtUIThread.stop();

	}

	/**
	 * 生产调用ID
	 * 
	 * @return
	 */
	private byte generateId() {
		msgLock.lock();
		try {
			seed++;
			if (seed < 0) {
				seed = 0;
			}
			return seed;
		} finally {
			msgLock.unlock();
		}
	}

	/**
	 * 发送消息
	 * 
	 * @param invokeId
	 * @param buffer
	 * @param timeout
	 * @return
	 */
	private String sendMessage(byte invokeId, byte[] buffer, long timeout)
			throws Exception {

		if (invokeId > 0) {
			// 注册等待结果ID
			retBasket.put(invokeId, null);
		}
		// 写数据
		processBridge.write(buffer);

		// 返回结果
		String ret = null;
		if (invokeId > 0) {
			msgLock.lock();
			try {
				// 计算等待时间
				int sum = 0;
				do {
					// 试图获取返回结果
					ret = retBasket.get(invokeId);
					// 如果没返回结果，继续等待
					if (ret == null) {
						msgCondition.await(200, TimeUnit.MILLISECONDS);
						sum += 200;
					}
				} while (ret == null && sum < timeout);
			} finally {
				// 移除ID
				retBasket.remove(invokeId);
				// 解锁
				msgLock.unlock();
			}
		}
		return ret;

	}

	/**
	 * 处理消息
	 * 
	 * @param bytes
	 */
	private void handleMessage(byte[] bytes) throws Exception {
		if (logger.isDebugEnabled()) {
			try {
				logger.debug("接收消息：" + new String(bytes, CHARSET_NAME));
			} catch (UnsupportedEncodingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		byte ops = bytes[0];
		if (ops == MessageType.ANSWER) {
			// 判断是否需要返回值
			byte invokeId = bytes[1];
			int offset = 2;
			// 获取内容长度
			int len = ByteUtil.byteArray2Int(bytes, offset);
			offset += 4;
			// 获取内容
			String content = new String(bytes, offset, len, CHARSET_NAME);
			msgLock.lock();
			try {
				if (this.retBasket.containsKey(invokeId)) {
					// 放置结果
					this.retBasket.put(invokeId, content);
					// 唤醒等待
					msgCondition.signalAll();
				}
			} finally {
				msgLock.unlock();
			}
		} else if (ops == MessageType.NOTIFY) {
			int offset = 1;
			// 定义长度
			int len = ByteUtil.byteArray2Int(bytes, offset);
			offset += 4;
			// 方法名
			String name = new String(bytes, offset, len, CHARSET_NAME);

			offset += len;
			// 参数列表
			String[] params = null;
			if (offset < bytes.length) {
				List<String> paramList = new ArrayList<String>();
				do {
					len = ByteUtil.byteArray2Int(bytes, offset);
					offset += 4;
					String arg = new String(bytes, offset, len, CHARSET_NAME);
					paramList.add(arg);
					offset += len;
				} while (offset < bytes.length);
				// 导出参数列表
				params = paramList.toArray(new String[0]);
			}
			// 监听器触发事件
			if (mListeners.containsKey(name)) {
				Listener mListener = mListeners.get(name);
				Event event = new Event();
				event.source = mControl;
				event.type = PHE.EVENTS.Action;
				event.data = params[0].split("\\|");
				mListener.handleEvent(event);
			}

			// 处理状态
			if ("status".equals(name)) {
				if ("loadSuccess".equals(params[0])) {
					this.isLoadSuccess = true;
				}
			}
		}
	}

	/**
	 * 退出进程
	 */
	private boolean exitProcess() {
		if (!this.embed) {
			return false;
		}

		boolean exitValue = false;
		if (processBridge != null) {
			// 写数据
			try {
				byte id = this.generateId();
				byte[] buffer = new byte[] { MessageType.EXIT, id };
				String res = this.sendMessage(id, buffer, 1000);
				exitValue = "true".equals(res);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		return exitValue;
	}
}
