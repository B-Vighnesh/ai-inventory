package com.stockpulse.api.dto;
import jakarta.validation.constraints.NotNull;
public record ResolveRequest(@NotNull Action action) {
    public enum Action { ACCEPT, REJECT }
}
