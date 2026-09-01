# 🎓 Bayan-E-Bartar (BB)

A modern Android application for managing the **Bayan-E-Bartar educational institute**.

BB is built to make everyday school and institute management easier for **admins, teachers, students, and parents** — from attendance and announcements to report cards, class history, and an offline dictionary.

---

## ✨ What can BB do?

BB provides a different experience based on the logged-in user role.

### 👨‍💼 Admin

Admins can manage almost every part of the system:

- Manage students and teachers
- Create and edit classes
- Assign teachers to classes
- Add students to classes
- Manage attendance
- Configure grading systems
- Enter and publish report cards
- Send announcements
- View class and academic history
- Restore deleted records from trash
- Export attendance data

### 👨‍🏫 Teacher

Teachers can:

- View their assigned classes
- Record student attendance
- Manage attendance sessions
- Enter grades
- Work with report cards
- Send announcements to their classes
- View previous and completed classes

### 👨‍🎓 Student

Students can:

- Read announcements
- View published report cards
- Check previous classes and term history
- Use the built-in offline English dictionary
- Manage their profile and password

> Student accounts are also intended to be usable by parents.

---

## 🔐 Authentication

BB uses token-based authentication.

Main authentication features include:

- Login with national ID and password
- Bearer-token authentication
- Automatic session restoration
- Token expiration handling
- Automatic logout after unauthorized responses
- Forced password change when required
- Password change from profile
- Admin password reset capability
- User avatar support

Authenticated API requests include:

```http
Authorization: Bearer <token>
```

---

## 🧑‍🏫 Attendance

The attendance system supports:

- `PRESENT`
- `LATE`
- `ABSENT`

For late students, the delay duration can also be recorded.

Attendance is stored per class session, and existing attendance records can be loaded and updated.

Attendance information can also be exported through the backend as an Excel file.

---

## 📊 Report Cards

BB includes a flexible report-card system.

Features include:

- Dynamic grading components
- Custom maximum scores
- Class-specific grading configuration
- Student grade entry
- Report-card generation
- Published report-card history
- Current report-card retrieval
- Result messages
- Academic history

According to the project requirements, the total score of grading components should equal:

```text
100
```

---

## 📢 Announcements

Announcements can be sent to different audiences depending on the user's role.

Admins can send announcements to:

- Students
- Teachers
- Classes

Teachers can send announcements to their own classes.

Supported announcement features include:

- Title and message body
- Audience selection
- File attachments
- Read / unread state
- Unread announcement count
- Announcement details

---

## 📚 Offline Dictionary

BB includes an offline English dictionary, so dictionary searches do not require an internet connection.

The database is bundled with the app:

```text
app/src/main/assets/dictionary.db
```

---

## 🌙 Dark Mode

The app supports both light and dark themes.

The selected theme is saved locally and restored automatically when the app starts.

---

## 🛠 Tech Stack

BB is a native Android project written in Kotlin.

| Technology | Usage |
|---|---|
| Kotlin | Main programming language |
| Android SDK | Native Android development |
| XML Layouts | User interface |
| Material Components | UI components |
| AndroidX | Android support libraries |
| Retrofit 2 | API communication |
| OkHttp | Networking and interceptors |
| Gson | JSON serialization |
| SQLite | Offline dictionary |
| Gradle | Build system |

### Main dependencies

```text
Retrofit          2.9.0
Retrofit Gson     2.9.0
OkHttp            4.12.0
Gson              2.10.1
Core SplashScreen 1.0.1
```

---

## ⚙️ Android Configuration

```text
Application ID : ir.bayanebartar.app
Namespace      : ir.bayanebartar.app

Min SDK        : 24
Target SDK     : 36

Version Code   : 1
Version Name   : 1.0

Java Compatibility : Java 11
```

Build tools currently used by the project:

```text
Android Gradle Plugin : 9.2.1
Gradle Wrapper        : 9.4.1
```

---

## 📁 Project Structure

```text
BB/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── dictionary.db
│   │   │
│   │   ├── java/com/example/bb/
│   │   │   ├── ApiConfig.kt
│   │   │   ├── RetrofitClient.kt
│   │   │   ├── LoginActivity.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── AttendanceActivity.kt
│   │   │   ├── AnnouncementsActivity.kt
│   │   │   ├── DictionaryActivity.kt
│   │   │   ├── StudentManagementActivity.kt
│   │   │   ├── TeacherManagementActivity.kt
│   │   │   ├── ClassManagementActivity.kt
│   │   │   ├── ReportCardSetupActivity.kt
│   │   │   ├── GradeEntryActivity.kt
│   │   │   ├── ReportCardViewActivity.kt
│   │   │   └── ...
│   │   │
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── docs/
│   ├── api_contract.md
│   ├── mysql_schema.sql
│   └── requirements.md
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

---

## 🌐 Backend & API

Networking is handled mainly by:

```text
ApiConfig.kt
RetrofitClient.kt
```

The Android client communicates with PHP endpoints such as:

```text
login.php
logout.php
get_profile.php

