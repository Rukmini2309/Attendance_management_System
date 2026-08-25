Attendance Management System

A Java-based client-server application for managing school attendance.
The system uses Java Swing for the desktop GUI, Java Socket Programming
for client-server communication, JDBC for database connectivity, and
MySQL for persistent storage.

Features

Teacher

Mark daily attendance as PRESENT or ABSENT.

Fetch attendance summaries for a selected date.

Update attendance for previous dates.

Add new students.

Refresh and view the student list.

Student

Log in through the student portal.

View attendance records.

View attendance percentage.

Authentication

Role-based login.

Credentials are validated through the server and database.

Technology Stack

Technology                Purpose

Java / JDK 23+            Application development
Java Swing                Desktop GUI
Java Socket Programming   Client-server communication
JDBC                      Database connectivity
MySQL                     Data storage
MySQL Connector/J 9.2.0   MySQL JDBC driver
IntelliJ IDEA             Development
Git / GitHub              Version control

Architecture

Teacher / Student
       |
       v
  CClient.java
       |
       | Java Socket
       v
  SServer.java
       |
       | JDBC
       v
     MySQL

CClient.java is responsible for the user interface and sending
requests.

SServer.java accepts client connections, authenticates users,
processes attendance/student requests, performs database operations, and
returns responses.

DatabaseConfig.java centralizes database connection configuration.

CreateDatabase.java initializes/ensures the database schema and
supports attendance initialization.

InsertData.java checks/initializes sample attendance data and prints
student attendance summaries.

Project Structure

Attendance_management_System/
├── src/
│   ├── CClient.java
│   ├── SServer.java
│   ├── DatabaseConfig.java
│   ├── CreateDatabase.java
│   ├── InsertData.java
│   └── test
├── .env.example
├── .gitignore
├── mysql-connector-j-9.2.0.jar
├── Rukmini_Java_31.iml
├── server_logs.txt
└── README.md

Compiled .class files are generated locally and are intentionally
ignored by Git.

Database

The main database entities are:

users

Stores authentication information such as username, password, role, and
an associated student ID where applicable.

students

Stores student information such as student ID and name.

attendance

Stores student attendance by date and status.

Supported attendance statuses:

PRESENT
ABSENT

A student can have multiple attendance records. The application is
designed to avoid duplicate attendance records for the same student/date
combination.

Database Configuration

Database credentials are not hard-coded in the Java source.

DatabaseConfig.java reads these environment variables:

DB_HOST
DB_PORT
DB_DATABASE
DB_USERNAME
DB_PASSWORD

Example .env.example:

DB_HOST=your-host
DB_PORT=your-port
DB_DATABASE=your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password

Important: .env is not automatically read by Java

System.getenv(...) reads operating-system environment variables. A
.env file by itself is not automatically loaded.

For Windows PowerShell, set the variables before running:

$env:DB_HOST="your-host"
$env:DB_PORT="your-port"
$env:DB_DATABASE="your-database"
$env:DB_USERNAME="your-username"
$env:DB_PASSWORD="your-password"

These values are available to Java processes started from that
PowerShell session.

Never commit real database credentials.

Security

The repository ignores real environment files:

.env
.env.*
!.env.example

It also ignores compiled Java files:

*.class
out/

Only .env.example should be committed as the configuration template.

If a real database password has ever been exposed in a Git commit or
public repository, rotate that password immediately.

Prerequisites

Install/configure:

JDK 23 or higher.

A reachable MySQL database.

MySQL Connector/J 9.2.0.

IntelliJ IDEA or a Java-compatible command-line environment.

Git, if cloning from GitHub.

Check Java:

java -version
javac -version

Setup

Clone the repository:

git clone https://github.com/Rukmini2309/Attendance_management_System.git
cd Attendance_management_System

Configure the database environment variables:

$env:DB_HOST="your-host"
$env:DB_PORT="your-port"
$env:DB_DATABASE="your-database"
$env:DB_USERNAME="your-username"
$env:DB_PASSWORD="your-password"

Make sure this file is available locally:

mysql-connector-j-9.2.0.jar

Command-Line Running

1. Compile

From the project root:

