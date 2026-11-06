
package com.rover.ac13.app;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;

import com.rover.ac13.AC13Comunicator;
import com.rover.ac13.Frame;

import java.awt.Color;



@SuppressWarnings("serial")
public class Controller extends JFrame {

	private JPanel contentPane;
	private AC13Comunicator communicator;
	Thread ImageThread;
	JPanel imagePanel;
	JButton btnUp;
	JButton btnRight;
	JButton btnDown;
	JButton btnLeft;
	JButton btnConnect;
	JCheckBox chkSwitch;
	private JLabel lblImage;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				var frame = new Controller();
				frame.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public Controller() {
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				OnWindowLoad(e);
			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (communicator != null) {
					communicator.close();
				}
			}
		});
		setTitle("AC13 Desktop Controller");
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 700, 675);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
	    btnUp = new JButton("\u21E7");
	    btnUp.addMouseListener(new MouseAdapter() {
	    	
	    	@Override
	    	public void mouseReleased(MouseEvent e) {
	    		StopWheels();
	    	}
	    	@Override
	    	public void mousePressed(MouseEvent e) {
	    		btnUpOnclick(e);
	    	}
	    });
		
		btnUp.setBounds(82, 512, 55, 40);
		contentPane.add(btnUp);
		
		btnRight = new JButton("\u21E8");
		btnRight.addMouseListener(new MouseAdapter() {
	    	
	    	@Override
	    	public void mouseReleased(MouseEvent e) {
	    		StopWheels();
	    	}
	    	@Override
	    	public void mousePressed(MouseEvent e) {
	    		btnRightOnclick(e);
	    	}
	    });
		
		btnRight.setBounds(136, 553, 55, 40);
		contentPane.add(btnRight);
		
	    btnDown = new JButton("\u21E9");
	    btnDown.addMouseListener(new MouseAdapter() {
	    	
	    	@Override
	    	public void mouseReleased(MouseEvent e) {
	    		StopWheels();
	    	}
	    	@Override
	    	public void mousePressed(MouseEvent e) {
	    		btnDownOnclick(e);
	    	}
	    });
		
		btnDown.setBounds(82, 596, 55, 40);
		contentPane.add(btnDown);
		
		btnLeft = new JButton("\u21E6");
		btnLeft.addMouseListener(new MouseAdapter() {
	    	
	    	@Override
	    	public void mouseReleased(MouseEvent e) {
	    		StopWheels();
	    	}
	    	@Override
	    	public void mousePressed(MouseEvent e) {
	    		btnLeftOnclick(e);
	    	}
	    });
	
		btnLeft.setBounds(28, 553, 55, 40);
		contentPane.add(btnLeft);
		
	    imagePanel = new JPanel();
		imagePanel.setBackground(new Color(0, 0, 0));
		imagePanel.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		imagePanel.setBounds(28, 11, 640, 480);
		contentPane.add(imagePanel);
		imagePanel.setLayout(null);
		
		lblImage = new JLabel("");
		lblImage.setBounds(0, 0, 640, 480);
		imagePanel.add(lblImage);
		
	    btnConnect = new JButton("CONNECT");
		btnConnect.addActionListener(this::btnConnectOnclick);
		btnConnect.setBounds(294, 529, 114, 57);
		contentPane.add(btnConnect);
		
	    chkSwitch = new JCheckBox("InfraRed Switch");
		chkSwitch.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				chkInfraRedOnclick(e);
			}
		});
		chkSwitch.setBounds(494, 546, 149, 23);
		contentPane.add(chkSwitch);
	}
	

	/////////////////////////////////////////////////// COMUNICATION METHODS /////////////////////////////////////////////////
	
    private void receiveImage()
    {
    	
        Connect();
			
		try {
			long lastSequence = 0;
			while (communicator.isStreaming()) {
				// Wait for a frame newer than the last one shown; time out periodically
				// so the loop notices when streaming stops.
				Frame frame = communicator.awaitFrame(lastSequence, 500, TimeUnit.MILLISECONDS);
				if (frame == null) {
					continue;
				}
				lastSequence = frame.sequence();
				DisplayImage(frame.jpeg());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(Controller.this, e.toString());

		}
    	
    }
    
    private void DisplayImage(byte[] jpeg)
    {
    	
    	// Decode with ImageIO rather than new ImageIcon(byte[]): the rover pads its JPEG
    	// frames before the EOI marker, and the AWT Toolkit decoder behind ImageIcon prints
    	// "Corrupt JPEG data: N extraneous bytes before marker 0xd9" to stderr for every
    	// frame. ImageIO decodes the same frames without writing anything to the console.
    	BufferedImage frame;
    	try {
    		frame = ImageIO.read(new ByteArrayInputStream(jpeg));
    	} catch (IOException e) {
    		return; // undecodable frame: keep showing the previous one
    	}
    	if (frame == null) {
    		return;
    	}

    	// Swing components must only be touched on the event dispatch thread.
    	ImageIcon image = new ImageIcon(frame);
    	EventQueue.invokeLater(() -> lblImage.setIcon(image));

    }
    
    
    
    private void Connect(){	
		communicator.Connect();
		
		if(communicator.isConnected())
		{
			btnConnect.setText("DISCONNECT");
			SwitchControlsState(true);
		}
		else {
			SwitchControlsState(false);//Disabling controls buttons
		}
		 
	}
	
	private void Disconnect() {
		communicator.Disconnect();
		
		if(!communicator.isConnected())
		{
			btnConnect.setText("CONNECT");
			SwitchControlsState(false);
		}
		
	}
	
	private void StopWheels()
	{
		communicator.stopMovement();
	}
	
	
	private void SwitchControlsState(boolean state)
	{
		btnUp.setEnabled(state);
		btnDown.setEnabled(state);
		btnRight.setEnabled(state);
		btnLeft.setEnabled(state);
		chkSwitch.setEnabled(state);
		
	}
	/////////////////////////////////////////////////// EVENT HANDLERS /////////////////////////////////////////////////
	private void OnWindowLoad(WindowEvent e) {
		communicator = new AC13Comunicator();
		ImageThread = Thread.ofVirtual().name("image-receiver").start(() -> {
			try {
				receiveImage();
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(Controller.this, ex.toString());
			}
		});
	}
	
	private void chkInfraRedOnclick(MouseEvent e)
	{
		communicator.SwitchInfrared();
		
	}
	
	private void btnUpOnclick(MouseEvent e)
	{
		communicator.MoveFoward();
		
	}
	private void btnDownOnclick(MouseEvent e)
	{
		communicator.MoveBackward();
		
	}
	private void btnRightOnclick(MouseEvent e)
	{
		communicator.RotateRight();
		
	}
	
	private void btnLeftOnclick(MouseEvent e)
	{
		communicator.RotateLeft();
		
	}
	
	private void btnConnectOnclick(ActionEvent e)
	{
		if(communicator.isConnected())
		{
			Disconnect();
			communicator = new AC13Comunicator();
			lblImage.setIcon(null);//Remove image
		}
		else {
			ImageThread = Thread.ofVirtual().name("image-receiver").start(() -> {
				try {
					receiveImage();
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(Controller.this, ex.toString());
				}
			});
		}
		
	}
}
