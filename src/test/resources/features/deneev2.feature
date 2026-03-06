@smoke
Feature: Deneev2
    Auto-generated test scenario

    @smoke
    Scenario: ertugrul deneme
        Given system uses "dummyjson" service

        When user sends "GET" request to "/posts"
        Then response status code should be 200
        Then the response should match JSON schema example "posts_get.json"
