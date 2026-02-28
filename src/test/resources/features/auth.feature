@auth
Feature: DummyJSON Authentication
  Authentication tests for the DummyJSON API.
  Tests login with valid/invalid credentials and token-based authentication.

  @auth @smoke
  Scenario: Login with valid credentials
    Given system uses "dummyjson" service
    When user sends "POST" request to "/auth/login" with body:
      """
      {
        "username": "{{defaultUsername}}",
        "password": "{{defaultPassword}}"
      }
      """
    Then response status code should be 200
    And the response "$.username" should be "{{defaultUsername}}"
    And the response "$.email" should be "emily.johnson@x.dummyjson.com"
    And response should match:
      | $.accessToken  | not empty |
      | $.refreshToken | not empty |
      | $.id           | exists    |
      | $.firstName    | exists    |
      | $.lastName     | exists    |

  @auth
  Scenario: Login and store token for later use
    Given system uses "dummyjson" service
    When user sends "POST" request to "/auth/login" with body:
      """
      {
        "username": "{{defaultUsername}}",
        "password": "{{defaultPassword}}"
      }
      """
    Then response status code should be 200
    And user stores response "$.accessToken" as "authToken"
    And user stores response "$.id" as "userId"
    And user stores response "$.firstName" as "firstName"

  @auth @negative
  Scenario: Login with invalid credentials
    Given system uses "dummyjson" service
    When user sends "POST" request to "/auth/login" with body:
      """
      {
        "username": "{{defaultUsername}}",
        "password": "wrongpassword"
      }
      """
    Then response status code should be 400
    And the response "$.message" should be "Invalid credentials"

  @auth
  Scenario: Get authenticated user profile
    Given system uses "dummyjson" service
    # Step 1: Login
    When user sends "POST" request to "/auth/login" with body:
      """
      {
        "username": "{{defaultUsername}}",
        "password": "{{defaultPassword}}"
      }
      """
    Then response status code should be 200
    And user stores response "$.accessToken" as "authToken"
    # Step 2: Get profile with token
    Given user is authenticated with token "authToken"
    When user sends "GET" request to "/auth/me"
    Then response status code should be 200
    And the response "$.username" should be "{{defaultUsername}}"
