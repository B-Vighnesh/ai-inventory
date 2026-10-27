package com.stockpulse.api.dto;
import com.stockpulse.enums.Action;
import jakarta.validation.constraints.NotNull;
public record ResolveRequest(@NotNull Action action) {
}
