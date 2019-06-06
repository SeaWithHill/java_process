package com.process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProcessBuilderTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@Test
	void test() {
		try {
			Process p = new ProcessBuilder("command","param").start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/** 获取Windows系统下的网卡的MAC地址 */
	public static List<String> getPhysicalAddress() {
		Process p = null;
		List<String> address = new ArrayList<String>(); // 物理网卡列表
		try {
			p = new ProcessBuilder("ipconfig", "/all").start(); // 执行ipconfig/all命令
		} catch (IOException e) {
			return address;
		}
		byte[] b = new byte[1024];
		int readbytes = -1;
		StringBuffer sb = new StringBuffer();
		// 读取进程输出值
		// 在JAVA IO中,输入输出是针对JVM而言,读写是针对外部数据源而言
		InputStream in = p.getInputStream();
		try {
			while ((readbytes = in.read(b)) != -1) {
				sb.append(new String(b, 0, readbytes, Charset.forName("utf-8")));
			}
		} catch (IOException e1) {
		} finally {
			try {
				in.close();
			} catch (IOException e2) {
			}
		}
		// 以下是分析输出值,得到物理网卡
		String rtValue = sb.toString();
		int i = rtValue.indexOf("Physical Address. . . . . . . . . :");
		while (i > 0) {
			rtValue = rtValue.substring(i + "Physical Address. . . . . . . . . :".length());
			address.add(rtValue.substring(1, 18));
			i = rtValue.indexOf("Physical Address. . . . . . . . . :");
		}
		return address;
	}

	/** 执行自定义的一个命令,该命令放在C:/temp下,并且需要两个环境变量的支持 */
	public static boolean executeMyCommand1() {
		// 创建系统进程创建器
		ProcessBuilder pb = new ProcessBuilder("myCommand", "myArg1", "myArg2");
		Map<String, String> env = pb.environment(); // 获得进程的环境
		// 设置和去除环境变量
		env.put("VAR1", "myValue");
		env.remove("VAR0");
		env.put("VAR2", env.get("VAR1") + ";");
		// 迭代环境变量,获取属性名和属性值
		Iterator it = env.keySet().iterator();
		String sysatt = null;
		while (it.hasNext()) {
			sysatt = (String) it.next();
			System.out.println("System Attribute:" + sysatt + "=" + env.get(sysatt));
		}
		pb.directory(new File("C:/temp"));
		try {
			Process p = pb.start(); // 得到进程实例
			// 等待进程执行完毕
			if (p.waitFor() != 0) {
				// 如果进程运行结果不为0,表示进程是错误退出的
				// 获得进程实例的错误输出
				InputStream error = p.getErrorStream();
				// do something
			}
			InputStream sdin = p.getInputStream(); // 获得进程实例的标准输出
			// do something
		} catch (IOException e) {
		} catch (InterruptedException e) {
		}
		return true;
	}

	public static void executeMyCommand2() {
		ProcessBuilder pb = null;
		String sysatt = null;
		try {
			// 创建一个进程示例
			pb = new ProcessBuilder("cmd.exe");
			// 获取系统参数并打印显示
			Map<String, String> env = pb.environment();
			Iterator it = env.keySet().iterator();
			while (it.hasNext()) {
				sysatt = (String) it.next();
				System.out.println("System Attribute:" + sysatt + "=" + env.get(sysatt));
			}
			// 设置工作目录
			pb.directory(new File("d://myDir"));
			Process p = pb.start();
			// 将要执行的Windows命令写入
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
			// '/r/n'是必须写入的
			bw.write("test.bat /r/n");
			bw.write("ping -t www.yahoo.com.cn /r/n");
			// flush()方法是必须调用的
			bw.flush();
			// 将执行结果打印显示
			InputStream is = p.getInputStream();
			InputStreamReader isr = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(isr);
			String line;
			while ((line = br.readLine()) != null) {
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		List<String> address = ProcessBuilderTest.getPhysicalAddress();
		for (String add : address) {
			System.out.printf("物理网卡地址: %s%n", add);
		}
		executeMyCommand1();
           executeMyCommand2();
	}

}
