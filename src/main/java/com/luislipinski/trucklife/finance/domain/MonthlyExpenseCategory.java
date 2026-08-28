package com.luislipinski.trucklife.finance.domain;

public enum MonthlyExpenseCategory {
    RENT("rent", "Aluguel", true),
    ELECTRICITY("electricity", "Eletricidade", true),
    WATER("water", "Água / lixo", true),
    INTERNET("internet", "Internet", false),
    PHONE("phone", "Celular", false),
    GROCERIES("groceries", "Mercado", true),
    EATING_OUT("eatingOut", "Alimentação fora", true),
    HEALTH("health", "Saúde / parcela pessoal", false),
    PUBLIC_TRANSPORT("publicTransport", "Ônibus / metrô", true),
    HOUSEHOLD("household", "Higiene / casa", true),
    LEISURE("leisure", "Lazer", true);

    private final String code;
    private final String label;
    private final boolean citySensitive;

    MonthlyExpenseCategory(String code, String label, boolean citySensitive) {
        this.code = code;
        this.label = label;
        this.citySensitive = citySensitive;
    }

    public String code() { return code; }
    public String label() { return label; }
    public boolean citySensitive() { return citySensitive; }
}
