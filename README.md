# 🚀 API Automation Framework & UI Dashboard

A complete, end-to-end API test automation solution built with **Java**, **Cucumber**, **REST Assured**, and a powerful **Visual UI Dashboard**. 

This framework takes BDD (Behavior-Driven Development) to the next level by providing a modern web interface to visually construct, manage, and execute Gherkin-based API tests, complete with Git integration and automatic JSON Schema validation.

---

## ✨ Key Features

- **Visual Scenario Builder**: Construct complex, chained API requests using a clean, drag-and-drop web interface without writing a single line of code.
- **Dynamic Gherkin Generation**: The UI automatically compiles your visual flows into perfectly formatted Cucumber `.feature` files.
- **Advanced Validation**: Built-in support for exact matches, Regex, JSON path extractions, and robust **JSON Schema validation**.
- **Global Variables & Chaining**: Easily extract data from one request (e.g., Auth Token or User ID) and pass it into subsequent requests.
- **Custom Step Library**: Drag and drop custom backend step definitions directly into your scenario flows.
- **Built-in Git Integration**: Create branches, commit, and push your test scenarios directly from the UI.
- **Allure Reporting**: Generate beautiful, comprehensive graphical test reports.
- **Endpoint Registry**: Pulls available API endpoints automatically, providing smart dropdowns and auto-populating mocked JSON schema examples.

---

## 🛠 Tech Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Core Language** | Java 21 | Backend execution |
| **Testing Engine** | Cucumber 7.x + JUnit 5 | BDD framework and test runner |
| **HTTP Client** | REST Assured 5.5 | API request dispatching and validation |
| **UI Server** | Java `HttpServer` (JDK 21) | Lightweight local web server for the dashboard |
| **Frontend** | HTML5, Vanilla JS, CSS3 | Clean, dark-mode native web interface |
| **Reporting** | Allure 2.29 | Graphical test execution reports |

---

## 🚀 Getting Started

### Prerequisites
- **Java 21** or later installed
- **Maven 3.8+** installed
- **Git** installed

### Installation

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/api-automation.git
cd api-automation

# Download dependencies and compile
mvn clean install -DskipTests
```

---

## 🖥 Using the UI Dashboard (Recommended)

The easiest way to write and manage tests is through the included Local UI Server.

### 1. Start the UI Server
Run the following Maven command in your terminal:
```bash
mvn exec:java -Dexec.mainClass="com.apiautomation.ui.UIServer" -Dexec.classpathScope=test
```

### 2. Open the Dashboard
Navigate to **[http://localhost:8090](http://localhost:8090)** in your web browser. 

### 3. Application Modules
* **Dashboard**: View test statistics, coverage, and recent test runs.
* **Scenario Builder**: Visually compose API tests. Add multiple "Flow Steps", configure endpoints, map global variables, apply JSON schema validation, and test the scenario in real-time. Unwanted UI sections can be removed from a step and re-added via the dropdown at the bottom of the step.
* **Existing Scenarios**: View, edit, delete, and drag-and-drop reorder your previously saved `.feature` files.
* **Git Panel**: Create new branches, stage your `.feature` file changes, and commit/push directly to your repository without leaving the UI.

---

## ✍️ Writing Tests Manually (Gherkin Syntax)

While the UI is recommended, you can still write tests manually by creating `.feature` files inside `src/test/resources/features/`.

### Simple API Test
```gherkin
Scenario: Get a product by ID
  Given system uses "dummyjson" service
  When user sends "GET" request to "/products/1"
  Then response status code should be 200
  And the response "$.title" should contain "Essence"
```

### Chained Requests (Logging in and using a token)
```gherkin
Scenario: Access protected resource using globals
  # Step 1: Login
  Given system uses "dummyjson" service
  When user sends "POST" request to "/auth/login" with body:
    """
    { "username": "emilys", "password": "emilyspass" }
    """
  Then response status code should be 200
  And user stores response "$.accessToken" as global variable "globalAuthToken"

  # Step 2: Use Token
  Given system loads global variable "globalAuthToken" as "authToken"
  And user is authenticated with token "authToken"
  When user sends "GET" request to "/auth/me"
  Then response status code should be 200
```

### Custom Utility Steps
```gherkin
Given user generates a random email as "randomEmail"
Given system waits for 2 seconds
```

---

## ▶️ Executing Tests (CLI)

If you are running tests inside a CI/CD pipeline (like Jenkins, GitHub Actions, or GitLab CI), use Maven to execute the suite.

```bash
# Run all tests
mvn clean test

# Run tests by specific Cucumber tags
mvn clean test -Dcucumber.filter.tags="@auth"
mvn clean test -Dcucumber.filter.tags="@smoke"

# Run a specific feature file
mvn clean test -Dcucumber.features="src/test/resources/features/auth.feature"
```

---

## 📊 Viewing Test Reports (Allure)

The framework automatically generates Allure results during test execution (`mvn test`).

To view the graphical HTML report:
```bash
mvn allure:serve
```
This will start a local web server and open the interactive test report in your browser.

---

## 📁 Directory Structure

```
api-automation/
├── pom.xml                          # Maven configuration
├── README.md                        # Documentation
├── src/test/
│   ├── java/com/apiautomation/
│   │   ├── client/                  # REST Assured HTTP Client wrapper
│   │   ├── context/                 # Test Context and Global State management
│   │   ├── hooks/                   # Cucumber Before/After hooks
│   │   ├── runners/                 # JUnit Suite runner
│   │   ├── steps/                   # Backend Step Definitions (CommonSteps.java)
│   │   └── ui/                      # Local UI Web Server (UIServer.java)
│   └── resources/
│       ├── features/                # Automatically generated & manual Gherkin files
│       ├── schemas/                 # JSON Schema examples for strict validation
│       ├── ui/                      # HTML/CSS/JS for the Dashboard
│       ├── endpoint-registry.json   # Centralized API endpoint definitions
│       └── environments.json        # DEV/QA environment configs
```

---

## 🤝 Contributing & Maintenance
1. Create your feature branch (`git checkout -b feature/amazing-feature` or use the UI Git Panel).
2. Ensure you have mapped valid schemas if adding new `dummyjson` endpoints.
3. Commit your changes.
4. Push to the branch and open a Pull Request.