get_students.php
add_student.php

get_teachers.php
add_teacher.php

get_classes.php
add_class.php
update_class.php

get_announcements.php
create_announcement.php

get_attendance_overview.php
get_attendance_session.php
finalize_attendance.php

get_report_config.php
get_report_cards.php
save_report_cards.php
```

There are also endpoints for:

- Password management
- Attendance export
- History
- Trash management
- Report configuration
- User management

---

## 🔧 API Configuration

The API server configuration is located here:

```text
app/src/main/java/com/example/bb/ApiConfig.kt
```

If you want to connect the app to another backend, update this file.

Example:

```kotlin
object ApiConfig {
    const val BASE_URL = "https://example.com/api/"
    const val API_HOST = "example.com"
    const val API_IP = "YOUR_SERVER_IP"
}
```

The current implementation also includes custom DNS resolution for the configured API host.

> For production environments, HTTPS is strongly recommended.

---

## 🗄 Database

A reference MySQL / MariaDB schema is available in:

```text
docs/mysql_schema.sql
```

It includes tables for concepts such as:

```text
users
student_profiles
classes
class_days
enrollments
attendance_sessions
attendance_items
announcements
report_templates
...
```

The Android app does **not** connect directly to MySQL.

All database operations are expected to go through the backend API.

---

## 📖 Documentation

More project documentation is available inside the `docs` directory:

| File | Description |
|---|---|
| `requirements.md` | Business rules and project requirements |
| `api_contract.md` | Initial backend API contract |
| `mysql_schema.sql` | MySQL / MariaDB database schema |

> The Android client currently uses PHP-style endpoints, so `RetrofitClient.kt` is the best reference for the current API implementation.

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/ali-e17/BB.git
cd BB
```

### 2. Open the project

Open the project in **Android Studio**.

Wait for Gradle sync to finish and let Android Studio download the required dependencies.

### 3. Check the API configuration

Open:

```text
app/src/main/java/com/example/bb/ApiConfig.kt
```

Make sure the configured backend server is reachable.

### 4. Build the project

Linux / macOS:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
gradlew.bat assembleDebug
```

You can also build and run the app directly from Android Studio.

### 5. Run BB

Use an Android device or emulator running **Android API 24 or newer**.

Most management features require:

- A working backend server
- A valid user account
- Network access

The offline dictionary works without the backend.

---

## 🔄 App Flow

```text
Launch App
    │
    ▼
Check Local Session
    │
    ├── Valid Token ───────► Main Dashboard
    │
    ├── Password Change ───► Change Password
    │
    └── No Session ────────► Login
                                 │
                                 ▼
                            Backend API
                                 │
                                 ▼
                           Save Auth Token
                                 │
                                 ▼
                         Role-Based Dashboard
```

---

## 👥 Role-Based Dashboard

```text
ADMIN
├── Student Management
├── Teacher Management
├── Class Management
├── Attendance
├── Report Cards
└── Announcements

TEACHER
├── Attendance
├── Report Cards
├── Announcements
└── Teaching History

STUDENT
├── Announcements
├── Report Cards
├── Term History
└── Offline Dictionary
```

---

## 📌 Important Business Rules

Some important rules defined by the project:

- The system has one administrator.
- Users can be `ADMIN`, `TEACHER`, or `STUDENT`.
- Initial passwords are based on the user's national ID.
- Users can change their password.
- A student can have at most one active class at a time.
- Previous class memberships remain in history.
- Every class has one teacher.
- Completing a class must not delete its historical records.
- Late attendance stores the delay duration.
- Teachers can send announcements only to their own classes.
- Grading components are configurable.
- The total grading score must equal `100`.
- Published report cards and previous-term records are preserved.

For full details, see:

```text
docs/requirements.md
```

---

## 🤝 Contributing

Contributions are welcome.

If you want to improve the project:

1. Fork the repository
2. Create a new branch
3. Make your changes
4. Commit your work
5. Push your branch
6. Open a Pull Request

Example:

```bash
git clone https://github.com/YOUR_USERNAME/BB.git
cd BB

git checkout -b feature/your-feature

git add .
git commit -m "Add your feature"
git push origin feature/your-feature
```

When opening a Pull Request:

- Keep the change focused
- Follow the existing Kotlin / Android structure
- Do not commit credentials or private server information
- Test the affected screens
- Explain what you changed and why

---

## 💡 Project Goal

The goal of BB is to provide a simple and practical Android experience for handling the everyday workflow of an educational institute — without making teachers, students, or administrators deal with unnecessary complexity.

---

## 📦 Repository

```text
https://github.com/ali-e17/BB
```

---

Made for **Bayan-E-Bartar** 🎓
