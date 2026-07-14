package com.wms.events;

public final class StompDestinations {

    private StompDestinations() {
    }

    public static String stock(Long companyId, Long locationId) {
        return "/topic/company." + companyId + ".location." + locationId + ".stock";
    }

    public static String locations(Long companyId, Long locationId) {
        return "/topic/company." + companyId + ".location." + locationId + ".locations";
    }

    public static String approvals(Long companyId) {
        return "/topic/company." + companyId + ".approvals";
    }

    public static String userApprovals(Long userId) {
        return "/queue/user." + userId + ".approvals";
    }

    public static String integrations(Long companyId) {
        return "/topic/company." + companyId + ".integrations";
    }

    public static String userTasks(Long userId) {
        return "/queue/user." + userId + ".tasks";
    }

    public static String tasks(Long companyId, Long locationId) {
        return "/topic/company." + companyId + ".location." + locationId + ".tasks";
    }

    public static String operators(Long companyId, Long locationId) {
        return "/topic/company." + companyId + ".location." + locationId + ".operators";
    }

    /** Organizasyonel firma değişiklikleri (admin / org hierarchy yenileme). */
    public static String orgCompanies() {
        return "/topic/org.companies";
    }
}
