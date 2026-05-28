# 🚀 Download Manager - Beginner's Guide

Welcome to the **Download Manager** project! This guide is designed for beginners who know the basics of Java but are new to Maven, JavaFX, or complex project structures.

---

## 📂 1. Project Navigation (Where is everything?)

When you open this project, you'll see a structure that follows the standard Java Maven layout. Here is the breakdown:

### Root Directory
*   **`pom.xml`**: This is the "heart" of a Maven project. It lists all the libraries (dependencies) the project needs (like JavaFX and MySQL) and tells Maven how to build the app.
*   **`.env`**: (You might need to create this) A hidden file used to store sensitive information like your Database password so it's not hardcoded in the Java files.

### The `src` Folder (Source Code)
All your code lives in `src/main/java`.
*   **`download.manager`**: The base package.
    *   **`Main.java`**: The starting point of the application.
    *   **`config/`**: Contains configuration files (Database connection, Settings).
    *   **`controller/`**: The "brain" of the UI. It handles button clicks and updates the screen.
    *   **`model/`**: Defines what a "Download" looks like (URL, file name, progress).
    *   **`service/`**: Contains the logic for actually downloading files from the internet.
    *   **`storage/`**: Handles saving and loading data from the MySQL database (DAO).
    *   **`view/`**: Sets up the JavaFX window.

### The `resources` Folder
Lives in `src/main/resources`.
*   **`fxml/`**: Contains `View.fxml`, which is an XML-like file that defines the layout of the UI (where buttons and text fields are).
*   **`css/`**: Contains `dark.css` for styling the application (colors, fonts).

---

## 🛠 2. Prerequisites

Before running the code, ensure you have:
1.  **Java 21** installed.
2.  **Maven** installed.
3.  **MySQL Server** running.

---

## ⚙️ 3. Setup (Crucial Step!)

This project uses a database to remember your downloads. You need to tell the app how to connect to it.

1.  Create a file named `.env` in the root folder (next to `pom.xml`).
2.  Add the following lines (replace with your MySQL details):
    ```env
    DB_URL=jdbc:mysql://localhost:3306/your_database_name
    DB_USER=root
    DB_PASSWORD=your_password
    ```

---

## 🏃 4. How to Run the Project

Since this is a Maven project, you don't run it by just clicking a "play" button on a single file. You use Maven commands.

### Using the Terminal
Open your terminal in the project folder and type:
```bash
mvn javafx:run
```

**What does this command do?**
1.  **`mvn`**: Calls the Maven tool.
2.  **`javafx:run`**: A special plugin command that handles all the complex JavaFX module settings for you and starts the app from `Main.java`.

---

## 🔍 5. File-by-File Explanation

| File | Purpose |
| :--- | :--- |
| **`Main.java`** | The entry point. It simply launches the JavaFX application. |
| **`DB.java`** | Manages the connection to your MySQL database using the credentials in your `.env` file. |
| **`SettingsManager.java`** | Remembers your preferences, like where you want to save files and if you prefer Dark Mode. |
| **`DownloadController.java`** | Connects the UI (buttons) to the logic. When you click "Start", this file tells the `Downloader` to begin. |
| **`Download.java`** | A simple class (Model) that holds data about a single download (ID, URL, Progress). |
| **`Downloader.java`** | The "worker". It runs in the background and fetches the file bytes from the internet. |
| **`DownloadInfo.java`** | Manages the state of a download (is it paused? is it running?) and splits the task into threads. |
| **`DAO.java`** | (Data Access Object) The only file that talks directly to the database to Save, Update, or Delete download records. |
| **`View.java`** | Loads the FXML layout and shows the actual window on your screen. |
| **View.fxml** | The "blueprint" of the UI. You can open this with a tool called **Scene Builder** to drag and drop buttons. |

---

## 🧠 6. Deep Dive: How the Code Works

Understanding how these files talk to each other is the key to mastering this project.

### A. The MVC Pattern (Architecture)
This project follows the **Model-View-Controller** design pattern:
*   **Model (`Download.java`)**: Just data. It doesn't know about the UI or the internet.
*   **View (`View.fxml`)**: Just the look. It's like a drawing of the app.
*   **Controller (`DownloadController.java`)**: The glue. When you click a button in the View, the Controller updates the Model and triggers the logic.

### B. Database & Secrets (`DB.java` & `.env`)
*   **Singleton Pattern**: The `DB.java` file uses a "Singleton" pattern. This means no matter how many times you ask for a connection, it only creates **one** and shares it everywhere. This saves memory and prevents database errors.
*   **Automatic Setup**: When you first run the app, `DB.java` runs a `CREATE TABLE IF NOT EXISTS` command. You don't need to manually create the database tables!
*   **Dotenv**: We use `io.github.cdimascio.dotenv` to read the `.env` file. This is a security best practice—never put your passwords directly inside your Java code.

### C. The Mechanics of a Download (`Downloader.java` & `DownloadInfo.java`)
This is the most complex part. Here is what happens when you click "Start":
1.  **The Handshake**: `DownloadInfo` sends a "GET" request to the URL just to ask "How big is this file?".
2.  **The Split**: It calculates how to divide the file into chunks. (Currently set to 1 thread for simplicity, but designed for many).
3.  **The HTTP Range Header**: This is the "magic" of resuming. Instead of asking for the whole file, the code says: *"Give me bytes 500,000 to 1,000,000"*. If you pause at byte 500,000, the next time it starts, it knows exactly where to pick up.
4.  **RandomAccessFile**: Normal Java file writing starts at the beginning and goes to the end. `RandomAccessFile` allows the code to "jump" to any position in the file and write there. This is essential for multi-threaded downloads where different parts are finished at different times.

### D. Why the UI doesn't "Freeze" (Threading)
If you ran the download on the "Main Thread", you wouldn't be able to move the window or click "Pause" because the CPU would be 100% busy fetching data. 
*   We use a **Background Thread** (`new Thread(task).start()`). 
*   The UI stays responsive while the "worker" thread does the heavy lifting.

### E. Data Binding (Automatic Progress)
In `DownloadController.java`, you'll see lines like `progressBar.progressProperty().bind(...)`. 
*   In basic Java, you might think you need a loop that says `progressBar.setValue(newVal)` every second.
*   In JavaFX, we "bind" the progress bar to the `Download` model. Whenever the `Downloader` updates the progress number in the background, the UI **automatically** detects the change and moves the bar.

---

## 💡 7. Pro Tips for Beginners

*   **Case Sensitivity**: Java is case-sensitive. `Download` and `download` are different!
*   **Maven Lifecycle**: If things get weird, try `mvn clean`. This deletes the `target` folder (temporary build files) and lets you start fresh.
*   **Dependencies**: If you want to add a new library, you add it to the `<dependencies>` section in `pom.xml`.
*   **Threads**: This app uses "Background Threads". This is why the UI doesn't freeze while a file is downloading!

Happy coding! 👨‍💻👩‍💻

