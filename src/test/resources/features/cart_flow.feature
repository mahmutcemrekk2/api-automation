@flow
Feature: DummyJSON Cart Flow
  End-to-end flow: Login → Get user info → Access cart → Validate data.
  Demonstrates variable chaining across multiple API calls.

  @flow @smoke
  Scenario: Login and retrieve cart details
    Given system uses "dummyjson" service
    And user is logged in as default

        # Step 1: Get all carts (limited to 1)
    When user sends "GET" request to "/carts" with query params:
      | limit | 1 |
    Then response status code should be 200
    And response should match:
      | $.carts | not empty |
      | $.total | exists    |

        # Step 2: Store first cart ID and get details
    And user stores response "$.carts[0].id" as "cartId"

    When user sends "GET" request to "/carts/{{cartId}}"
    Then response status code should be 200
    And response should match:
      | $.id       | exists    |
      | $.products | not empty |
      | $.total    | exists    |

  @flow
  Scenario: Login and get user posts
    Given system uses "dummyjson" service
    And user is logged in as default

        # Step 1: Get user's posts
    When user sends "GET" request to "/posts/user/{{userId}}"
    Then response status code should be 200
    And response should match:
      | $.posts | not empty |
      | $.total | exists    |

        # Step 2: Get first post details
    And user stores response "$.posts[0].id" as "postId"
    When user sends "GET" request to "/posts/{{postId}}"
    Then response status code should be 200
    And response should match:
      | $.title | not empty |
      | $.body  | not empty |
      | $.tags  | exists    |

        # Step 3: Get comments for this post
    When user sends "GET" request to "/posts/{{postId}}/comments"
    Then response status code should be 200

  @flow
  Scenario: Product search and add to cart flow
    Given system uses "dummyjson" service
    And user is logged in as default

        # Step 1: Search for a product
    When user sends "GET" request to "/products/search" with query params:
      | q | laptop |
    Then response status code should be 200
    And response should match:
      | $.products | not empty |
    And user stores response "$.products[0].id" as "productId"

        # Step 2: Add product to a new cart
    When user sends "POST" request to "/carts/add" with body:
            """
            {
            "userId": {{userId}},
            "products": [
            {
            "id": {{productId}},
            "quantity": 2
            }
            ]
            }
            """
    Then response status code should be 201
    And response should match:
      | $.products      | not empty |
      | $.totalProducts | exists    |
      | $.total         | exists    |

