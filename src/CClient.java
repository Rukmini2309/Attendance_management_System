import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class CClient{
    private static Socket socket;
    private static DataInputStream input;
    private static DataOutputStream output;
    private static boolean connectionActive = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            initializeConnection();
            new RoleSelectionFrame();
        });
    }

    private static void initializeConnection() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket("localhost", 2400);
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());
                connectionActive = true;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Connection to server failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void reconnect() {
        closeConnection();
        initializeConnection();
    }

    private static void closeConnection() {
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
            connectionActive = false;
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    static class RoleSelectionFrame extends JFrame {
        public RoleSelectionFrame() {
            setTitle("Attendance System");
            setSize(300, 200);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel titleLabel = new JLabel("Select Your Role", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));

            JButton teacherBtn = new JButton("Teacher");
            JButton studentBtn = new JButton("Student");

            teacherBtn.addActionListener(e -> {
                setVisible(false);
                SwingUtilities.invokeLater(() -> new LoginFrame("Teacher")); // Open LoginFrame for Teacher
            });

            studentBtn.addActionListener(e -> {
                setVisible(false);
                SwingUtilities.invokeLater(() -> new LoginFrame("Student")); // Open LoginFrame for Student
            });

            panel.add(titleLabel);
            panel.add(teacherBtn);
            panel.add(studentBtn);

            add(panel);
            setVisible(true);
        }
    }

    static class LoginFrame extends JFrame {
        public LoginFrame(String role) {
            setTitle(role + " Login");
            setSize(300, 200);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel usernameLabel = new JLabel("Username:");
            JTextField usernameField = new JTextField();
            JLabel passwordLabel = new JLabel("Password:");
            JPasswordField passwordField = new JPasswordField();

            JButton loginBtn = new JButton("Login");
            loginBtn.addActionListener(e -> {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword()).trim();
                if (authenticate(role, username, password)) {
                    setVisible(false);
                    if (role.equalsIgnoreCase("Teacher")) {
                        SwingUtilities.invokeLater(() -> new TeacherFrame(new RoleSelectionFrame()));
                    } else {
                        SwingUtilities.invokeLater(() -> new StudentFrame(new RoleSelectionFrame()));
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            panel.add(usernameLabel);
            panel.add(usernameField);
            panel.add(passwordLabel);
            panel.add(passwordField);
            panel.add(new JLabel()); // Empty cell
            panel.add(loginBtn);

            add(panel);
            setVisible(true);
        }

        private boolean authenticate(String role, String username, String password) {
            try {
                output.writeUTF("LOGIN");
                output.writeUTF(role);
                output.writeUTF(username);
                output.writeUTF(password);
                System.out.println("Sent login request: Role=" + role + ", Username=" + username); // Debug message
                String response = input.readUTF();
                System.out.println("Received login response: " + response); // Debug message
                return response.equals("SUCCESS");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error during login: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    static class TeacherFrame extends JFrame {
        private JTextArea studentListArea;
        private JTextField idField;
        private JComboBox<String> statusCombo;
        private JTextField dateField; // New field for date input
        private RoleSelectionFrame roleSelectionFrame;

        public TeacherFrame(RoleSelectionFrame roleSelectionFrame) {
            this.roleSelectionFrame = roleSelectionFrame;
            setTitle("Teacher Portal");
            setSize(600, 500); // Ensure sufficient size for all components
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    try {
                        output.writeUTF("EXIT"); // Notify the server to exit the teacher loop
                    } catch (IOException ex) {
                        System.err.println("Error sending exit command: " + ex.getMessage());
                    }
                    roleSelectionFrame.setVisible(true); // Show the RoleSelectionFrame when this frame is closed
                }
            });

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            studentListArea = new JTextArea();
            studentListArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(studentListArea);

            JPanel inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.gridy = 0;
            inputPanel.add(new JLabel("Student ID:"), gbc);

            gbc.gridx = 1;
            idField = new JTextField(10); // Minimized size
            inputPanel.add(idField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            inputPanel.add(new JLabel("Status:"), gbc);

            gbc.gridx = 1;
            statusCombo = new JComboBox<>(new String[]{"Present", "Absent"});
            inputPanel.add(statusCombo, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            inputPanel.add(new JLabel("Date (YYYY-MM-DD):"), gbc);

            gbc.gridx = 1;
            dateField = new JTextField(10); // Minimized size
            inputPanel.add(dateField, gbc);

            JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 10, 10)); // Grid layout for buttons
            JButton markBtn = new JButton("Mark Attendance");
            JButton refreshBtn = new JButton("Refresh List");
            JButton backBtn = new JButton("Back");
            JButton summaryBtn = new JButton("Fetch Summary");
            JButton updateAttendanceBtn = new JButton("Update Past Attendance");
            JButton addStudentBtn = new JButton("Add New Student");

            markBtn.addActionListener(e -> markAttendance());
            refreshBtn.addActionListener(e -> loadStudentList());
            backBtn.addActionListener(e -> {
                dispose();
                roleSelectionFrame.setVisible(true);
            });
            summaryBtn.addActionListener(e -> fetchAttendanceSummary());
            updateAttendanceBtn.addActionListener(e -> updatePastAttendance());
            addStudentBtn.addActionListener(e -> addNewStudent());

            buttonPanel.add(markBtn);
            buttonPanel.add(refreshBtn);
            buttonPanel.add(summaryBtn);
            buttonPanel.add(updateAttendanceBtn);
            buttonPanel.add(addStudentBtn);
            buttonPanel.add(backBtn);

            mainPanel.add(scrollPane, BorderLayout.CENTER);
            mainPanel.add(inputPanel, BorderLayout.NORTH);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            add(mainPanel);
            loadStudentList();
            setVisible(true);
        }

        private void loadStudentList() {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (!connectionActive) reconnect();

                    try {
                        System.out.println("Refresh List button clicked"); // Debug log
                        output.writeUTF("teacher");
                        String studentData = input.readUTF();
                        System.out.println("Received student list: " + studentData); // Debug log

                        SwingUtilities.invokeLater(() -> {
                            studentListArea.setText(""); // Clear the text area
                            if (studentData.equals("No students found")) {
                                studentListArea.setText(studentData);
                            } else {
                                String[] students = studentData.split("\\|");
                                for (String student : students) {
                                    String[] parts = student.split(",");
                                    if (parts.length == 2) {
                                        studentListArea.append("ID: " + parts[0] + " - " + parts[1] + "\n");
                                    }
                                }
                            }
                        });
                    } catch (IOException e) {
                        showError("Error loading student list: " + e.getMessage());
                        reconnect();
                    }
                    return null;
                }
            }.execute();
        }

        private void markAttendance() {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (!connectionActive) reconnect();

                    try {
                        System.out.println("Mark Attendance button clicked");
                        output.writeUTF("teacher");
                        output.writeUTF("MARK_ATTENDANCE");

                        int studentId = Integer.parseInt(idField.getText().trim());
                        String status = (String) statusCombo.getSelectedItem();

                        output.writeInt(studentId);
                        output.writeUTF(status);
                        System.out.println("Sent student ID: " + studentId + ", Status: " + status);

                        String response = input.readUTF();
                        System.out.println("Server response: " + response);

                        if (response.startsWith("CONFLICT:")) {
                            String existingStatus = response.substring(9);
                            int choice = JOptionPane.showConfirmDialog(
                                    TeacherFrame.this,
                                    "Attendance already marked as " + existingStatus + ". Override?",
                                    "Conflict",
                                    JOptionPane.YES_NO_OPTION);

                            output.writeUTF(choice == JOptionPane.YES_OPTION ? "yes" : "no");
                            response = input.readUTF();
                            System.out.println("Update response: " + response);
                        }

                        // Create a final copy of the response for use in the lambda
                        final String finalResponse = response;
                        SwingUtilities.invokeLater(() -> {
                            studentListArea.setText(finalResponse);
                        });

                    } catch (NumberFormatException e) {
                        SwingUtilities.invokeLater(() -> {
                            showError("Please enter a valid student ID");
                        });
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> {
                            showError("Error marking attendance: " + e.getMessage());
                        });
                        reconnect();
                    }
                    return null;
                }
            }.execute();
        }

        private void fetchAttendanceSummary() {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (!connectionActive) reconnect();

                    try {
                        System.out.println("Fetch Summary button clicked"); // Debug log
                        output.writeUTF("FETCH_SUMMARY");
                        output.writeUTF(dateField.getText().trim());
                        String response = input.readUTF();
                        System.out.println("Received summary: " + response); // Debug log

                        SwingUtilities.invokeLater(() -> {
                            studentListArea.setText(""); // Clear the text area
                            if (response.startsWith("No records found")) {
                                studentListArea.setText(response);
                            } else if (response.startsWith("ERROR_FETCHING_SUMMARY")) {
                                studentListArea.setText("Error fetching summary. Please try again.");
                            } else {
                                String[] records = response.split("\\|");
                                for (String record : records) {
                                    String[] parts = record.split(",");
                                    if (parts.length == 2) {
                                        studentListArea.append("Name: " + parts[0] + " - Status: " + parts[1] + "\n");
                                    }
                                }
                            }
                        });
                    } catch (IOException e) {
                        showError("Error fetching summary: " + e.getMessage());
                        reconnect();
                    }
                    return null;
                }
            }.execute();
        }

        private void updatePastAttendance() {
            String studentId = JOptionPane.showInputDialog(this, "Enter Student ID:");
            String date = JOptionPane.showInputDialog(this, "Enter Date (YYYY-MM-DD):");
            String status = (String) JOptionPane.showInputDialog(this, "Select Status:", "Update Attendance",
                    JOptionPane.QUESTION_MESSAGE, null, new String[]{"Present", "Absent"}, "Present");

            if (studentId != null && date != null && status != null) {
                new SwingWorker<Void, Void>() {
                    protected Void doInBackground() throws Exception {
                        try {
//                            int studentId = Integer.parseInt(studentIdStr.trim());
                            output.writeUTF("UPDATE_ATTENDANCE");
                            output.writeInt(Integer.parseInt(studentId.trim()));
                            output.writeUTF(date.trim());
                            output.writeUTF(status.trim());
                            String response = input.readUTF();
                            SwingUtilities.invokeLater(() ->
                                    studentListArea.setText(response));
//                            output.writeUTF("UPDATE_ATTENDANCE");
//                            output.writeInt(Integer.parseInt(studentId.trim()));
//                            output.writeUTF(date.trim());
//                            output.writeUTF(status.trim());
//                            String response = input.readUTF();
//                            System.out.println("Server response: " + response); // Debug message
//                            // Use a final local variable to avoid the lambda issue
//                            final String finalResponse = response;
//                            SwingUtilities.invokeLater(() -> studentListArea.setText(finalResponse)); // Display response in the text area
                        } catch (NumberFormatException e) {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(TeacherFrame.this, "Please enter a valid Student ID", "Error", JOptionPane.ERROR_MESSAGE));
                        } catch (IOException e) {
                            showError("Error updating attendance: " + e.getMessage());
                        }
                        return null;
                    }
                }.execute();
            }
        }

        private void addNewStudent() {
            String studentName = JOptionPane.showInputDialog(this, "Enter Student Name:");
            if (studentName != null) {
                new SwingWorker<Void, Void>() {
                    protected Void doInBackground() throws Exception {
                        try {
                            output.writeUTF("ADD_STUDENT");
                            output.writeUTF(studentName.trim());
                            String response = input.readUTF(); // Read server response
                            SwingUtilities.invokeLater(() ->
                                    studentListArea.setText(response));
                        } catch (IOException e) {
                            showError("Error adding new student: " + e.getMessage());
                        }
                        return null;
                    }
                }.execute();
            }
        }

        private void showMessage(String msg) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(TeacherFrame.this, msg));
        }

        private void showError(String msg) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(TeacherFrame.this, msg, "Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    static class StudentFrame extends JFrame {
        private JTextField idField;
        private JTextArea resultArea;
        private RoleSelectionFrame roleSelectionFrame;

        public StudentFrame(RoleSelectionFrame roleSelectionFrame) {
            this.roleSelectionFrame = roleSelectionFrame;
            setTitle("Student Portal");
            setSize(600, 500); // Increased size to ensure all buttons are visible
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    roleSelectionFrame.setVisible(true); // Show the RoleSelectionFrame when this frame is closed
                }
            });

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            inputPanel.add(new JLabel("Enter Student ID:"));
            idField = new JTextField(10);
            inputPanel.add(idField);

            JButton checkBtn = new JButton("Check Attendance");
            checkBtn.addActionListener(e -> checkAttendance());
            inputPanel.add(checkBtn);

            JButton backBtn = new JButton("Back");
            backBtn.addActionListener(e -> {
                dispose(); // Close the current frame
                roleSelectionFrame.setVisible(true); // Show the RoleSelectionFrame
            });
            inputPanel.add(backBtn);

            resultArea = new JTextArea();
            resultArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(resultArea);

            mainPanel.add(inputPanel, BorderLayout.NORTH);
            mainPanel.add(scrollPane, BorderLayout.CENTER);

            add(mainPanel);
            setVisible(true);
        }

        private void checkAttendance() {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (!connectionActive) reconnect();

                    try {
                        System.out.println("Check Attendance button clicked"); // Debug log
                        int studentId = Integer.parseInt(idField.getText().trim());
                        output.writeUTF("student");
                        output.writeInt(studentId);

                        String response = input.readUTF();
                        System.out.println("Received attendance records: " + response); // Debug log

                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText("");
                            if (response.equals("No records found")) {
                                resultArea.setText(response);
                            } else {
                                String[] records = response.split("\\|");
                                int total = 0, present = 0;
                                for (String record : records) {
                                    String[] parts = record.split(",");
                                    if (parts.length == 3) {
                                        resultArea.append(String.format(
                                                "%-20s %-15s %s\n",
                                                parts[0], parts[1], parts[2]));
                                        total++;
                                        if (parts[2].equalsIgnoreCase("Present")) {
                                            present++;
                                        }
                                    }
                                }
                                if (total > 0) {
                                    resultArea.append("\nAttendance Percentage: " + (present * 100 / total) + "%");
                                } else {
                                    resultArea.append("\nNo attendance records available.");
                                }
                            }
                        });
                    } catch (NumberFormatException e) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(StudentFrame.this,
                                        "Please enter a valid student ID",
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(StudentFrame.this,
                                        "Error checking attendance: " + e.getMessage(),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE));
                        reconnect();
                    }
                    return null;
                }
            }.execute();
        }
    }
}