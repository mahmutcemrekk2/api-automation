# 🚀 API Automation Framework

A generic, reusable API test automation framework built with **Java**, **Cucumber**, **REST Assured**, and **Allure Reports**.

Inspired by [Karate](https://github.com/karatelabs/karate) but powered by **Cucumber's Gherkin syntax** for maximum flexibility and readability.

---

## 📋 Table of Contents
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Writing Tests](#-writing-tests)
- [Running Tests](#-running-tests)
- [Available Steps](#-available-steps)
- [Flow Example](#-flow-example)
- [Allure Reports](#-allure-reports)

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Programming language |
| Maven | 3.x | Build & dependency management |
| Cucumber | 7.x | BDD framework (Gherkin syntax) |
| JUnit 5 | 5.11 | Test platform |
| REST Assured | 5.5 | HTTP client for API testing |
| Allure | 2.29 | Test reporting |
| Jackson | 2.18 | JSON processing |
| JsonPath | 2.9 | JSON data extraction |

---

## 📁 Project Structure

```
api-automation/
├── pom.xml                          # Maven configuration
├── README.md
├── src/test/
│   ├── java/com/apiautomation/
│   │   ├── client/
│   │   │   └── ApiClient.java       # REST Assured wrapper
│   │   ├── config/
│   │   │   ├── DefaultHeaders.java  # Common header sets
│   │   │   ├── Environment.java     # DEV/QA environment enum
│   │   │   └── EnvironmentConfig.java # Config loader
│   │   ├── context/
│   │   │   ├── GlobalTestState.java # Cross-scenario state
│   │   │   ├── TestContext.java     # Per-scenario state
│   │   │   └── VariableResolver.java # Placeholder resolver
│   │   ├── hooks/
│   │   │   └── Hooks.java          # Before/After hooks
│   │   ├── runners/
│   │   │   └── TestRunner.java     # JUnit Suite runner
│   │   ├── steps/
│   │   │   └── CommonSteps.java    # All generic step definitions
│   │   └── utils/
│   │       ├── JsonPathUtils.java  # JsonPath utilities
│   │       └── RandomDataGenerator.java # Random data gen
│   └── resources/
│       ├── features/               # Gherkin test scenarios
│       │   ├── auth.feature
│       │   ├── products.feature
│       │   └── cart_flow.feature
│       ├── environments.json       # Environment configs
│       ├── allure.properties
│       └── logback-test.xml
```

---

## 🚀 Getting Started

### Prerequisites
- **Java 21** or later
- **Maven 3.8+**
- **Allure CLI** (optional, for report viewing)

### Installation

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/api-automation.git
cd api-automation

# Install dependencies
mvn clean install -DskipTests
```

---

## ✍️ Writing Tests

Tests are written in **Gherkin** syntax. Create `.feature` files under `src/test/resources/features/`.

### Simple API Test
```gherkin
Scenario: Get a product by ID
  Given I am using "dummyjson" service
  When I send a "GET" request to "/products/1"
  Then the response status should be 200
  And the response "$.title" should contain "Essence"
```

### With Authentication
```gherkin
Scenario: Access protected resource
  Given I am using "dummyjson" service
  When I send a "POST" request to "/auth/login" with body:
    """
    { "username": "emilys", "password": "emilyspass" }
    """
  Then the response status should be 200
  And I store "$.token" as "authToken"
  Given I am authenticated with token "authToken"
  When I send a "GET" request to "/auth/me"
  Then the response status should be 200
```

---

## ▶️ Running Tests

```bash
# Run all tests
mvn clean test

# Run by tag
mvn clean test -Dcucumber.filter.tags="@auth"
mvn clean test -Dcucumber.filter.tags="@smoke"
mvn clean test -Dcucumber.filter.tags="@flow"

# Run against a specific environment
mvn clean test -Denv=qa

# Run a specific feature file
mvn clean test -Dcucumber.features="src/test/resources/features/auth.feature"
```

---

## 📖 Available Steps

### Setup
| Step | Description |
|------|-------------|
| `Given I am using {service} service` | Set base URL |
| `Given I set header {name} to {value}` | Add a header |
| `Given I am authenticated with token {key}` | Set Bearer token |
| `Given I have stored {value} as {key}` | Store a variable |

### Requests
| Step | Description |
|------|-------------|
| `When I send a {method} request to {endpoint}` | Send request |
| `When I send a {method} request to {endpoint} with body:` | With JSON body |
| `When I send a {method} request to {endpoint} with query params:` | With query params |
| `When I send a {method} request to {endpoint} with payload:` | DataTable → JSON |
| `When I send a form {method} request to {endpoint} with params:` | Form POST |

### Validation
| Step | Description |
|------|-------------|
| `Then the response status should be {code}` | Status code check |
| `Then the response {path} should be {value}` | Exact value match |
| `Then the response {path} should contain {substring}` | Contains check |
| `Then the response {path} should match regex {regex}` | Regex match |
| `Then the response should contain the following fields:` | Bulk field checks |
| `Then the response should match JSON schema {file}` | Schema validation |

### Data Storage
| Step | Description |
|------|-------------|
| `And I store {jsonPath} as {key}` | Save response field |
| `And I store response body as {key}` | Save full body |
| `And I store the following values from the response:` | Bulk save |

### Variables
Use `{{variableName}}` or `${variableName}` in endpoints, bodies, and headers to reference stored values.

---

## 🔄 Flow Example

```gherkin
Scenario: Login → Search → Add to Cart
  # 1. Login and save token
  Given I am using "dummyjson" service
  When I send a "POST" request to "/auth/login" with body:
    """
    { "username": "emilys", "password": "emilyspass" }
    """
  And I store "$.token" as "authToken"
  And I store "$.id" as "userId"

  # 2. Search for a product
  When I send a "GET" request to "/products/search" with query params:
    | q | laptop |
  And I store "$.products[0].id" as "productId"

  # 3. Add to cart using saved variables
  Given I am authenticated with token "authToken"
  When I send a "POST" request to "/carts/add" with body:
    """
    {
      "userId": {{userId}},
      "products": [{ "id": {{productId}}, "quantity": 2 }]
    }
    """
  Then the response status should be 201
```

---

## 📊 Allure Reports

```bash
# Generate and open Allure report
mvn allure:serve

# Or generate report only
mvn allure:report
# Report will be at: target/site/allure-maven-plugin/index.html
```

---

## 📝 License

This project is open source and available under the [MIT License](LICENSE).

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
