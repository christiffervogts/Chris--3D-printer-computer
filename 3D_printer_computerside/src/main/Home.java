package main;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
@SuppressWarnings("serial")
public class Home extends JPanel implements MouseListener, MouseMotionListener{


	JFrame frame;
	Graphics2D g2;
	
	Color background;
	Font font20, font25;
	boolean home_screen = true;
	boolean home_screen_setup = true;

	int hovered = -1;
	
	String text = "Open an STL File";
	Rectangle home_button;
    FontMetrics metrics;

	
	public Home(JFrame frame) {
		this.frame = frame;
		background = new Color(81, 100, 130);
		font20 = new Font("Fixedsys Regular", Font.PLAIN, 20);
		font25 = new Font("Fixedsys Regular", Font.PLAIN, 25);

	    setBackground(background);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		frame.setSize(Toolkit.getDefaultToolkit().getScreenSize().width/2, Toolkit.getDefaultToolkit().getScreenSize().height/2);
		frame.setLocationRelativeTo(null);
		frame.add(this);
		frame.addMouseMotionListener(this);
		frame.addMouseListener(this);
	}
	
	public void HOME() {
		
		
		g2.setColor(Color.BLACK);	
		g2.setFont(font20);
		
		if(hovered == 1) {
			g2.setFont(font25);
		}
		metrics = g2.getFontMetrics();
		int x = (getWidth() - metrics.stringWidth(text)) / 2;
		int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
		
		if(home_screen_setup) {
			home_screen_setup = false;
			home_button = new Rectangle(x-50, y-50, 250, 100);			
		}
		
		g2.drawString(text, x, y);
		g2.draw(home_button);
	}
	
	public void paintComponent(Graphics g) {

		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g;
		this.g2 = g2;
		if(home_screen) {
			HOME();
		}
		repaint();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if(home_button.contains(e.getPoint())) {
			hovered = 1;
		}
		else {
			hovered = -1;
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	    if (home_button.contains(e.getPoint())) {
	        try {
	            FileDialog dialog = new FileDialog((Frame) null, "Open an STL File", FileDialog.LOAD);
	            dialog.setFile("*.stl");
	            dialog.setVisible(true);

	            String directory = dialog.getDirectory();
	            String fileName = dialog.getFile();

	            if (fileName != null) {
	                if (!fileName.toLowerCase().endsWith(".stl")) {
	                    fileName += ".stl";
	                }

	                File file = new File(directory, fileName);
	                file.createNewFile();

	                System.out.println("LOADED to: " + file.getAbsolutePath());
	            }

	        } catch (Exception ex) {
	            ex.printStackTrace();
	        }
	    }
	}	@Override
	public void mouseReleased(MouseEvent e) {		
	}

	@Override
	public void mouseEntered(MouseEvent e) {		
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

}
