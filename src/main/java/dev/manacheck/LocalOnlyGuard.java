package dev.manacheck;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalOnlyGuard extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String host = request.getHeader("Host"), origin = request.getHeader("Origin");
    boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    if (host != null && !host.matches("(?i)(localhost|127\\.0\\.0\\.1)(:[0-9]{1,5})?")) {
      response.sendError(403, "Local requests only");
      return;
    }
    if (unsafe
        && ((origin != null && !origin.equals("http://" + (host == null ? "localhost" : host)))
            || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
      response.sendError(403, "Cross-origin changes are disabled");
      return;
    }
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'"
            + " https://cards.scryfall.io data:; connect-src 'self'; base-uri 'none';"
            + " frame-ancestors 'none'; form-action 'self'");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    chain.doFilter(request, response);
  }
}
