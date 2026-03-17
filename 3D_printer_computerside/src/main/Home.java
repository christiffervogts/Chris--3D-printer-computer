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
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;

@SuppressWarnings("serial")
public class Home extends JPanel implements MouseListener, MouseMotionListener {
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
    STL_file_minipulation stl_min = new STL_file_minipulation(this);

    // --- rotation state ---
    double rotX = 0.4;   // pitch (up/down) in radians — nice starting angle
    double rotY = 0.6;   // yaw   (left/right) in radians
    int dragLastX, dragLastY;
    boolean dragging = false;

    public Home(JFrame frame) {
        this.frame = frame;
        background = new Color(81, 100, 130);
        font20 = new Font("Fixedsys Regular", Font.PLAIN, 20);
        font25 = new Font("Fixedsys Regular", Font.PLAIN, 25);
        setBackground(background);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        frame.setSize(Toolkit.getDefaultToolkit().getScreenSize().width / 2,
                Toolkit.getDefaultToolkit().getScreenSize().height / 2);
        frame.setLocationRelativeTo(null);
        frame.add(this);
        frame.addMouseMotionListener(this);
        frame.addMouseListener(this);
    }

    /** Called by STL_file_minipulation once parsing + sorting is complete. */
    public void onMeshReady() {
        home_screen = false;
        repaint();
    }

    // -------------------------------------------------------------------------
    //  Home screen
    // -------------------------------------------------------------------------
    public void HOME() {
        g2.setColor(Color.BLACK);
        g2.setFont(font20);
        if (hovered == 1) g2.setFont(font25);

        metrics = g2.getFontMetrics();
        int x = (getWidth()  - metrics.stringWidth(text)) / 2;
        int y = (getHeight() - metrics.getHeight())       / 2 + metrics.getAscent();

        if (home_screen_setup) {
            home_screen_setup = false;
            home_button = new Rectangle(x - 50, y - 50, 250, 100);
        }
        g2.drawString(text, x, y);
        g2.draw(home_button);
    }

    // -------------------------------------------------------------------------
    //  3-D mesh draw with drag-to-rotate
    // -------------------------------------------------------------------------
    public void MESH() {
        // stl_min exposes raw float coords as rawVertices (float[N][3], one row per vertex)
        float[][] raw = stl_min.rawVertices;
        if (raw == null || raw.length < 3) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- 1. centroid so we rotate around the model centre ---
        double cx = 0, cy = 0, cz = 0;
        for (float[] v : raw) { cx += v[0]; cy += v[1]; cz += v[2]; }
        cx /= raw.length; cy /= raw.length; cz /= raw.length;

        // --- 2. apply rotation matrix to every vertex ---
        double sinY = Math.sin(rotY), cosY = Math.cos(rotY);
        double sinX = Math.sin(rotX), cosX = Math.cos(rotX);

        double[][] rot = new double[raw.length][3];
        double maxR = 0;
        for (int i = 0; i < raw.length; i++) {
            double x = raw[i][0] - cx;
            double y = raw[i][1] - cy;
            double z = raw[i][2] - cz;

            // rotate around Y axis (yaw — left/right drag)
            double x1 =  x * cosY + z * sinY;
            double y1 =  y;
            double z1 = -x * sinY + z * cosY;

            // rotate around X axis (pitch — up/down drag)
            double x2 = x1;
            double y2 = y1 * cosX - z1 * sinX;
            double z2 = y1 * sinX + z1 * cosX;

            rot[i][0] = x2;
            rot[i][1] = y2;
            rot[i][2] = z2;

            double r = Math.sqrt(x2 * x2 + y2 * y2);
            if (r > maxR) maxR = r;
        }

        // --- 3. scale to fit panel ---
        int padding = 40;
        double available = Math.min(getWidth(), getHeight()) / 2.0 - padding;
        double scale = (maxR == 0) ? 1.0 : available / maxR;

        int cx2d = getWidth()  / 2;
        int cy2d = getHeight() / 2;

        // --- 4. build triangle list; painter's sort (back → front) by avg Z ---
        int triCount = raw.length / 3;
        ArrayList<int[]> tris = new ArrayList<>(triCount);

        for (int t = 0; t < triCount; t++) {
            int b = t * 3;
            double[] p0 = rot[b], p1 = rot[b + 1], p2 = rot[b + 2];

            int sx0 = cx2d + (int)(p0[0] * scale);
            int sy0 = cy2d - (int)(p0[1] * scale);   // flip Y so +Y is up
            int sx1 = cx2d + (int)(p1[0] * scale);
            int sy1 = cy2d - (int)(p1[1] * scale);
            int sx2 = cx2d + (int)(p2[0] * scale);
            int sy2 = cy2d - (int)(p2[1] * scale);

            int avgZ = (int)((p0[2] + p1[2] + p2[2]) * 1000.0 / 3.0);
            tris.add(new int[]{sx0, sy0, sx1, sy1, sx2, sy2, avgZ});
        }

        // smallest Z = furthest back → draw first
        tris.sort(Comparator.comparingInt(a -> a[6]));

        // --- 5. draw back-to-front with simple Z shading ---
        for (int[] tri : tris) {
            int[] xs = {tri[0], tri[2], tri[4]};
            int[] ys = {tri[1], tri[3], tri[5]};

            double t = (tri[6] / 1000.0) / (maxR == 0 ? 1 : maxR);
            float bright = (float) Math.max(0.35, Math.min(1.0, 0.7 + t * 0.3));

            g2.setColor(new Color(
                (int)(160 * bright),
                (int)(190 * bright),
                (int)(215 * bright),
                210));
            g2.fillPolygon(xs, ys, 3);

            g2.setColor(new Color(30, 40, 60, 120));
            g2.drawPolygon(xs, ys, 3);
        }
    }

    // -------------------------------------------------------------------------
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        this.g2 = g2;
        if (home_screen) HOME();
        else             MESH();
    }

    // -------------------------------------------------------------------------
    //  Mouse handlers
    // -------------------------------------------------------------------------
    @Override
    public void mouseDragged(MouseEvent e) {
        if (!home_screen && dragging) {
            int dx = e.getX() - dragLastX;
            int dy = e.getY() - dragLastY;
            rotY += dx * 0.01;   // horizontal drag → yaw
            rotX += dy * 0.01;   // vertical   drag → pitch
            dragLastX = e.getX();
            dragLastY = e.getY();
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (home_button != null && home_button.contains(e.getPoint())) {
            if (hovered != 1)  { hovered =  1; repaint(); }
        } else {
            if (hovered != -1) { hovered = -1; repaint(); }
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        if (!home_screen) {
            dragging  = true;
            dragLastX = e.getX();
            dragLastY = e.getY();
        }

        if (home_button != null && home_button.contains(e.getPoint())) {
            try {
                FileDialog dialog = new FileDialog((Frame) null, "Open an STL File", FileDialog.LOAD);
                dialog.setFile("*.stl");
                dialog.setVisible(true);
                String directory = dialog.getDirectory();
                String fileName  = dialog.getFile();
                if (fileName != null) {
                    if (!fileName.toLowerCase().endsWith(".stl")) fileName += ".stl";
                    File file = new File(directory, fileName);
                    System.out.println("LOADED: " + file.getAbsolutePath());
                    stl_min.stl_get(file);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override public void mouseReleased(MouseEvent e) { dragging = false; }
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}
}