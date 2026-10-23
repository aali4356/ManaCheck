package dev.manacheck;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  private final DeckService decks;
  private final CatalogRepository catalog;

  public PageController(DeckService decks, CatalogRepository catalog) {
    this.decks = decks;
    this.catalog = catalog;
  }

  @GetMapping("/")
  public String library(Model model) {
    model.addAttribute("decks", decks.list());
    model.addAttribute("catalog", catalog.status());
    return "library";
  }

  @GetMapping("/decks/{id}")
  public String deck(@PathVariable UUID id, Model model) {
    model.addAttribute("deck", decks.get(id));
    model.addAttribute("analysis", decks.analysis(id));
    return "editor";
  }

  @GetMapping("/decks/{id}/compare")
  public String compare(@PathVariable UUID id, Model model) {
    model.addAttribute("deck", decks.get(id));
    model.addAttribute("revisions", decks.revisions(id));
    return "compare";
  }
}
