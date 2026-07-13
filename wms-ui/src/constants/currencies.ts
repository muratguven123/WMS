export const CURRENCIES = ["TRY", "USD", "EUR", "GBP", "AED", "SAR", "JPY", "CHF", "CNY"] as const;

export type CurrencyCode = (typeof CURRENCIES)[number];
