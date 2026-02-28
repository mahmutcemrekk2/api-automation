@smoke
Feature: Users
    Auto-generated test scenario

    @smoke
    Scenario: test user flow
        Given system uses "dummyjson" service

        When user sends "GET" request to "/users"
        Then response status code should be 200