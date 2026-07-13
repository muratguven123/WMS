package com.wms.inbound.service.strategy;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.ReceiptItem;

import java.util.List;
import java.util.Set;

final class ZoneMatchSupport {

    private ZoneMatchSupport() {
    }

    static String determineRequiredZoneType(String productCode) {
        if (productCode == null) {
            return "STANDARD";
        }
        String upper = productCode.toUpperCase();
        if (upper.contains("COLD") || upper.contains("SOGUK") || upper.contains("SOĞUK")) {
            return "COLD_ROOM";
        }
        if (upper.contains("HAZ") || upper.contains("KIMYASAL") || upper.contains("KİMYASAL")) {
            return "HAZARDOUS";
        }
        if (upper.contains("BULK") || upper.contains("HACIMLI") || upper.contains("HACİMLİ")) {
            return "BULK";
        }
        return "STANDARD";
    }

    static boolean matchesZone(StorageLocationResponse location, String requiredZoneType) {
        Set<String> aliases = zoneAliases(requiredZoneType);
        return aliases.stream().anyMatch(alias ->
                alias.equalsIgnoreCase(location.zoneType())
                        || alias.equalsIgnoreCase(location.zoneCode()));
    }

    static Set<String> zoneAliases(String requiredZoneType) {
        return switch (requiredZoneType.toUpperCase()) {
            case "COLD_ROOM" -> Set.of("COLD_ROOM", "COLD_ZONE", "COLD");
            case "HAZARDOUS" -> Set.of("HAZARDOUS", "HAZMAT_ZONE", "HAZMAT");
            case "BULK" -> Set.of("BULK", "BULK_ZONE");
            case "STANDARD" -> Set.of("STANDARD", "STD", "STD_ZONE");
            default -> Set.of(requiredZoneType);
        };
    }

    static List<StorageLocationResponse> filterByZone(
            List<StorageLocationResponse> candidates, ReceiptItem item) {

        String requiredZoneType = determineRequiredZoneType(item.getProductCode());

        List<StorageLocationResponse> matched = candidates.stream()
                .filter(loc -> matchesZone(loc, requiredZoneType))
                .toList();

        if (!matched.isEmpty() || "STANDARD".equalsIgnoreCase(requiredZoneType)) {
            return matched;
        }

        return candidates.stream()
                .filter(loc -> matchesZone(loc, "STANDARD"))
                .toList();
    }
}
