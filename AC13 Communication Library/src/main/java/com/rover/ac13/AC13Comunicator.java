

package com.rover.ac13;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;

public class AC13Comunicator implements AutoCloseable {
	private static final String ROVER_HOST = "192.168.1.100";
	private static final int ROVER_PORT = 80;
	private static final String ROVER_USERNAME = "AC13";
	private static final String ROVER_PASSWORD = "AC13";

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static final String AUTH_HEADER_VALUE = "Basic " + Base64.getEncoder()
			.encodeToString((ROVER_USERNAME + ":" + ROVER_PASSWORD).getBytes(StandardCharsets.UTF_8));
	
	//region Declarations

	//Codes
	/*
	 * Right Foward = 5
	 * Left	Foward	= 7
	 * Foward = 5 and 7
	 * Right Backward = 6
	 * Left Backward = 8
	 * Backward = 6 and 8
	 * 
	 * High Resolution = http://192.168.1.100/set_params.cgi?resolution=32
	 * Low Resolution = High Resolution = http://192.168.1.100/set_params.cgi?resolution=8
	 */
	
	//Robot Parameters
	private class Parameters
	{
		public String id;
		public String sys_ver;
		public String app_ver;
		public String alias;
		public String adhoc_ssid;
		public String username;
		public String userpwd;
		public String resolution;
		public String ip;
		public String mask;
		public String gateway;
		public String port;
		public String wifi_ssid;
		public String wifi_encrypt;
		public String wifi_defkey;
		public String  wifi_key1;
		public String  wifi_key2;
		public String  wifi_key3;
		public String  wifi_key4;
		public String  wifi_authtype;
		public String  wifi_keyformat;
		public String  wifi_key1_bits;
		public String  wifi_key2_bits;
		public String  wifi_key3_bits;
		public String  wifi_key4_bits;
		public String  wifi_wpa_psk;
		
		
	}
	
	private Parameters PARAMETERS;
	
	//Thread Handling objects
	private Thread T;
	private Semaphore semaphore;
	
	// TCP/IP sockets
	private Socket cSock;
	private Socket vSock;

	// Flags to store state of the connection
	boolean streaming = false;
	boolean isConnected = false;
	
	// Infrared on/off
	boolean infrared;

	//Command Buffer
	byte[] cmdBuffer;

	// The maximal image buffer will be sufficient for hi-res images, notice
	// that JPEG images do not have a default image size. The TCP buffer is
	// large enough for the packages send by the Rover, but the latter tends
	// to chop images across multiple TCP chunks so it is not large enough
	// for one image.
	int maxTCPBuffer = 2048;
	int maxImageBuffer = 231072;
	byte[] imageBuffer = new byte[maxImageBuffer];
	int imagePtr = 0;
	int tcpPtr = 0;
	private int imageStartPosition;
	private int imageLength;


	// Debugging variables
	public int socketReadCount = 0;
	
	// endRegion Declarations

	//region Main Methods

	public AC13Comunicator() {
		
		PARAMETERS = new Parameters();
	}
	
	public AC13Comunicator(Semaphore semaphore ){
		
		this();
		this.semaphore = semaphore;
	}
	
	public boolean isConnected(){
		return this.isConnected;
	}
	
	public String getInfraredState()
	{
		return (infrared)?"On":"Off";	
	}
	
	public synchronized boolean Connect() {

		try {
			
			//Initializing command socket
			SocketAddress sockaddr = new InetSocketAddress(ROVER_HOST, ROVER_PORT);
			cSock = new Socket();
			cSock.connect(sockaddr,10000);
	
			
				
			//Setting the connection
			WriteStart();
			ReceiveAnswer(0);
			
			cSock.close();
			
			//Reinitializing the command socket
			cSock = new Socket();
			cSock.connect(sockaddr,10000);
			
			byte[] buffer = new byte[2048];
			
			for (int i = 1; i < 4; i++) {
				
				WriteCmd(i, null);
				buffer = ReceiveAnswer(i);
			}
			
			byte[] imgid = new byte[4];
			
			for (int i = 0; i < 4; i++) {
				imgid[i] = buffer[i + 25];
			}

			vSock = new Socket();
			vSock.connect(sockaddr,10000);
			WriteCmd(4, imgid);
			
			startStreaming();
			
			isConnected = true;
			
			
			
		} catch (Exception e) {
			Disconnect();
			 return false;	
		}
		
		return true;
	}

