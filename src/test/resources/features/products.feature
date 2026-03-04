@products
Feature: DummyJSON Products
  Product CRUD operations and listing tests.

  @products
  Scenario: Get a single product by ID
    Given system uses "dummyjson" service
    When user sends "GET" request to "/products/1"
    Then response status code should be 200
    And the response "$.id" should be 1
    And response should match:
      | $.title       | not empty |
      | $.description | not empty |
      | $.price       | exists    |
      | $.category    | not empty |
      | $.brand       | exists    |

  @products
  Scenario: Search products
    Given system uses "dummyjson" service
    When user sends "GET" request to "/products/search" with query params:
      | q | phone |
    Then response status code should be 200
    And response should match:
      | $.products | not empty |
      | $.total    | exists    |

  @products
  Scenario: Add a new product
    Given system uses "dummyjson" service
    When user sends "POST" request to "/products/add" with body:
            """
            {
                "title": "Test Automation Product",
                "description": "Created by API automation framework",
                "price": 99.99,
                "category": "test"
            }
            """
    Then response status code should be 201
    And the response "$.title" should be "Test Automation Product"
    And the response "$.price" should be "99.99"

  @products
  Scenario: Update a product
    Given system uses "dummyjson" service
    When user sends "PUT" request to "/products/1" with body:
            """
            {
                "title": "Updated Product Title"
            }
            """
    Then response status code should be 200
    And the response "$.title" should be "Updated Product Title"

  @products
  Scenario: Delete a product
    Given system uses "dummyjson" service
    When user sends "DELETE" request to "/products/1"
    Then response status code should be 200
    And response should match:
      | $.isDeleted | exists |
      | $.deletedOn | exists |

  @products @negative
  Scenario: Add a product with missing required fields
    Given system uses "dummyjson" service
    When user sends "POST" request to "/products/add" with body:
            """
            {}
            """
    Then response status code should be 201
    And response should match:
      | $.id | exists |

  @smoke
  Scenario: last
    Given system uses "dummyjson" service
    When user sends "GET" request to "/carts"
    Then response status code should be 200
    And response should match:
      | $.carts | exists |
      | $.total | exists |
      | $.skip  | exists |
      | $.limit | exists |

  @smoke
  Scenario: defded
    Given system uses "dummyjson" service
    When user sends "GET" request to "/recipes"
    Then response status code should be 200
    And response should match:
      | $.limit | exists |
