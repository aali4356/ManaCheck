Feature: Commander deck construction
  A player can save unfinished drafts and see precise construction findings.
  The commander counts toward the 100-card total. Role targets are advice.

  Background:
    Given the fixture catalog contains no saved decks

  Scenario Outline: Count exactly <total> cards including the commander
    Given my commander is "Ezuri, Renegade Leader"
    And my main deck contains:
      | card   | quantity  |
      | Forest | <forests> |
    When I check the construction rules
    Then the analysis counts <total> cards including the commander
    And deck completion is <complete>
    And recommendation eligibility is <eligible>
    And the construction error codes are "<errors>"

    Examples:
      | total | forests | complete | eligible | errors     |
      | 99    | 98      | false    | true     | CARD_COUNT |
      | 100   | 99      | true     | true     | none       |
      | 101   | 100     | false    | false    | CARD_COUNT |

  Scenario: A blue spell is outside a green commander's color identity
    Given my commander is "Ezuri, Renegade Leader"
    And my main deck contains:
      | card         | quantity |
      | Forest       | 98       |
      | Counterspell | 1        |
    When I check the construction rules
    Then the analysis counts 100 cards including the commander
    And deck completion is false
    And recommendation eligibility is false
    And the construction error codes are "COLOR_IDENTITY"
    And "Counterspell" is identified by the "COLOR_IDENTITY" finding

  Scenario: The back face contributes to a card's color identity
    Given my commander is "Losheel, Clockwork Scholar"
    And my main deck contains:
      | card                                   | quantity |
      | Plains                                 | 98       |
      | Archangel Avacyn // Avacyn, the Purifier | 1        |
    When I check the construction rules
    Then deck completion is false
    And the construction error codes are "COLOR_IDENTITY"
    And "Archangel Avacyn // Avacyn, the Purifier" is identified by the "COLOR_IDENTITY" finding

  Scenario Outline: Check the singleton allowance with <copies> copies of an ordinary nonbasic card
    Given my commander is "Ezuri, Renegade Leader"
    And my main deck contains:
      | card     | quantity  |
      | Forest   | <forests> |
      | Sol Ring | <copies>  |
    When I check the construction rules
    Then the analysis counts 100 cards including the commander
    And deck completion is <complete>
    And the construction error codes are "<errors>"

    Examples:
      | copies | forests | complete | errors     |
      | 1      | 98      | true     | none       |
      | 2      | 97      | false    | COPY_LIMIT |

  Scenario: Basic lands can fill all 99 main-deck slots
    Given my commander is "Ezuri, Renegade Leader"
    And my main deck contains:
      | card   | quantity |
      | Forest | 99       |
    When I check the construction rules
    Then the analysis counts 100 cards including the commander
    And deck completion is true
    And the construction error codes are "none"

  Scenario: The commander also uses its singleton allowance
    Given my commander is "Ezuri, Renegade Leader"
    And my main deck contains:
      | card                  | quantity |
      | Forest                | 98       |
      | Ezuri, Renegade Leader | 1        |
    When I check the construction rules
    Then deck completion is false
    And the construction error codes are "COPY_LIMIT"
    And "Ezuri, Renegade Leader" is identified by the "COPY_LIMIT" finding

  Scenario: Explicit any-number wording permits repeated Relentless Rats
    Given my commander is "Meren of Clan Nel Toth"
    And my main deck contains:
      | card            | quantity |
      | Forest          | 79       |
      | Relentless Rats | 20       |
    When I check the construction rules
    Then the analysis counts 100 cards including the commander
    And deck completion is true
    And the construction error codes are "none"

  Scenario Outline: Check the nine-copy exception with <copies> Nazgûl
    Given my commander is "Meren of Clan Nel Toth"
    And my main deck contains:
      | card   | quantity  |
      | Forest | <forests> |
      | Nazgûl | <copies>  |
    When I check the construction rules
    Then the analysis counts 100 cards including the commander
    And deck completion is <complete>
    And the construction error codes are "<errors>"

    Examples:
      | copies | forests | complete | errors     |
      | 9      | 90      | true     | none       |
      | 10     | 89      | false    | COPY_LIMIT |