javac -cp ".;mysql-connector-j-9.2.0.jar" src/*.java

2. Initialize the database

java -cp "src;mysql-connector-j-9.2.0.jar" CreateDatabase

3. Initialize/check sample data

java -cp "src;mysql-connector-j-9.2.0.jar" InsertData

InsertData connects through DatabaseConfig, ensures the schema
exists, initializes today's required attendance records, avoids
duplicate rows, and prints a student attendance summary.

4. Start the server

java -cp "src;mysql-connector-j-9.2.0.jar" SServer

Keep this terminal running.

5. Start the client

Open a second PowerShell terminal, configure the environment variables
there if necessary, then run:

java -cp "src;mysql-connector-j-9.2.0.jar" CClient

The Swing client should open.

IntelliJ IDEA Running

The main classes are:

Purpose          Main class

Database setup   CreateDatabase
Sample data      InsertData
Server           SServer
Client           CClient

For IntelliJ Run Configurations, provide:

DB_HOST=your-host
DB_PORT=your-port
DB_DATABASE=your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password

Do not save real credentials in source code or commit them to GitHub.

Application Workflow

Teacher

Start database
     |
Start SServer
     |
Start CClient
     |
Teacher login
     |
Teacher portal
     |
     +-- Mark attendance
     +-- Fetch summary
     +-- Update past attendance
     +-- Add student
     +-- View student list

Student

Start database
     |
Start SServer
     |
Start CClient
     |
Student login
     |
Student portal
     |
     +-- View attendance records
     +-- View attendance percentage

Attendance Percentage

The attendance percentage is calculated using:

Present Records
--------------------------- × 100
Total Attendance Records

For example:

Present = 8
Absent  = 2
Total   = 10

Attendance = 80%

Logging

Server-side activity is written to:

server_logs.txt

This can be used for troubleshooting and monitoring server activity.

Troubleshooting

No suitable driver found

Make sure the MySQL Connector/J JAR is in the classpath:

-cp ".;mysql-connector-j-9.2.0.jar"

Missing environment variable

Check:

echo $env:DB_HOST
echo $env:DB_PORT
echo $env:DB_DATABASE
echo $env:DB_USERNAME
echo $env:DB_PASSWORD

Set any missing value again.

Database connection failure

Verify:

Host

Port

Database name

Username

Password

Database availability

Network access

MySQL Connector/J

Client cannot connect to server

Verify:

SServer is running.

The server is listening on the expected port.

The client is configured with the correct server address/port.

No firewall/network rule is blocking the connection.

.class files are missing

Compile again:

javac -cp ".;mysql-connector-j-9.2.0.jar" src/*.java

.class files do not need to be uploaded to GitHub.

Testing / Run Commands

The src/test file contains the local commands used to compile,
initialize, and run the project. It is command documentation, not a Java
test class.

The normal order is:

1. Configure environment variables
2. Compile
3. Run CreateDatabase
4. Run InsertData
5. Run SServer
6. Run CClient

Git and GitHub

Repository:

https://github.com/Rukmini2309/Attendance_management_System

The updated implementation was developed on:

Updated-code

and can be merged into:

main

Check status

git status

Stage changes

git add .

Review staged changes

git diff --cached

Check for accidentally staged credentials

git diff --cached | Select-String "AVNS_|password|PASSWORD"

Normal Java code containing the word password is not automatically a
secret. However, an actual database password, API key, or token must
never be committed.

Commit

git commit -m "Update database integration and configuration"

Push the current branch

git push

For the first push of Updated-code:

git push -u origin Updated-code

Pull Request

Create a pull request from:

Updated-code -> main

Review the changed files before merging.

Team Contributions

Rukmini

SServer.java

Server-side implementation

Database connectivity

Database schema and setup

Sample attendance data

DatabaseConfig.java

Database integration

Ananya

CClient.java

Client-side Swing GUI

Login interface

Teacher portal

Teacher-side UI functionality

Ayushi

CClient.java

Role authentication

Student portal

Student-side UI functionality

Concepts Demonstrated

The project demonstrates:

Object-Oriented Programming

Java Swing GUI development

Java Socket Programming

Client-server architecture

JDBC

SQL and relational databases

CRUD database operations

Role-based authentication

Environment-based configuration

Exception handling

Server-side logging

Git branching

GitHub pull requests

Future Improvements

Potential improvements include:

Password hashing

Stronger authentication and authorization

Improved input validation

Automated unit/integration testing

Database connection pooling

Improved exception handling

Better network error handling

Improved UI/UX

Deployment configuration

CI/CD

More detailed attendance reports and analytics

Quick Start

# Configure database
$env:DB_HOST="your-host"
$env:DB_PORT="your-port"
$env:DB_DATABASE="your-database"
$env:DB_USERNAME="your-username"
$env:DB_PASSWORD="your-password"

# Compile
javac -cp ".;mysql-connector-j-9.2.0.jar" src/*.java

# Database setup
java -cp "src;mysql-connector-j-9.2.0.jar" CreateDatabase

# Sample data
java -cp "src;mysql-connector-j-9.2.0.jar" InsertData

# Terminal 1
java -cp "src;mysql-connector-j-9.2.0.jar" SServer

# Terminal 2
java -cp "src;mysql-connector-j-9.2.0.jar" CClient

Important: Start SServer before CClient.
