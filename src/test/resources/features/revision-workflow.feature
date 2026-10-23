Feature: Immutable deck revision history
  Saving a draft publishes a complete snapshot, including its commander and metadata.
  Later edits preserve earlier snapshots, and an out-of-date editor cannot overwrite them.

  Background:
    Given the fixture catalog contains no saved decks
    And my commander is "Meren of Clan Nel Toth"
    And I save a revision with theme "graveyard", note "First list" and cards:
      | card     | quantity |
      | Forest   | 10       |
      | Sol Ring | 1        |
      | Swamp    | 5        |

  Scenario: A later save preserves the created and populated snapshots
    When I save a new revision with commander "Ezuri, Renegade Leader", theme "general", note "New leader and list" and cards:
      | card      | quantity |
      | Forest    | 9        |
      | Cultivate | 1        |
    Then the current revision is 3
    And revision 1 keeps its original snapshot
    And revision 2 keeps its original snapshot
    And revision 2 contains exactly:
      | card     | quantity |
      | Forest   | 10       |
      | Sol Ring | 1        |
      | Swamp    | 5        |
    And revision 2 has commander "Meren of Clan Nel Toth", theme "graveyard" and note "First list"
    And revision 3 contains exactly:
      | card      | quantity |
      | Forest    | 9        |
      | Cultivate | 1        |
    And revision 3 has commander "Ezuri, Renegade Leader", theme "general" and note "New leader and list"

  Scenario: Compare additions removals and quantity changes in both directions
    When I save a revision with theme "graveyard", note "Adjusted list" and cards:
      | card      | quantity |
      | Forest    | 9        |
      | Cultivate | 1        |
      | Swamp     | 5        |
    Then comparing revision 2 to revision 3 shows exactly:
      | card      | before | after | delta |
      | Cultivate | 0      | 1     | 1     |
      | Forest    | 10     | 9     | -1    |
      | Sol Ring  | 1      | 0     | -1    |
    And comparing revision 3 to revision 2 shows exactly:
      | card      | before | after | delta |
      | Cultivate | 1      | 0     | -1    |
      | Forest    | 9      | 10    | 1     |
      | Sol Ring  | 0      | 1     | 1     |
    And comparing revision 3 to itself shows no differences

  Scenario: Changing the commander appears as one removal and one addition
    When I save a new revision with commander "Ezuri, Renegade Leader", theme "graveyard", note "Changed commander" and cards:
      | card     | quantity |
      | Forest   | 10       |
      | Sol Ring | 1        |
      | Swamp    | 5        |
    Then comparing revision 2 to revision 3 shows exactly:
      | card                  | before | after | delta |
      | Ezuri, Renegade Leader | 0      | 1     | 1     |
      | Meren of Clan Nel Toth | 1      | 0     | -1    |

  Scenario: Reject a stale editor without creating a partial revision
    When I save a revision with theme "graveyard", note "Accepted edit" and cards:
      | card   | quantity |
      | Forest | 20       |
    And I try to save expecting revision 2 with cards:
      | card     | quantity |
      | Sol Ring | 2        |
    Then the save is rejected with status 409 and code "STALE_REVISION"
    And the current revision is 3
    And the revision history is unchanged by the rejected save
    And revision 2 keeps its original snapshot
    And revision 3 keeps its original snapshot
    And revision 3 contains exactly:
      | card   | quantity |
      | Forest | 20       |

  Scenario Outline: A published snapshot rejects attempts to <change>
    When I try to "<change>" in published revision 2
    Then PostgreSQL rejects the change because the snapshot is immutable
    And the current revision is 2
    And revision 2 keeps its original snapshot

    Examples:
      | change                 |
      | rewrite its note       |
      | delete the snapshot    |
      | change a card quantity |
      | remove a card          |
      | add a card             |
