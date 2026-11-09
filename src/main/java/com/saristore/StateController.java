package com.saristore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the whole store state as JSON in PostgreSQL. */
@RestController
public class StateController {
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public StateController(ObjectMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initializeDatabase() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS store_state (id INTEGER PRIMARY KEY CHECK (id = 1), state JSONB NOT NULL)");
        String initialState;
        try {
            initialState = mapper.writeValueAsString(emptyState());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize store data", ex);
        }
        jdbc.update("INSERT INTO store_state (id, state) VALUES (1, CAST(? AS JSONB)) ON CONFLICT (id) DO NOTHING", initialState);
    }

    @GetMapping("/api/state")
    public synchronized JsonNode getState(HttpSession session, HttpServletResponse response) {
        requireOwner(session);
        response.setHeader("Cache-Control", "no-store, private");
        return readState();
    }

    /** Public catalog exposes only available item names and prices. */
    @GetMapping("/api/catalog")
    public synchronized List<PublicItem> getPublicCatalog() {
        JsonNode products = readState().path("products");
        List<PublicItem> catalog = new ArrayList<>();
        if (products.isArray()) {
            for (JsonNode product : products) {
                if (product.path("available").asBoolean(true) && product.path("qty").asInt(0) > 0
                        && !product.path("name").asText("").isBlank()) {
                    catalog.add(new PublicItem(product.path("name").asText(), product.path("price").decimalValue()));
                }
            }
        }
        return catalog;
    }

    @PutMapping("/api/state")
    public synchronized JsonNode saveState(@RequestBody JsonNode state, HttpSession session) {
        requireOwner(session);
        if (state == null || !state.path("products").isArray() || !state.path("buyers").isArray()
                || !state.path("sales").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State must contain products, buyers, and sales arrays");
        }
        try {
            String json = mapper.writeValueAsString(state);
            jdbc.update("INSERT INTO store_state (id, state) VALUES (1, CAST(? AS JSONB)) " +
                    "ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state", json);
            return state;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save store data", ex);
        }
    }

    private JsonNode readState() {
        String json = jdbc.queryForObject("SELECT state::text FROM store_state WHERE id = 1", String.class);
        try {
            return mapper.readTree(json);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read store data", ex);
        }
    }

    private ObjectNode emptyState() {
        ObjectNode state = JsonNodeFactory.instance.objectNode();
        state.putArray("products");
        state.putArray("buyers");
        state.putArray("sales");
        return state;
    }

    private void requireOwner(HttpSession session) {
        if (session == null || !Boolean.TRUE.equals(session.getAttribute("ownerAuthenticated"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner login required");
        }
    }

    public record PublicItem(String name, BigDecimal price) {}
}