	public synchronized boolean Disconnect(){
		
		try {
			
		    streaming = false;
		    stopStreamingThread();
		    
		    if(infrared) {
		    	SwitchInfrared();
		    }

			closeQuietly(cSock);
			closeQuietly(vSock);
			cSock = null;
			vSock = null;
			
			isConnected = false;
			
		} catch (Exception e) {
			return false;
		}
		
		return true;	
	}

	@Override
	public void close() {
		Disconnect();
	}
	
	
	public void SwitchInfrared() {
		
		if (infrared) {
			WriteCmd(11,null);
		} else {
			WriteCmd(10,null);
		}

		infrared = !infrared;
	}

	private byte[] ReceiveAnswer(int i) {
		
		byte[] buffer = new byte[2048];
		try {
			
		 cSock.getInputStream().read(buffer, 0, 2048);
			
		} catch (Exception eg) {
			eg.printStackTrace();
		}
		return buffer;
	}
	
	private void WriteStart() {
		try {

			PrintWriter out = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(cSock.getOutputStream())), true);
			out.println("GET /check_user.cgi?user=AC13&pwd=AC13 HTTP/1.1\r\nHost: " + ROVER_HOST + ":" + ROVER_PORT + "\r\nUser-Agent: WifiCar/1.0 CFNetwork/485.12.7 Darwin/10.4.0\r\nAccept: */*\r\nAccept-Language: en-us\r\nAccept-Encoding: gzip, deflate\r\nConnection: keep-alive\r\n\r\n!");

		} catch (Exception e) {
		}
	}

	private List<String> executeGetLines(String path) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder()
				.uri(URI.create("http://" + ROVER_HOST + path))
				.header("Authorization", AUTH_HEADER_VALUE)
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return List.of(response.body().split("\\R"));
	}
	
	// endRegion Main Methods
	
	//region Image Methods
	
	private boolean startStreaming() {
		streaming = true;
		T = Thread.ofVirtual().name("rover-video-stream").start(() -> {
			if (semaphore == null) {
				while (streaming) {
					ReceiveImage();
				}
			} else {
				while (streaming) {
					ReceiveImageUsingSemaphore();
				}
			}
		});
		return true;
	}

	private void stopStreamingThread() {
		if (T == null) {
			return;
		}

		try {
			T.join(250);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void closeQuietly(Socket socket) {
		if (socket == null) {
			return;
		}

		try {
			socket.close();
		} catch (IOException _) {
		}
	}
	
	public void SwitchTo640X480Resolution() {
		Thread.ofVirtual().start(() -> sendResolutionCommand(32));
	}

	public void SwitchTo320X240Resolution() {
		Thread.ofVirtual().start(() -> sendResolutionCommand(8));
	}

	private void sendResolutionCommand(int command) {
		try {
			for (String line : executeGetLines("/set_params.cgi?resolution=" + command)) {
				if (!line.isEmpty()) {
					System.out.println("RESOLUTION COMMAND: " + line + " " + command);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.out.println("Resolution Command Error " + e);
		} catch (Exception e) {
			System.out.println("Resolution Command Error " + e);
		}
	}
		
	public boolean isStreaming(){
		
		return streaming;	
	}
	
	public byte[] GetImageBuffer(){

		return 	Arrays.copyOfRange(imageBuffer, GetImageStartPosition(), GetImageStartPosition() + GetImageLength());
	}
	
	public byte[] GetRawImageBuffer(){	
		
		return imageBuffer;	
	}
	
	public int  GetImageLength(){	
		
		return this.imageLength;
	}
	
	public int GetImageStartPosition(){
		
		return this.imageStartPosition;
	}
	
	private void SetImageStartPosition(int start){	
		this.imageStartPosition = start;	
	}
	
	private void SetImageLength(int len){	
		this.imageLength = len;	
	}
		
	private boolean ImgStart(byte[] start) {
		return (start[0] == 'M' && start[1] == 'O' && start[2] == '_' && start[3] == 'V');
	}
	
	private void ReceiveImage() {	
		try {
			int len;
			int newPtr = tcpPtr;
			int imageLength = 0;
			boolean fnew = false;

			while (!fnew && newPtr < maxImageBuffer - maxTCPBuffer) {
				len = vSock.getInputStream().read(imageBuffer, newPtr, maxTCPBuffer);
				if (len <= 0) {
					continue;
				}

				byte[] f4 = new byte[4];
				for (int i = 0; i < 4; i++) {
					f4[i] = imageBuffer[newPtr + i];
				}

				if (ImgStart(f4) && imageLength > 0) {
					fnew = true;
				}

				if (!fnew) {
					newPtr += len;
					imageLength = newPtr - imagePtr;
				} else {
					// strip 36-byte packet header
					SetImageStartPosition(imagePtr + 36);
					SetImageLength(imageLength - 36);

					if (newPtr > maxImageBuffer / 2) {
						// wrap: copy leading bytes of new image to start of buffer
						System.arraycopy(imageBuffer, newPtr, imageBuffer, 0, len);
						imagePtr = 0;
						tcpPtr = len;
					} else {
						imagePtr = newPtr;
						tcpPtr = newPtr + len;
					}
				}
			}

			// reset if buffer boundary exceeded
			if (newPtr >= maxImageBuffer - maxTCPBuffer) {
				imagePtr = 0;
				tcpPtr = 0;
			}
		} catch (Exception _) {
			// socket read error during streaming – silently continue
		}
	}
	
	private void ReceiveImageUsingSemaphore() {
		try {
			int len;
			int newPtr = tcpPtr;
			int imageLength = 0;
			boolean fnew = false;

			while (!fnew && newPtr < maxImageBuffer - maxTCPBuffer) {
				len = vSock.getInputStream().read(imageBuffer, newPtr, maxTCPBuffer);
				if (len <= 0) {
					continue;
				}

				byte[] f4 = new byte[4];
				for (int i = 0; i < 4; i++) {
					f4[i] = imageBuffer[newPtr + i];
				}

				if (ImgStart(f4) && imageLength > 0) {
					fnew = true;
				}

				if (!fnew) {
					newPtr += len;
					imageLength = newPtr - imagePtr;
				} else {
					SetImageStartPosition(imagePtr + 36);
					SetImageLength(imageLength - 36);
					semaphore.release();

					if (newPtr > maxImageBuffer / 2) {
						// wrap: copy leading bytes of new image to start of buffer
						System.arraycopy(imageBuffer, newPtr, imageBuffer, 0, len);
						imagePtr = 0;
						tcpPtr = len;
					} else {
						imagePtr = newPtr;
						tcpPtr = newPtr + len;
					}
				}
			}

			// reset if buffer boundary exceeded
			if (newPtr >= maxImageBuffer - maxTCPBuffer) {
				imagePtr = 0;
				tcpPtr = 0;
			}
		} catch (Exception _) {
			// socket read error during streaming – silently continue
		}
	}
	
	// endRegion Image Methods
	
	//region Movement Methods
	
	public void stopMovement() {
		Thread.ofVirtual().start(() -> {
			try {
				cmdBuffer[23] = 0x02;
				cmdBuffer[24] = 0;
				cSock.getOutputStream().write(cmdBuffer, 0, cmdBuffer.length);

				cmdBuffer[23] = 0x04;
				cmdBuffer[24] = 0;
				cSock.getOutputStream().write(cmdBuffer, 0, cmdBuffer.length);
			} catch (Exception _) {
				// socket closed or not connected
			}
		});
	}
	
	public void MoveFoward(){
		
		WriteCmd(5, null);
		WriteCmd(7, null);
	}
	
	public void MoveLeftFoward(){
		
		WriteCmd(7, null);	

	}
	
	public void MoveRightFoward(){
		WriteCmd(5, null);
	}
	
	public void MoveBackward(){
		
		WriteCmd(6, null);	
		WriteCmd(8, null);
	}
	
	public void MoveLeftBackward(){
	
		WriteCmd(8, null);
	}
	
	public void MoveRightBackward(){
		WriteCmd(6, null);	
	}
		
    public void RotateLeft(){
    	MoveLeftFoward();
    	MoveRightBackward();
	}
    
	public void RotateRight(){
		
		MoveRightFoward();
		MoveLeftBackward();

	}
	
	// endRegion Movements Methods
	
	//region Command Write	
	
	private void WriteCmd(int index, byte[] extra_input) {
		int len = switch (index) {
			case 1, 9       -> 23;
			case 3, 10, 11  -> 24;
			case 2          -> 49;
			case 4          -> 27;
			case 5, 6, 7, 8 -> 25; // wheel commands
			default         -> -1;
		};
		if (len == -1) {
			return;
		}

		byte[] buffer = new byte[len];
		for (int i = 4; i < len; i++) {
			buffer[i] = '\0';
		}
		buffer[0] = (byte) 'M';
		buffer[1] = (byte) 'O';
		buffer[2] = (byte) '_';
		buffer[3] = (index == 4) ? (byte) 'V' : (byte) 'O';

		switch (index) {
		case 1:
			break;
		case 2:
			buffer[4] = 0x02;
			buffer[15] = 0x1a;
			buffer[23] = 'A';
			buffer[24] = 'C';
			buffer[25] = '1';
			buffer[26] = '3';
			buffer[36] = 'A';
			buffer[37] = 'C';
			buffer[38] = '1';
			buffer[39] = '3';
			break;
		case 3:
			buffer[4] = 0x04;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = 0x02;
			break;
		case 4:
			buffer[15] = 0x04;
			buffer[19] = 0x04;
			for (int i = 0; i < 4; i++) {
				buffer[i + 23] = extra_input[i];
			}
			break;
		case 5: // left Wheel Foward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x04;
			buffer[24] = (byte) 0x0a;
			break;
		case 6: // Left Wheel Backward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x05;
			buffer[24] = (byte) 0x0a;
			break;
		case 7: // Right wheel Foward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x01;
			buffer[24] = (byte) 0x0a;
			break;
		case 8: // Right Wheel Backward
			buffer[4] = (byte) 0xfa;
			buffer[15] = 0x02;
			buffer[19] = 0x01;
			buffer[23] = 0x02;
			buffer[24] = (byte) 0x0a;
			break;
		case 9: // IR off(?)
			buffer[4] = (byte) 0xff;
			break;
		case 10:
			buffer[4] = (byte) 0x0e;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = (byte)0x5e;
			break;
		case 11:
			buffer[4] = (byte) 0x0e;
			buffer[15] = 0x01;
			buffer[19] = 0x01;
			buffer[23] = (byte)0x5f;
			break;

		}
		
		if (index != 4) {
			try {
				cSock.getOutputStream().write(buffer, 0, len);
				cmdBuffer = buffer;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		} 
		else {
			
			try {
				vSock.getOutputStream().write(buffer, 0, len);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	//endRegion Command Write
	
	//region Parameters
	
	public String getParameter_Id() {
		return PARAMETERS.id;
	}
	public String getParameterSys_ver() {
		return PARAMETERS.sys_ver;
	}
	public String getParameter_App_ver() {
		return PARAMETERS.app_ver;
	}
	public String getParameter_Alias() {
		return PARAMETERS.alias;
	}
	public String getParameter_Adhoc_ssid() {
		return PARAMETERS.adhoc_ssid;
	}
	public String getParameter_Username() {
		return PARAMETERS.username;
	}
	public String getParameter_Userpwd() {
		return PARAMETERS.userpwd;
	}
	public String getParameter_Resolution() {
		return PARAMETERS.resolution;
	}
	public String getParameter_Ip() {
		return PARAMETERS.ip;
	}
	public String getParameter_Mask() {
		return PARAMETERS.mask;
	}
	public String getParameter_Gateway() {
		return PARAMETERS.gateway;
	}
	public String getParameter_Port() {
		return PARAMETERS.port;
	}
	public String getParameter_Wifi_ssid() {
		return PARAMETERS.wifi_ssid;
	}
	public String getParameter_Wifi_encrypt() {
		return PARAMETERS.wifi_encrypt;
	}
	public String getParameter_Wifi_defkey() {
		return PARAMETERS.wifi_defkey;
	}
	public String geParameter_Wifi_key1() {
		return PARAMETERS.wifi_key1;
	}
	public String getParameter_Wifi_key2() {
		return PARAMETERS.wifi_key2;
	}
	public String getParameter_Wifi_key3() {
		return PARAMETERS.wifi_key3;
	}
	public String getParameter_Wifi_key4() {
		return PARAMETERS.wifi_key4;
	}
	public String getParameter_Wifi_authtype() {
		return PARAMETERS.wifi_authtype;
	}
	public String getParameter_Wifi_keyformat() {
		return PARAMETERS.wifi_keyformat;
	}
	public String getParameter_Wifi_key1_bits() {
		return PARAMETERS.wifi_key1_bits;
	}
	public String getParameter_Wifi_key2_bits() {
		return PARAMETERS.wifi_key2_bits;
	}
	public String getParameter_Wifi_key3_bits() {
		return PARAMETERS.wifi_key3_bits;
	}
	public String getParameter_Wifi_key4_bits() {
		return PARAMETERS.wifi_key4_bits;
	}
	public String getParameter_Wifi_wpa_psk() {
		return PARAMETERS.wifi_wpa_psk;
	}	
	
	public String getParameters_List() {
		return """
				id          = %s
				sys_ver     = %s
				app_ver     = %s
				alias       = %s
				adhoc_ssid  = %s
				username    = %s
				userpwd     = %s
				resolution  = %s
				ip          = %s
				mask        = %s
				gateway     = %s
				port        = %s
				wifi_ssid      = %s
				wifi_encrypt   = %s
				wifi_defkey    = %s
				wifi_key1      = %s
				wifi_key2      = %s
				wifi_key3      = %s
				wifi_key4      = %s
				wifi_authtype  = %s
				wifi_keyformat = %s
				wifi_key1_bits = %s
				wifi_key2_bits = %s
				wifi_key3_bits = %s
				wifi_key4_bits = %s
				wifi_wpa_psk   = %s
				""".formatted(
				PARAMETERS.id, PARAMETERS.sys_ver, PARAMETERS.app_ver,
				PARAMETERS.alias, PARAMETERS.adhoc_ssid, PARAMETERS.username,
				PARAMETERS.userpwd, PARAMETERS.resolution, PARAMETERS.ip,
				PARAMETERS.mask, PARAMETERS.gateway, PARAMETERS.port,
				PARAMETERS.wifi_ssid, PARAMETERS.wifi_encrypt, PARAMETERS.wifi_defkey,
				PARAMETERS.wifi_key1, PARAMETERS.wifi_key2, PARAMETERS.wifi_key3,
				PARAMETERS.wifi_key4, PARAMETERS.wifi_authtype, PARAMETERS.wifi_keyformat,
				PARAMETERS.wifi_key1_bits, PARAMETERS.wifi_key2_bits, PARAMETERS.wifi_key3_bits,
				PARAMETERS.wifi_key4_bits, PARAMETERS.wifi_wpa_psk);
	}
	
	private void fillParameters(ArrayList<String>parameters){	
		
		PARAMETERS.id = parameters.get(0);
		PARAMETERS.sys_ver = parameters.get(1);
		PARAMETERS.app_ver= parameters.get(2);
		PARAMETERS.alias= parameters.get(3);
		PARAMETERS.adhoc_ssid= parameters.get(4);
		PARAMETERS.username= parameters.get(5);
		PARAMETERS.userpwd= parameters.get(6);
		PARAMETERS.resolution= parameters.get(7);
		PARAMETERS.ip= parameters.get(8);
		PARAMETERS.mask= parameters.get(9);
		PARAMETERS.gateway= parameters.get(10);
		PARAMETERS.port= parameters.get(11);
		PARAMETERS.wifi_ssid= parameters.get(12);
		PARAMETERS.wifi_encrypt= parameters.get(13);
		PARAMETERS.wifi_defkey= parameters.get(14);
		PARAMETERS. wifi_key1= parameters.get(15);
		PARAMETERS. wifi_key2= parameters.get(16);
		PARAMETERS. wifi_key3= parameters.get(17);
		PARAMETERS. wifi_key4= parameters.get(18);
		PARAMETERS. wifi_authtype= parameters.get(19);
		PARAMETERS. wifi_keyformat= parameters.get(20);
		PARAMETERS. wifi_key1_bits= parameters.get(21);
		PARAMETERS. wifi_key2_bits= parameters.get(22);
		PARAMETERS. wifi_key3_bits= parameters.get(23);
		PARAMETERS. wifi_key4_bits= parameters.get(24);
		PARAMETERS. wifi_wpa_psk= parameters.get(25);
		
	}
	
	public void GetAllParameters() {
		Thread.ofVirtual().name("rover-get-params").start(() -> {
			var params = new ArrayList<String>();
			try {
				for (String line : executeGetLines("/get_params.cgi")) {
					if (line.contains("=") && line.contains(";")) {
						params.add(line.substring(line.indexOf("=") + 1, line.indexOf(";")).replace("'", ""));
					}
				}
				fillParameters(params);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.out.println("GET PARAMETERS ERROR: " + e);
			} catch (Exception e) {
				System.out.println("GET PARAMETERS ERROR: " + e);
			}
		});
	}
	
	//endRegion Parameters
	
}
