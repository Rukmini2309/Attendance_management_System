# Attendance Management System
This project is a client-server application for managing attendance records in a school. It allows teachers to mark attendance, fetch attendance summaries, and add new students, while students can view their attendance records.
	
## Features
### Teacher Features
- **Mark Attendance**: Teachers can mark attendance for students. The teacher should enter Student ID, select Status i.e. Present/Absent from the ComboBox and click Mark Attendance button. The teacher is not required to enter date here as current system date is automatically fetched. 
- **Fetch Attendance Summary**: Teachers can view attendance summaries for a specific date. The teacher should fill in the date in the date field and click Fetch Summary button.
- **Update Past Attendance**: Teachers can update attendance records for previous dates. The teacher should enter Student ID, select Status, fill in date and click on Update Past Attendance.
- **Add New Students**: Teachers can add new students to the system. Click the add new student button. Fill in the information asked in the dialog boxes as they appear.
- **View Student List**: Teachers can view a list of all students. Click on Refresh list button to view Student list.

### Student Features
- **View Attendance Records**: Students can view their attendance records and view their attendance percentage.

## Project Structure
### Key Files
- **[src/Server.java](src/Server.java)**: Implements the server-side logic, including database operations and handling client requests.
- **[src/Client.java](src/Client.java)**: Implements the client-side GUI and communication with the server.

## Prerequisites
- **Java Development Kit (JDK)**: Version 23 or higher.
- **MySQL Database**: Ensure MySQL is installed and running.
- **MySQL Connector**: The project uses `mysql-connector-j-9.2.0`. Ensure the JAR file is downloaded and configured in the project.

## Setup Instructions
1. **Database Setup**:
   - Create a MySQL database named `school_db`.
   - Create the required tables (`users`, `students`, `attendance`) and populate them with initial data.
2. **Configure MySQL Connector**:
   - Ensure the MySQL Connector JAR file is located at the path specified in `.idea/libraries/mysql_connector_j_9_2_0.xml`.
3. **Run the Server**:
   - Open the project in your IDE.
   - Run the `Server` class located in `src/Server.java`.
4. **Run the Client**:
   - Run the `Client` class located in `src/Client.java`.

## Usage
1. **Start the Server**:
   - The server listens on port `2400` and connects to the MySQL database.
2. **Launch the Client**:
   - The client provides a GUI for teachers and students to interact with the system.
3. **Teacher Workflow**:
   - Log in as a teacher.
   - Use the provided options to mark attendance, fetch summaries, update past attendance, or add new students.
4. **Student Workflow**:
   - Log in as a student.
   - Enter your student ID to view your attendance records.

## Logging
- All server-side activities are logged in `server_logs.txt`.

## Troubleshooting
- **Database Connection Issues**:
  - Ensure the MySQL server is running and the credentials in `Server.java` are correct.
- **Client-Server Connection Issues**:
  - Ensure the server is running and listening on the correct port (`2400`).
## Team members contribution  
The team consists of 3 members. Their contributions are as follows:  

**Rukmini**:  
- **SServer.java** - Server implementation with database connectivity  
- **Database Setup** - MySQL database schema and sample data  

**Ananya**:  
- **CClient.java** - Client GUI implementation of login portal and teacher portal with Swing  

**Ayushi**:  
- **CClient.java** - Client GUI implementation of role authentication and student portal with Swing  
