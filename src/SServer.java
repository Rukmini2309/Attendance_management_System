import java.io.*;
import java.net.*;
import java.sql.*;

public class SServer {
    private static final int PORT = 2400;
    private static Connection connection;

    public static void main(String[] args) {
        initializeDatabase();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/school_db", "root", "jklmnopqr");
            System.out.println("Database connection established");
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static synchronized void logActivity(String message) {
        try (FileWriter fw = new FileWriter("server_logs.txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
        } catch (IOException e) {
            System.err.println("Error logging activity: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private DataInputStream input;
        private DataOutputStream output;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                input = new DataInputStream(clientSocket.getInputStream());
                output = new DataOutputStream(clientSocket.getOutputStream());

                while (true) { // Keep the connection alive for multiple operations
                    String userType = input.readUTF();

                    if (userType.equalsIgnoreCase("LOGIN")) {
                        handleLogin(); // Handle login requests
                    } else if (userType.equalsIgnoreCase("teacher")) {
                        handleTeacher();
                    } else if (userType.equalsIgnoreCase("student")) {
                        handleStudent();
                    } else {
                        output.writeUTF("INVALID_USER_TYPE");
                    }
                }
            } catch (IOException | SQLException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                closeResources();
                System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            }
        }

        private void handleLogin() throws IOException, SQLException {
            String role = input.readUTF();
            String username = input.readUTF();
            String password = input.readUTF();
            System.out.println("Login attempt: Role=" + role + ", Username=" + username); // Debug message

            String sql = "SELECT * FROM users WHERE role = ? AND username = ? AND password = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, role);
                stmt.setString(2, username);
                stmt.setString(3, password);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        output.writeUTF("SUCCESS");
                        System.out.println("Login successful for user: " + username); // Debug message
                    } else {
                        output.writeUTF("FAILURE");
                        System.out.println("Invalid credentials for user: " + username); // Debug message
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error during login: " + e.getMessage());
                output.writeUTF("ERROR");
            }
        }

        private void handleTeacher() throws IOException, SQLException {
            while (true) {
                try {
                    String command = input.readUTF();
                    System.out.println("Received teacher command: " + command); // Debug log
                    switch (command) {
                        case "FETCH_SUMMARY":
                            fetchAttendanceSummary();
                            break;
                        case "MARK_ATTENDANCE":
                            int studentId = input.readInt();
                            String attendanceStatus = input.readUTF();
                            processAttendance(studentId, attendanceStatus);
                            break;
                        case "UPDATE_ATTENDANCE":  // Add this case
                            updatePastAttendance();
                            break;
                        case "ADD_STUDENT":  // Add this case
                            addNewStudent();
                            break;
                        case "teacher":
                            sendStudentList();
                            break;
                        case "EXIT":
                            System.out.println("Teacher exited."); // Debug log
                            return;
                        default:
                            System.out.println("Invalid teacher command: " + command); // Debug log
                            output.writeUTF("INVALID_COMMAND");
                    }
                } catch (IOException e) {
                    System.err.println("Error handling teacher command: " + e.getMessage()); // Debug log
                    break; // Exit the loop if there's an error
                }
            }
        }

        private void updatePastAttendance() throws IOException, SQLException {
            int studentId = input.readInt();
            String date = input.readUTF();
            String status = input.readUTF();
            String sql = "UPDATE attendance SET status = ? WHERE student_id = ? AND date = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                stmt.setInt(2, studentId);
                stmt.setString(3, date);
                int rows = stmt.executeUpdate();
                output.writeUTF(rows > 0 ? "Attendance updated successfully" : "Failed to update attendance");
            } catch (SQLException e) {
                System.err.println("Error updating attendance: " + e.getMessage());
                output.writeUTF("Error updating attendance");
            }
        }

        private void addNewStudent() throws IOException, SQLException {
            String studentName = input.readUTF();
            String sql = "INSERT INTO students (name) VALUES (?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, studentName);
                int rows = stmt.executeUpdate();
                String response = rows > 0 ? "Student added successfully: " + studentName : "Failed to add student";
                output.writeUTF(response); // Send response to client
                System.out.println(response); // Debug message
            } catch (SQLException e) {
                String error = "Error adding new student: " + e.getMessage();
                System.err.println(error);
                output.writeUTF(error); // Send error response to client
            }
        }

