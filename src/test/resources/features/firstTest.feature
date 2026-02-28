@smoke
Feature: FirstTest
    Auto-generated test scenario

    @smoke
    Scenario: test mi
        Given system uses "dummyjson" service

        When user sends "GET" request to "/products"
        Then response status code should be 200
        And response should match:
            | $.products           | exists     |
            | $.total              | exists     |
            | $.skip               | exists     |
            | $.limit              | exists     |
