# Translation Lookup & User Language Preference Flow

This document describes the sequence of language resolution and lookup inside `wms-localization-service`'s `TranslationLookupService`.

## Sequence Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as Operator / UI
    participant Core as wms-core-service
    participant Localize as wms-localization-service (TranslationLookupService)
    
    User->>Core: Login (username, password)
    Note over Core: KeycloakAuthService resolves user preferences
    Core-->>User: TokenResponse (accessToken, preferredLanguage, preferredTimezone)
    
    Note over User: UI stores preferredLanguage in local state
    
    User->>Core: API Request (e.g. GET /api/reports/stock) with HEADER preferredLanguage
    Core->>Localize: getLabel(keyCode, locale)
    Note over Localize: normalizedLocale = locale.toLowerCase()
    
    alt Locale translation exists in DB
        Localize-->>Core: Translation value (e.g. "Kalite Kontrol")
    else Fallback to system default language
        Note over Localize: Query active default language (e.g. isDefault=true)
        Localize-->>Core: Default translation value
    else Translation not found in either
        Note over Localize: Fire MissingTranslationEvent
        Localize-->>Core: Returns keyCode (fallback)
    end
    
    Core-->>User: API Response (translated strings)
```

## Preference Fallback Resolution

At login or token refresh, if the user's `preferredLanguage` is `null`:
1. The backend inspects the user's assigned `UserAccess` records to locate the first non-null `Location`.
2. Resolves the `Country` entity linked to the `Location` via its `Region`.
3. Maps the country's `isoCode` (e.g. `TR`, `US`, `DE`) to the corresponding ISO 639-1 language code (e.g. `tr`, `en`, `de`).
4. If no locations or country ISO codes are found, fallbacks to `en`.