        private void fetchAttendanceSummary() throws IOException, SQLException {
            String date = input.readUTF();
            String sql = "SELECT s.name, a.status FROM attendance a " +
                    "JOIN students s ON a.student_id = s.id " +
                    "WHERE a.date = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, date);
                try (ResultSet rs = stmt.executeQuery()) {
                    StringBuilder response = new StringBuilder();
                    while (rs.next()) {
                        response.append(rs.getString("name")).append(",")
                                .append(rs.getString("status")).append("|");
                    }
                    if (response.length() == 0) {
                        response.append("No records found for the date: ").append(date);
                    }
                    output.writeUTF(response.toString());
                    logActivity("Teacher fetched attendance summary for date: " + date);
                }
            } catch (SQLException e) {
                System.err.println("Error fetching attendance summary: " + e.getMessage());
                output.writeUTF("ERROR_FETCHING_SUMMARY");
            }
        }

        private void sendStudentList() throws SQLException, IOException {
            System.out.println("Sending student list to client..."); // Debug log
            StringBuilder response = new StringBuilder();
            String query = "SELECT id, name FROM students";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    response.append(rs.getInt("id")).append(",")
                            .append(rs.getString("name")).append("|");
                }

                if (response.length() == 0) {
                    response.append("No students found");
                }

                output.writeUTF(response.toString());
                System.out.println("Student list sent to client."); // Debug log
            } catch (SQLException e) {
                System.err.println("Error fetching student list: " + e.getMessage()); // Debug log
                output.writeUTF("ERROR_FETCHING_STUDENT_LIST");
            }
        }

        private synchronized void processAttendance(int studentId, String attendanceStatus)
                throws SQLException, IOException {
            String checkSql = "SELECT status FROM attendance WHERE student_id = ? AND date = CURDATE()";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setInt(1, studentId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        String existingStatus = rs.getString("status");
                        output.writeUTF("CONFLICT:" + existingStatus);

                        // Normalize decision to uppercase
                        String decision = input.readUTF().toUpperCase();

                        if ("YES".equals(decision)) {
                            updateAttendance(studentId, attendanceStatus);
                            output.writeUTF("UPDATED"); // Single success response
                        } else {
                            output.writeUTF("CANCELLED");
                        }
                    } else {
                        insertAttendance(studentId, attendanceStatus);
                    }
                }
            } catch (SQLException e) {
                String error = "Error processing attendance: " + e.getMessage();
                System.err.println(error);
                output.writeUTF(error);
            }
        }

        private void updateAttendance(int studentId, String status) throws SQLException, IOException {
            String sql = "UPDATE attendance SET status = ? WHERE student_id = ? AND date = CURDATE()";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, status);
                stmt.setInt(2, studentId);
                stmt.executeUpdate(); // Don't send response here - handled in processAttendance
            }
        }
        private void insertAttendance(int studentId, String status) throws SQLException, IOException {
            String sql = "INSERT INTO attendance (student_id, date, status) VALUES (?, CURDATE(), ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, studentId);
                stmt.setString(2, status);
                int rows = stmt.executeUpdate();
                String response = rows > 0 ? "Attendance recorded successfully for Student ID: " + studentId
                        : "Failed to record attendance";
                output.writeUTF(response); // Send response to client
                System.out.println(response); // Debug message
            }
        }

        private void handleStudent() throws IOException, SQLException {
            int studentId = input.readInt();

            String sql = "SELECT s.name, a.date, a.status " +
                    "FROM attendance a " +
                    "JOIN students s ON a.student_id = s.id " +
                    "WHERE a.student_id = ? " +
                    "ORDER BY a.date DESC";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    StringBuilder response = new StringBuilder();
                    while (rs.next()) {
                        response.append(rs.getString("name")).append(",")
                                .append(rs.getString("date")).append(",")  // Use getString instead of getDate
                                .append(rs.getString("status")).append("|");
                 }

                    if (response.length() == 0) {
                        response.append("No records found");
                    }

                    output.writeUTF(response.toString());
                }
            } catch (SQLException e) {
                System.err.println("Error fetching attendance records: " + e.getMessage());
                output.writeUTF("ERROR_FETCHING_RECORDS");
            }
        }

        private void closeResources() {
            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}