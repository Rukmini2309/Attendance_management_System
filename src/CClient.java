import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import com.toedter.calendar.JDateChooser;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class CClient {

    private static final String HOST = "localhost";
    private static final int PORT = 2400;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private String username;
    private String role;
    private Integer studentId;

    private JTable table;
    private DefaultTableModel model;
    private JLabel summary;
    private JLabel permissionBadge;
    private JLabel permissionStatus;
    private JTextField dateField;
    private JDateChooser dateChooser;

    private JButton markPresent;
    private JButton markAbsent;

    private final Color BG = new Color(245, 247, 250);
    private final Color CARD = Color.WHITE;
    private final Color TEXT = new Color(25, 35, 50);
    private final Color MUTED = new Color(100, 110, 125);
    private final Color ACCENT = new Color(39, 96, 205);
    private final Color GREEN = new Color(28, 140, 82);
    private final Color RED = new Color(185, 50, 50);
    private final Color ORANGE = new Color(205, 125, 30);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CClient().showLogin());
    }

    private void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    private synchronized String send(String command) throws IOException {
        out.println(command);
        return in.readLine();
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private void showLogin() {
        try {
            connect();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    null,
                    "Cannot connect to server on port " + PORT
                            + ".\nStart SServer first.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        frame = new JFrame("Attendance Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(520, 450);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(35, 45, 35, 45));

        JLabel title = label(
                "Attendance Management",
                new Font("Segoe UI", Font.BOLD, 29),
                TEXT
        );

        JLabel sub = label(
                "Secure client-server attendance portal",
                new Font("Segoe UI", Font.PLAIN, 14),
                MUTED
        );

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(5));
        head.add(sub);

        JPanel card = new JPanel(new GridLayout(0, 1, 8, 8));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(25, 25, 25, 25));

        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();

        card.add(label("Username", normalFont(), MUTED));
        card.add(user);
        card.add(label("Password", normalFont(), MUTED));
        card.add(pass);

        JButton login = button("LOGIN", ACCENT);
        card.add(Box.createVerticalStrut(8));
        card.add(login);

        frame.getRootPane().setDefaultButton(login);
        pass.addActionListener(e -> login.doClick());
        user.addActionListener(e -> pass.requestFocusInWindow());

        login.addActionListener(e -> {
            try {
                String response = send(
                        "LOGIN|" + safe(user.getText()) + "|"
                                + safe(new String(pass.getPassword()))
                );

                if (response != null && response.startsWith("LOGIN_OK|")) {
                    String[] p = response.split("\\|", -1);

                    role = p[1];
                    username = p[2];
                    studentId = p[3].isEmpty()
                            ? null
                            : Integer.parseInt(p[3]);

                    frame.getContentPane().removeAll();
                    showDashboard();
                } else {
                    showMessage(
                            response,
                            "Login Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (Exception ex) {
                error(ex);
            }
        });

        root.add(head, BorderLayout.NORTH);
        root.add(card, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    // =========================================================
    // DASHBOARD
    // =========================================================

    private void showDashboard() {
        frame.getContentPane().removeAll();
        frame.setSize(1280, 760);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        // ---------- HEADER ----------
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        String dashboardTitle =
                "Teacher".equalsIgnoreCase(role)
                        ? "Teacher Dashboard"
                        : "CR".equalsIgnoreCase(role)
                        ? "CR Dashboard"
                        : "My Attendance";

        JLabel title = label(
                dashboardTitle,
                new Font("Segoe UI", Font.BOLD, 29),
                TEXT
        );

        JLabel who = label(
                "Welcome, " + username + "   •   "
                        + LocalDate.now().format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy")
                ),
                new Font("Segoe UI", Font.BOLD, 14),
                MUTED
        );

        JPanel headerRight = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 12, 0)
        );
        headerRight.setOpaque(false);
        headerRight.add(who);
        headerRight.add(logoutButton());

        top.add(title, BorderLayout.WEST);
        top.add(headerRight, BorderLayout.EAST);

        // ---------- CONTROLS ----------
        JPanel controls = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 9)
        );
        controls.setBackground(CARD);
        controls.setBorder(new EmptyBorder(5, 8, 5, 8));

        dateField = new JTextField(LocalDate.now().toString(), 10);
        dateField.setEditable(false);
        dateField.setVisible(false);

        dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("yyyy-MM-dd");
        dateChooser.setDate(new Date());
        dateChooser.setMaxSelectableDate(new Date());
        dateChooser.setPreferredSize(new Dimension(150, 32));

        JButton previous = button("◀ PREVIOUS", ACCENT);
        JButton today = button("TODAY", ACCENT);
        JButton next = button("NEXT ▶", ACCENT);
        JButton view = button("VIEW DATE", ACCENT);

        controls.add(new JLabel("Date:"));
        controls.add(dateChooser);
        controls.add(previous);
        controls.add(today);
        controls.add(next);
        controls.add(view);

        markPresent = null;
        markAbsent = null;

        // Teacher always gets edit buttons.
        // CR gets them only after teacher approval.
        if ("Teacher".equalsIgnoreCase(role)
                || ("CR".equalsIgnoreCase(role) && hasLocalCrPermission())) {

            markPresent = button("✓ MARK PRESENT", GREEN);
            markAbsent = button("✕ MARK ABSENT", RED);

            controls.add(markPresent);
            controls.add(markAbsent);
        }

        if ("Teacher".equalsIgnoreCase(role)) {
            JButton addStudent = button("+ ADD STUDENT", ACCENT);
            JButton requests = button("🔔 CR REQUESTS", ACCENT);

            permissionBadge = label(
                    " 0 pending ",
                    new Font("Segoe UI", Font.BOLD, 12),
                    MUTED
            );
            permissionBadge.setOpaque(true);
            permissionBadge.setBackground(new Color(235, 238, 243));
            permissionBadge.setBorder(
                    new EmptyBorder(8, 8, 8, 8)
            );

            controls.add(addStudent);
            controls.add(requests);
            controls.add(permissionBadge);

            addStudent.addActionListener(e -> addStudentDialog());
            requests.addActionListener(e -> showPermissions());
        }

        if ("CR".equalsIgnoreCase(role)) {
            JButton request = button(
                    "🔐 REQUEST EDIT PERMISSION",
                    ORANGE
            );

            controls.add(request);

            permissionStatus = label(
                    "Permission: checking...",
                    new Font("Segoe UI", Font.BOLD, 12),
                    MUTED
            );

            controls.add(permissionStatus);

            request.addActionListener(e -> requestPermissionDialog());
        }

        // ---------- TABLE ----------
        String[] columns = {
                "ID", "Student", "Today's Status", "Marked By", "Attendance %"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        table = new JTable(model);
        table.setRowHeight(38);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );
        table.setGridColor(new Color(190, 198, 210));
        table.setFillsViewportHeight(true);

        table.getTableHeader().setFont(
                new Font("Segoe UI", Font.BOLD, 14)
        );
        table.getTableHeader().setReorderingAllowed(false);

        // Double-click = individual student attendance details.
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2
                        && SwingUtilities.isLeftMouseButton(e)) {
                    showSelectedStudentDetails();
                }
            }
        });

        // ---------- BOTTOM ----------
        summary = label(
                " ",
                new Font("Segoe UI", Font.BOLD, 15),
                TEXT
        );

        JButton details = button(
                "VIEW SELECTED STUDENT",
                ACCENT
        );
        details.addActionListener(
                e -> showSelectedStudentDetails()
        );

        JPanel bottom = new JPanel(
                new BorderLayout()
        );
        bottom.setOpaque(false);
        bottom.add(summary, BorderLayout.WEST);
        bottom.add(details, BorderLayout.EAST);

        // ---------- ACTIONS ----------
        dateChooser.addPropertyChangeListener("date", e -> {
            if (e.getNewValue() instanceof Date) {
                LocalDate selected = ((Date) e.getNewValue()).toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                if (selected.isAfter(LocalDate.now())) {
                    dateChooser.setDate(new Date());
                    return;
                }
                dateField.setText(selected.toString());
                loadAttendance(selected.toString());
            }
        });

        previous.addActionListener(
                e -> moveDate(-1)
        );

        next.addActionListener(
                e -> moveDate(1)
        );

        today.addActionListener(e -> {
            dateChooser.setDate(new Date());
            dateField.setText(LocalDate.now().toString());
            loadAttendance(dateField.getText());
        });

        view.addActionListener(e ->
                loadAttendance(dateField.getText())
        );

        if (markPresent != null) {
            markPresent.addActionListener(
                    e -> changeAttendance("PRESENT")
            );
        }

        if (markAbsent != null) {
            markAbsent.addActionListener(
                    e -> changeAttendance("ABSENT")
            );
        }

        JPanel center = new JPanel(
                new BorderLayout(10, 10)
        );
        center.setOpaque(false);
        center.add(controls, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.revalidate();
        frame.repaint();

        loadAttendance(LocalDate.now().toString());

        if ("Teacher".equalsIgnoreCase(role)) {
            refreshPendingBadge();
        }

        if ("CR".equalsIgnoreCase(role)) {
            refreshCrPermission();
        }
    }

    // =========================================================
    // CALENDAR
    // =========================================================

    // =========================================================
    // ATTENDANCE
    // =========================================================

    private void moveDate(int days) {
        try {
            LocalDate d = LocalDate.parse(
                    dateField.getText()
            ).plusDays(days);

            if (d.isAfter(LocalDate.now())) d = LocalDate.now();
            dateChooser.setDate(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            dateField.setText(d.toString());
            loadAttendance(d.toString());

        } catch (Exception e) {
            showMessage(
                    "ERROR|Invalid date",
                    "Date",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void loadAttendance(String date) {
        try {
            String response = send("ATTEND|" + date);

            if (response == null
                    || !response.startsWith("ATTEND_OK|")) {
                showMessage(
                        response,
                        "Attendance",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            model.setRowCount(0);

            String[] p = response.split("\\|", -1);

            int present = 0;
            int absent = 0;
            int notSet = 0;

            for (int i = 2; i < p.length; i++) {
                String[] row = p[i].split(",", -1);

                if (row.length < 5) continue;

                model.addRow(row);

                if ("PRESENT".equals(row[2])) present++;
                else if ("ABSENT".equals(row[2])) absent++;
                else notSet++;
            }

            summary.setText(
                    "Present: " + present
                            + "     Absent: " + absent
                            + "     Not Set: " + notSet
                            + "     Total: " + (present + absent + notSet)
            );

            // CR is editable only for today and only after approval.
            updateCrEditingState(date);

        } catch (Exception e) {
            error(e);
        }
    }

    private void changeAttendance(String status) {
        int row = table.getSelectedRow();

        if (row < 0) {
            showMessage(
                    "ERROR|Select a student first.",
                    "Attendance",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        String date = dateField.getText();

        try {
            int id = Integer.parseInt(
                    model.getValueAt(row, 0).toString()
            );

            String response = send(
                    "MARK|" + date + "|" + id + "|" + status
            );

            showMessage(
                    response,
                    "Attendance",
                    response != null && response.startsWith("OK|")
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE
            );

            if (response != null && response.startsWith("OK|")) {
                loadAttendance(date);
            }

        } catch (Exception e) {
            error(e);
        }
    }

    private void updateCrEditingState(String date) {
        if (!"CR".equalsIgnoreCase(role)) return;

        boolean canEdit =
                date.equals(LocalDate.now().toString())
                        && hasLocalCrPermission();

        if (markPresent != null) {
            markPresent.setVisible(canEdit);
        }

        if (markAbsent != null) {
            markAbsent.setVisible(canEdit);
        }
    }

    // =========================================================
    // CR PERMISSION REQUEST
    // =========================================================

    private void requestPermissionDialog() {
        try {
            String response = send("TEACHERS");

            if (response == null
                    || !response.startsWith("TEACHERS_OK")) {
                showMessage(
                        response,
                        "Request Permission",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            String[] p = response.split("\\|", -1);

            List<String> teachers = new ArrayList<>();

            for (int i = 1; i < p.length; i++) {
                if (!p[i].isBlank()) teachers.add(p[i]);
            }

            if (teachers.isEmpty()) {
                showMessage(
                        "ERROR|No teacher account is available.",
                        "Request Permission",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            JComboBox<String> teacherBox =
                    new JComboBox<>(teachers.toArray(new String[0]));

            JTextArea reason = new JTextArea(5, 28);
            reason.setLineWrap(true);
            reason.setWrapStyleWord(true);

            JPanel panel = new JPanel(
                    new BorderLayout(8, 8)
            );

            JPanel fields = new JPanel(
                    new GridLayout(0, 1, 5, 5)
            );

            fields.add(label(
                    "Date: " + LocalDate.now(),
                    new Font("Segoe UI", Font.BOLD, 13),
                    TEXT
            ));

            fields.add(label(
                    "Select approving teacher:",
                    normalFont(),
                    MUTED
            ));

            fields.add(teacherBox);

            fields.add(label(
                    "Reason for editing today's attendance:",
                    normalFont(),
                    MUTED
            ));

            panel.add(fields, BorderLayout.NORTH);
            panel.add(new JScrollPane(reason), BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(
                    frame,
                    panel,
                    "Request Attendance Edit Permission",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) return;

            String selectedTeacher =
                    teacherBox.getSelectedItem().toString();

            String why = reason.getText().trim();

            if (why.isEmpty()) {
                showMessage(
                        "ERROR|Please enter a reason.",
                        "Request Permission",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            String command =
                    "REQUEST|" + LocalDate.now()
                            + "|" + safe(selectedTeacher)
                            + "|" + safe(why);

            String resultResponse = send(command);

            showMessage(
                    resultResponse,
                    "Request Permission",
                    resultResponse != null
                            && resultResponse.startsWith("OK|")
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE
            );

            refreshCrPermission();

        } catch (Exception e) {
            error(e);
        }
    }

    private boolean hasLocalCrPermission() {
        if (!"CR".equalsIgnoreCase(role)) return false;

        try {
            String response = send("MY_PERMISSION");

            return response != null
                    && response.contains(",GRANTED,");
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshCrPermission() {
        if (!"CR".equalsIgnoreCase(role)
                || permissionStatus == null) return;

        try {
            String response = send("MY_PERMISSION");

            if (response == null
                    || response.equals("MY_PERMISSION|NONE")) {
                permissionStatus.setText(
                        "Permission: NOT REQUESTED"
                );
                permissionStatus.setForeground(MUTED);
                return;
            }

            String[] p = response.split("\\|", -1);

            if (p.length < 2) return;

            String[] r = p[1].split(",", -1);

            if (r.length < 5) return;

            String teacher = r[1];
            String status = r[4];

            if ("GRANTED".equals(status)) {
                permissionStatus.setText(
                        "✓ APPROVED by " + teacher
                );
                permissionStatus.setForeground(GREEN);
            } else if ("PENDING".equals(status)) {
                permissionStatus.setText(
                        "⏳ PENDING • " + teacher
                );
                permissionStatus.setForeground(ORANGE);
            } else {
                permissionStatus.setText(
                        "✕ REJECTED by " + teacher
                );
                permissionStatus.setForeground(RED);
            }

            updateCrEditingState(dateField.getText());

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // TEACHER REQUESTS
    // =========================================================

    private void showPermissions() {
        try {
            String response = send("PERMISSIONS");

            if (response == null
                    || !response.startsWith("PERMISSIONS_OK")) {
                showMessage(
                        response,
                        "CR Requests",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            String[] p = response.split("\\|", -1);

            DefaultTableModel m =
                    new DefaultTableModel(
                            new String[]{
                                    "Request ID", "CR", "Date",
                                    "Reason", "Status", "Requested At"
                            }, 0
                    ) {
                        @Override
                        public boolean isCellEditable(int r, int c) {
                            return false;
                        }
                    };

            for (int i = 1; i < p.length; i++) {
                String[] r = p[i].split(",", -1);
                if (r.length >= 6) {
                    m.addRow(r);
                }
            }

            JTable requests = new JTable(m);
            requests.setRowHeight(32);
            requests.setSelectionMode(
                    ListSelectionModel.SINGLE_SELECTION
            );

            JPanel panel = new JPanel(
                    new BorderLayout(8, 8)
            );

            panel.add(
                    label(
                            "Requests sent specifically to " + username,
                            new Font("Segoe UI", Font.BOLD, 14),
                            TEXT
                    ),
                    BorderLayout.NORTH
            );

            panel.add(
                    new JScrollPane(requests),
                    BorderLayout.CENTER
            );

            JButton grant = button(
                    "✓ ACCEPT",
                    GREEN
            );

            JButton reject = button(
                    "✕ REJECT",
                    RED
            );

            JPanel actions = new JPanel(
                    new FlowLayout(FlowLayout.RIGHT)
            );
            actions.add(grant);
            actions.add(reject);

            panel.add(actions, BorderLayout.SOUTH);

            JDialog dialog = new JDialog(
                    frame,
                    "CR Edit Permission Requests",
                    true
            );

            grant.addActionListener(e ->
                    permissionAction(
                            requests, m, dialog, true
                    )
            );

            reject.addActionListener(e ->
                    permissionAction(
                            requests, m, dialog, false
                    )
            );

            dialog.add(panel);
            dialog.setSize(850, 420);
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);

            refreshPendingBadge();

        } catch (Exception e) {
            error(e);
        }
    }

    private void permissionAction(
            JTable requests,
            DefaultTableModel m,
            JDialog dialog,
            boolean grant) {

        int row = requests.getSelectedRow();

        if (row < 0) {
            showMessage(
                    "ERROR|Select a request first.",
                    "CR Request",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            int requestId = Integer.parseInt(
                    m.getValueAt(row, 0).toString()
            );

            String response = send(
                    (grant ? "GRANT|" : "REJECT|") + requestId
            );

            showMessage(
                    response,
                    "CR Request",
                    response != null && response.startsWith("OK|")
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE
            );

            if (response != null && response.startsWith("OK|")) {
                m.removeRow(row);
                refreshPendingBadge();

                if (m.getRowCount() == 0) {
                    dialog.dispose();
                }
            }

        } catch (Exception e) {
            error(e);
        }
    }

    private void refreshPendingBadge() {
        if (permissionBadge == null) return;

        try {
            String response = send("PENDING_REQUESTS");

            int count = 0;

            if (response != null
                    && response.startsWith("PENDING_OK")) {

                String[] p = response.split("\\|", -1);
                count = Math.max(0, p.length - 1);
            }

            permissionBadge.setText(
                    " " + count + " pending "
            );

            permissionBadge.setBackground(
                    count > 0
                            ? new Color(255, 225, 225)
                            : new Color(235, 238, 243)
            );

            permissionBadge.setForeground(
                    count > 0 ? RED : MUTED
            );

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // STUDENT DETAILS
    // =========================================================

    private void showSelectedStudentDetails() {
        int row = table.getSelectedRow();

        if (row < 0) {
            if ("Student".equalsIgnoreCase(role)
                    && studentId != null) {
                showStudentDetails(studentId);
            } else {
                showMessage(
                        "ERROR|Select a student first.",
                        "Student Details",
                        JOptionPane.WARNING_MESSAGE
                );
            }
            return;
        }

        try {
            int id = Integer.parseInt(
                    model.getValueAt(row, 0).toString()
            );

            showStudentDetails(id);

        } catch (Exception e) {
            error(e);
        }
    }

    private void showStudentDetails(int id) {
        try {
            String response = send(
                    "STUDENT_DETAILS|" + id
            );

            if (response == null
                    || !response.startsWith("DETAILS_OK|")) {
                showMessage(
                        response,
                        "Student Details",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            String[] p = response.split("\\|", -1);

            if (p.length < 2) return;

            String[] s = p[1].split(",", -1);

            if (s.length < 6) return;

            String studentName = s[1];
            String present = s[2];
            String absent = s[3];
            String marked = s[4];
            String percent = s[5];

            JPanel root = new JPanel(
                    new BorderLayout(10, 10)
            );
            root.setBorder(
                    new EmptyBorder(15, 15, 15, 15)
            );

            JPanel summaryPanel = new JPanel(
                    new GridLayout(1, 4, 10, 0)
            );

            summaryPanel.add(
                    statCard("PRESENT", present, GREEN)
            );

            summaryPanel.add(
                    statCard("ABSENT", absent, RED)
            );

            summaryPanel.add(
                    statCard("MARKED DAYS", marked, ACCENT)
            );

            summaryPanel.add(
                    statCard("ATTENDANCE", percent, ORANGE)
            );

            root.add(
                    label(
                            studentName + " • Attendance till today",
                            new Font("Segoe UI", Font.BOLD, 18),
                            TEXT
                    ),
                    BorderLayout.NORTH
            );

            root.add(summaryPanel, BorderLayout.CENTER);

            DefaultTableModel historyModel =
                    new DefaultTableModel(
                            new String[]{"Date", "Status", "Marked By"},
                            0
                    ) {
                        @Override
                        public boolean isCellEditable(int r, int c) {
                            return false;
                        }
                    };

            for (int i = 2; i < p.length; i++) {
                String[] r = p[i].split(",", -1);

                if (r.length >= 4 && "H".equals(r[0])) {
                    historyModel.addRow(
                            new Object[]{
                                    r[1], r[2], r[3]
                            }
                    );
                }
            }

            JTable history = new JTable(historyModel);
            history.setRowHeight(30);

            root.add(
                    new JScrollPane(history),
                    BorderLayout.SOUTH
            );

            // Put the summary and history in a better structure.
            JPanel center = new JPanel(
                    new BorderLayout(10, 10)
            );
            center.setOpaque(false);
            center.add(summaryPanel, BorderLayout.NORTH);
            center.add(new JScrollPane(history), BorderLayout.CENTER);

            root.remove(1);
            root.add(center, BorderLayout.CENTER);

            JDialog dialog = new JDialog(
                    frame,
                    "Attendance Details • " + studentName,
                    true
            );

            dialog.add(root);
            dialog.setSize(720, 560);
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);

        } catch (Exception e) {
            error(e);
        }
    }

    private JPanel statCard(
            String title, String value, Color color) {

        JPanel panel = new JPanel(
                new GridLayout(2, 1)
        );

        panel.setBackground(CARD);
        panel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(215, 220, 228)
                        ),
                        new EmptyBorder(10, 12, 10, 12)
                )
        );

        JLabel t = label(
                title,
                new Font("Segoe UI", Font.BOLD, 11),
                MUTED
        );

        JLabel v = label(
                value,
                new Font("Segoe UI", Font.BOLD, 23),
                color
        );

        panel.add(t);
        panel.add(v);

        return panel;
    }

    // =========================================================
    // ADD STUDENT
    // =========================================================

    private void addStudentDialog() {
        JTextField name = new JTextField();
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();

        JPanel p = new JPanel(
                new GridLayout(0, 1, 7, 7)
        );

        p.add(label("Student full name", normalFont(), MUTED));
        p.add(name);

        p.add(label("Login username", normalFont(), MUTED));
        p.add(user);

        p.add(label("Login password", normalFont(), MUTED));
        p.add(pass);

        int result = JOptionPane.showConfirmDialog(
                frame,
                p,
                "Add New Student",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) return;

        if (name.getText().trim().isEmpty()
                || user.getText().trim().isEmpty()
                || pass.getPassword().length == 0) {

            showMessage(
                    "ERROR|All fields are required.",
                    "Add Student",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            String response = send(
                    "ADD_STUDENT|"
                            + safe(name.getText())
                            + "|"
                            + safe(user.getText())
                            + "|"
                            + safe(new String(pass.getPassword()))
            );

            showMessage(
                    response,
                    "Add Student",
                    response != null && response.startsWith("OK|")
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE
            );

            if (response != null && response.startsWith("OK|")) {
                loadAttendance(dateField.getText());
            }

        } catch (Exception e) {
            error(e);
        }
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    private JButton logoutButton() {
        JButton b = button("LOGOUT", RED);
        b.addActionListener(e -> logout());
        return b;
    }

    private void logout() {
        try {
            if (out != null) out.println("LOGOUT");
        } catch (Exception ignored) {
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }

        username = null;
        role = null;
        studentId = null;
        table = null;
        model = null;
        summary = null;
        permissionBadge = null;
        permissionStatus = null;
        dateField = null;
        dateChooser = null;
        markPresent = null;
        markAbsent = null;

        if (frame != null) {
            frame.dispose();
        }

        showLogin();
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private JButton button(String text, Color color) {
        JButton b = new JButton(text);

        b.setFocusPainted(false);
        b.setFont(
                new Font("Segoe UI", Font.BOLD, 12)
        );
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setEnabled(true);
        b.setBorder(
                BorderFactory.createEmptyBorder(
                        9, 13, 9, 13
                )
        );
        b.setCursor(
                new Cursor(Cursor.HAND_CURSOR)
        );

        return b;
    }

    private JLabel label(
            String text, Font font, Color color) {

        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    private Font normalFont() {
        return new Font(
                "Segoe UI",
                Font.PLAIN,
                13
        );
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("|", "/")
                .replace(",", " ")
                .replace("\n", " ")
                .trim();
    }

    private void showMessage(
            String response,
            String title,
            int type) {

        String[] p = response == null
                ? new String[]{"ERROR", "No response from server"}
                : response.split("\\|", 2);

        JOptionPane.showMessageDialog(
                frame,
                p.length > 1 ? p[1] : p[0],
                title,
                type
        );
    }

    private void error(Exception e) {
        showMessage(
                "ERROR|"
                        + (e.getMessage() == null
                        ? e.toString()
                        : e.getMessage()),
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}