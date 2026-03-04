@smoke
Feature: Linkedin Test
    Auto-generated test scenario

    @smoke
    Scenario: linkedin test
        Given system uses "dummyjson" service
        And user is logged in as default

        When user sends "GET" request to "/posts/tags"
        Then response status code should be 200
        Then the response should match JSON schema example "posts_tags_get.json"
