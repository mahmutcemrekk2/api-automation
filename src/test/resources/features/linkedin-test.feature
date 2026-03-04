@smoke @linkedin
Feature: Linkedin-Test
  Auto-generated test scenario

  @smoke @linkedin
  Scenario: linkedin scenarios
    Given system uses "dummyjson" service
    And user is logged in as default

    When user sends "GET" request to "/products/categories"
    Then response status code should be 200
    And user stores response "$[0].slug" as "slug"
    And user stores response "$[0].slug" as global variable "globalSlugName"
    Then the response should match JSON schema example "products_categories_get.json"
