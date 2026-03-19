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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.util.Arrays;

@SuppressWarnings("serial")
public class Home extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    private static final Color BACKGROUND_COLOR = new Color(81, 100, 130);
    private static final Color EDGE_COLOR        = new Color(30, 40, 60, 10);

    private static final float BASE_BRIGHTNESS   = 0.7f;
    private static final float BRIGHTNESS_RANGE  = 0.3f;
    private static final float MIN_BRIGHTNESS    = 0.35f;

    private static final int   MESH_FILL_ALPHA   = 210;
    private static final int   MESH_BASE_RED     = 160;
    private static final int   MESH_BASE_GREEN   = 190;
    private static final int   MESH_BASE_BLUE    = 215;

    private static final int   HOME_BUTTON_PAD_X = 50;
    private static final int   HOME_BUTTON_PAD_Y = 50;
    private static final int   HOME_BUTTON_W     = 250;
    private static final int   HOME_BUTTON_H     = 100;

    private static final int   MESH_PADDING      = 40;
    private static final double DRAG_SENSITIVITY = 0.01;
    private static final double ZOOM_FACTOR      = 0.1;
    private static final double MIN_ZOOM         = 0.1;
    private static final double MAX_ZOOM         = 10.0;

    // Packed triBuffer slot offsets
    private static final int BUF_X0    = 0;
    private static final int BUF_Y0    = 1;
    private static final int BUF_X1    = 2;
    private static final int BUF_Y1    = 3;
    private static final int BUF_X2    = 4;
    private static final int BUF_Y2    = 5;
    private static final int BUF_AVG_Z = 6;
    private static final int BUF_COLOR = 7;
    private static final int BUF_SLOTS = 8;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    JFrame frame;
    Graphics2D g2;

    Font font20, font25;
    boolean isHomeScreen      = true;
    boolean homeNeedsSetup    = true;
    int     hoveredButton     = -1;

    String    homeButtonText = "Open an STL File";
    Rectangle homeButtonRect;
    FontMetrics metrics;

    STL_file_minipulation stlLoader = new STL_file_minipulation(this);

    // Rotation angles (radians)
    double rotX = 0.4;
    double rotY = 0.6;

    // Zoom multiplier (1.0 = default fit-to-screen)
    double zoom = 1.0;

    // Mouse drag state
    int     dragLastX, dragLastY;
    boolean isDragging = false;

    // Mesh rendering cache
    private int[]     triBuffer;      // flattened per-triangle data: BUF_SLOTS ints each
    private int       triCount;
    private Integer[] sortIndices;
    private double[]  rotatedVerts;   // vertices after applying current rotation
    private double    cachedMaxRadius;
    private double    lastCachedRotX  = Double.NaN;
    private double    lastCachedRotY  = Double.NaN;
    private double[]  centeredVerts;  // vertices centered at origin, before rotation

    // Reusable arrays to avoid per-frame allocation
    private final int[] polyX = new int[3];
    private final int[] polyY = new int[3];

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public Home(JFrame frame) {
        this.frame = frame;
        font20 = new Font("Fixedsys Regular", Font.PLAIN, 20);
        font25 = new Font("Fixedsys Regular", Font.PLAIN, 25);

        setBackground(BACKGROUND_COLOR);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(
            Toolkit.getDefaultToolkit().getScreenSize().width  / 2,
            Toolkit.getDefaultToolkit().getScreenSize().height / 2
        );
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.add(this);
        frame.addMouseMotionListener(this);
        frame.addMouseListener(this);
        frame.addMouseWheelListener(this);
    }

    // -------------------------------------------------------------------------
    // Mesh lifecycle
    // -------------------------------------------------------------------------

    /** Called by STL_file_minipulation once the mesh is fully loaded. */
    public void onMeshReady() {
        isHomeScreen = false;
        buildMeshCache();
        repaint();
    }

    /**
     * Pre-processes raw vertices: centers them, computes a bounding radius,
     * and allocates all per-frame working arrays.
     */
    private void buildMeshCache() {
        float[][] raw = stlLoader.rawVertices;
        if (raw == null || raw.length < 3) return;

        int vertexCount = raw.length;
        triCount    = vertexCount / 3;
        triBuffer   = new int[triCount * BUF_SLOTS];
        sortIndices = new Integer[triCount];
        rotatedVerts = new double[vertexCount * 3];

        // Compute centroid
        double cx = 0, cy = 0, cz = 0;
        for (float[] v : raw) { cx += v[0]; cy += v[1]; cz += v[2]; }
        cx /= vertexCount;
        cy /= vertexCount;
        cz /= vertexCount;

        // Store centered vertices
        centeredVerts = new double[vertexCount * 3];
        for (int i = 0; i < vertexCount; i++) {
            centeredVerts[i * 3    ] = raw[i][0] - cx;
            centeredVerts[i * 3 + 1] = raw[i][1] - cy;
            centeredVerts[i * 3 + 2] = raw[i][2] - cz;
        }

        // Compute bounding radius (XY plane only, used for scale fitting)
        double maxRadius2 = 0;
        for (int i = 0; i < vertexCount; i++) {
            double x  = centeredVerts[i * 3    ];
            double y  = centeredVerts[i * 3 + 1];
            double r2 = x * x + y * y;
            if (r2 > maxRadius2) maxRadius2 = r2;
        }
        cachedMaxRadius = Math.sqrt(maxRadius2);

        // Initialise sort index array
        for (int i = 0; i < triCount; i++) sortIndices[i] = i;

        // Force rotation cache rebuild on next draw
        lastCachedRotX = Double.NaN;
    }

    // -------------------------------------------------------------------------
    // Rotation helpers
    // -------------------------------------------------------------------------

    /**
     * Applies the current rotX / rotY Euler angles to centeredVerts,
     * writing results into rotatedVerts. Skips work if angles are unchanged.
     */
    private void updateRotatedVerts() {
        if (rotX == lastCachedRotX && rotY == lastCachedRotY) return;
        lastCachedRotX = rotX;
        lastCachedRotY = rotY;

        double sinY = Math.sin(rotY), cosY = Math.cos(rotY);
        double sinX = Math.sin(rotX), cosX = Math.cos(rotX);

        int vertexCount = centeredVerts.length / 3;
        for (int i = 0; i < vertexCount; i++) {
            double x = centeredVerts[i * 3    ];
            double y = centeredVerts[i * 3 + 1];
            double z = centeredVerts[i * 3 + 2];

            // Rotate around Y axis
            double xRotY =  x * cosY + z * sinY;
            double zRotY = -x * sinY + z * cosY;

            // Rotate around X axis
            rotatedVerts[i * 3    ] = xRotY;
            rotatedVerts[i * 3 + 1] = y * cosX - zRotY * sinX;
            rotatedVerts[i * 3 + 2] = y * sinX + zRotY * cosX;
        }
    }

    // -------------------------------------------------------------------------
    // Drawing — home screen
    // -------------------------------------------------------------------------

    private void drawHomeScreen() {
        g2.setColor(Color.BLACK);
        g2.setFont(hoveredButton == 1 ? font25 : font20);

        metrics = g2.getFontMetrics();
        int textX = (getWidth()  - metrics.stringWidth(homeButtonText)) / 2;
        int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

        if (homeNeedsSetup) {
            homeNeedsSetup = false;
            homeButtonRect = new Rectangle(
                textX - HOME_BUTTON_PAD_X,
                textY - HOME_BUTTON_PAD_Y,
                HOME_BUTTON_W,
                HOME_BUTTON_H
            );
        }

        g2.drawString(homeButtonText, textX, textY);
        g2.draw(homeButtonRect);
    }

    // -------------------------------------------------------------------------
    // Drawing — mesh
    // -------------------------------------------------------------------------

    private void drawMesh() {
        if (centeredVerts == null) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        updateRotatedVerts();

        double baseScale = computeBaseScale();
        double scale     = baseScale * zoom;

        int canvasCX = getWidth()  / 2;
        int canvasCY = getHeight() / 2;

        fillTriangleBuffer(scale, canvasCX, canvasCY);
        sortTrianglesByDepth();
        renderTriangles();
    }

    /**
     * Computes the scale factor that fits the mesh to the viewport
     * (ignoring zoom — zoom is applied on top of this).
     */
    private double computeBaseScale() {
        double available = Math.min(getWidth(), getHeight()) / 2.0 - MESH_PADDING;
        return (cachedMaxRadius == 0) ? 1.0 : available / cachedMaxRadius;
    }

    /**
     * Projects each triangle's rotated vertices to 2-D screen coordinates,
     * computes a depth value and a shading colour, and stores everything in
     * triBuffer for later sorting and rendering.
     */
    private void fillTriangleBuffer(double scale, int canvasCX, int canvasCY) {
        for (int tri = 0; tri < triCount; tri++) {
            int vertBase = tri * 3;    // first vertex index of this triangle
            int bufBase  = tri * BUF_SLOTS;

            // --- Project three vertices to screen space ---
            for (int corner = 0; corner < 3; corner++) {
                int vi = (vertBase + corner) * 3;
                triBuffer[bufBase + corner * 2    ] = canvasCX + (int)(rotatedVerts[vi    ] * scale);
                triBuffer[bufBase + corner * 2 + 1] = canvasCY - (int)(rotatedVerts[vi + 1] * scale);
            }

            // --- Depth: average Z of the three vertices (scaled to int) ---
            double z0 = rotatedVerts[vertBase       * 3 + 2];
            double z1 = rotatedVerts[(vertBase + 1) * 3 + 2];
            double z2 = rotatedVerts[(vertBase + 2) * 3 + 2];
            int avgZInt = (int)((z0 + z1 + z2) * 1000.0 / 3.0);
            triBuffer[bufBase + BUF_AVG_Z] = avgZInt;

            // --- Shading colour based on depth ---
            double normalizedDepth = (avgZInt / 1000.0) / (cachedMaxRadius == 0 ? 1 : cachedMaxRadius);
            float brightness = (float) Math.max(MIN_BRIGHTNESS,
                                Math.min(1.0, BASE_BRIGHTNESS + normalizedDepth * BRIGHTNESS_RANGE));

            int red   = (int)(MESH_BASE_RED   * brightness);
            int green = (int)(MESH_BASE_GREEN * brightness);
            int blue  = (int)(MESH_BASE_BLUE  * brightness);
            triBuffer[bufBase + BUF_COLOR] = (MESH_FILL_ALPHA << 24) | (red << 16) | (green << 8) | blue;
        }
    }

    /** Sorts triangles back-to-front so closer triangles paint over distant ones. */
    private void sortTrianglesByDepth() {
        Arrays.sort(sortIndices, (a, b) -> triBuffer[a * BUF_SLOTS + BUF_AVG_Z]
                                         - triBuffer[b * BUF_SLOTS + BUF_AVG_Z]);
    }

    /** Iterates over depth-sorted triangles and paints fill + edge. */
    private void renderTriangles() {
        for (int si = 0; si < triCount; si++) {
            int tri    = sortIndices[si];
            int bufBase = tri * BUF_SLOTS;

            polyX[0] = triBuffer[bufBase + BUF_X0]; polyY[0] = triBuffer[bufBase + BUF_Y0];
            polyX[1] = triBuffer[bufBase + BUF_X1]; polyY[1] = triBuffer[bufBase + BUF_Y1];
            polyX[2] = triBuffer[bufBase + BUF_X2]; polyY[2] = triBuffer[bufBase + BUF_Y2];

            // Fill
            g2.setColor(new Color(triBuffer[bufBase + BUF_COLOR], true));
            g2.fillPolygon(polyX, polyY, 3);

            // Subtle edge
            g2.setColor(EDGE_COLOR);
            g2.drawPolygon(polyX, polyY, 3);
        }
    }

    // -------------------------------------------------------------------------
    // paintComponent
    // -------------------------------------------------------------------------

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g2 = (Graphics2D) g;
        if (isHomeScreen) drawHomeScreen();
        else              drawMesh();
    }

    // -------------------------------------------------------------------------
    // Mouse event handlers
    // -------------------------------------------------------------------------

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!isHomeScreen && isDragging) {
            int dx = e.getX() - dragLastX;
            int dy = e.getY() - dragLastY;
            rotY += dx * DRAG_SENSITIVITY;
            rotX += dy * DRAG_SENSITIVITY;
            dragLastX = e.getX();
            dragLastY = e.getY();
            repaint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        boolean over = homeButtonRect != null && homeButtonRect.contains(e.getPoint());
        int newHover = over ? 1 : -1;
        if (newHover != hoveredButton) {
            hoveredButton = newHover;
            repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!isHomeScreen) {
            isDragging = true;
            dragLastX  = e.getX();
            dragLastY  = e.getY();
        } else if (homeButtonRect != null && homeButtonRect.contains(e.getPoint())) {
            openFileDialog();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) { isDragging = false; }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (!isHomeScreen) {
            // Negative rotation = scroll up = zoom in
            double delta = -e.getPreciseWheelRotation() * ZOOM_FACTOR;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + delta));
            repaint();
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e)  {}

    // -------------------------------------------------------------------------
    // File dialog
    // -------------------------------------------------------------------------

    private void openFileDialog() {
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
                stlLoader.stl_get(file);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}