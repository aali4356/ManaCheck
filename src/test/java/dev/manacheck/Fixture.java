package dev.manacheck;

import static dev.manacheck.Domain.*;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import tools.jackson.databind.json.JsonMapper;

final class Fixture {
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final Map<String, Card> CARDS = load();

  static byte[] archive() {
    try (InputStream in = Fixture.class.getResourceAsStream("/demo/oracle-cards.jsonl.gz")) {
      return Objects.requireNonNull(in).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] jsonl() {
    try {
      return new GZIPInputStream(new ByteArrayInputStream(archive())).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Map<String, Card> load() {
    Map<String, Card> cards = new HashMap<>();
    CardClassifier classifier = new CardClassifier();
    for (String line : new String(jsonl(), java.nio.charset.StandardCharsets.UTF_8).split("\\R"))
      if (!line.isBlank()) {
        var source = JSON.readTree(line);
        if (classifier.excluded(source)) continue;
        Card c = classifier.classify(source);
        cards.put(c.name(), c);
      }
    return cards;
  }

  static Card card(String name) {
    return Objects.requireNonNull(CARDS.get(name), name);
  }

  static DeckView deck(String commander, DeckEntry... entries) {
    return new DeckView(
        UUID.randomUUID(),
        "Test deck",
        1,
        card(commander),
        "general",
        "",
        List.of(entries),
        Instant.EPOCH);
  }

  static DeckEntry entry(String name, int quantity) {
    return new DeckEntry(card(name), quantity);
  }

  static String sourceLine(String name) {
    return Arrays.stream(new String(jsonl(), java.nio.charset.StandardCharsets.UTF_8).split("\\R"))
        .filter(line -> !line.isBlank() && JSON.readTree(line).path("name").asText().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
