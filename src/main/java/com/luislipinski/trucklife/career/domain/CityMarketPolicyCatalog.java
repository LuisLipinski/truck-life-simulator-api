package com.luislipinski.trucklife.career.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CityMarketPolicyCatalog {

    public static final String VERSION = "1";

    private static final Set<String> ATS_PREMIUM_CITIES = cities("Los Angeles, CA|Oakland, CA|San Diego, CA|San Francisco, CA|San Rafael, CA|Santa Cruz, CA|Truckee, CA|Denver, CO|Chicago, IL|Las Vegas, NV|Portland, OR|Austin, TX|Seattle, WA|Jackson, WY");
    private static final Set<String> ATS_MAJOR_CITIES = cities("Phoenix, AZ|Tucson, AZ|Fayetteville, AR|Little Rock, AR|Fresno, CA|Sacramento, CA|Colorado Springs, CO|Fort Collins, CO|Boise, ID|Coeur d'Alene, ID|Peoria, IL|Rockford, IL|Cedar Rapids, IA|Des Moines, IA|Kansas City, KS|Wichita, KS|Baton Rouge, LA|New Orleans, LA|Kansas City, MO|Springfield, MO|St. Louis, MO|Billings, MT|Bozeman, MT|Lincoln, NE|Omaha, NE|Reno, NV|Albuquerque, NM|Santa Fe, NM|Oklahoma City, OK|Tulsa, OK|Bend, OR|Eugene, OR|Dallas, TX|Fort Worth, TX|Houston, TX|San Antonio, TX|Provo, UT|Salt Lake City, UT|Spokane, WA|Tacoma, WA|Vancouver, WA|Cheyenne, WY");
    private static final Set<String> ATS_REGIONAL_CITIES = cities("Flagstaff, AZ|Kingman, AZ|Yuma, AZ|Fort Smith, AR|Springdale, AR|Bakersfield, CA|Carlsbad, CA|Eureka, CA|Oxnard, CA|Redding, CA|Santa Maria, CA|Durango, CO|Grand Junction, CO|Pueblo, CO|Idaho Falls, ID|Lewiston, ID|Nampa, ID|Twin Falls, ID|Champaign, IL|Springfield, IL|Davenport, IA|Iowa City, IA|Sioux City, IA|Topeka, KS|Lafayette, LA|Lake Charles, LA|Shreveport, LA|Columbia, MO|Jefferson City, MO|Missoula, MT|Grand Island, NE|Carson City, NV|Las Cruces, NM|Roswell, NM|Salem, OR|Amarillo, TX|Corpus Christi, TX|El Paso, TX|Laredo, TX|Lubbock, TX|McAllen, TX|Odessa, TX|Waco, TX|Ogden, UT|St. George, UT|Bellingham, WA|Everett, WA|Olympia, WA|Yakima, WA|Casper, WY|Gillette, WY|Laramie, WY");
    private static final Set<String> ATS_SMALLER_CITIES = cities("Camp Verde, AZ|Clifton, AZ|Grand Canyon Village, AZ|Kayenta, AZ|Lake Havasu City, AZ|Nogales, AZ|Page, AZ|San Simon, AZ|Show Low, AZ|Sierra Vista, AZ|Winslow, AZ|El Dorado, AR|Harrison, AR|Hot Springs, AR|Jonesboro, AR|Pine Bluff, AR|Texarkana, AR|Barstow, CA|El Centro, CA|Hornbrook, CA|Ukiah, CA|Alamosa, CO|Burlington, CO|Lamar, CO|Montrose, CO|Rangely, CO|Steamboat Springs, CO|Sterling, CO|Grangeville, ID|Ketchum, ID|Pocatello, ID|Salmon, ID|Sandpoint, ID|Bloomington, IL|East St. Louis, IL|Effingham, IL|Marion, IL|Moline, IL|Quincy, IL|Burlington, IA|Council Bluffs, IA|Dubuque, IA|Fort Dodge, IA|Mason City, IA|Ottumwa, IA|Waterloo, IA|Colby, KS|Dodge City, KS|Emporia, KS|Garden City, KS|Hays, KS|Hutchinson, KS|Junction City, KS|Marysville, KS|Phillipsburg, KS|Pittsburg, KS|Salina, KS|Alexandria, LA|DeRidder, LA|Houma, LA|Monroe, LA|Natchitoches, LA|Port Fourchon, LA|Cape Girardeau, MO|Joplin, MO|Kirksville, MO|Maryville, MO|Poplar Bluff, MO|Rolla, MO|St. Joseph, MO|Butte, MT|Glasgow, MT|Glendive, MT|Great Falls, MT|Havre, MT|Helena, MT|Kalispell, MT|Laurel, MT|Lewistown, MT|Miles City, MT|Sidney, MT|Thompson Falls, MT|Alliance, NE|Chadron, NE|Columbus, NE|McCook, NE|Norfolk, NE|North Platte, NE|Scottsbluff, NE|Sidney, NE|Valentine, NE|Elko, NV|Ely, NV|Jackpot, NV|Pioche, NV|Primm, NV|Tonopah, NV|Winnemucca, NV|Alamogordo, NM|Artesia, NM|Carlsbad, NM|Clovis, NM|Farmington, NM|Gallup, NM|Hobbs, NM|Raton, NM|Socorro, NM|Tucumcari, NM|Ardmore, OK|Clinton, OK|Enid, OK|Guymon, OK|Idabel, OK|Lawton, OK|McAlester, OK|Woodward, OK|Astoria, OR|Burns, OR|Coos Bay, OR|Klamath Falls, OR|Lakeview, OR|Medford, OR|Newport, OR|Ontario, OR|Pendleton, OR|The Dalles, OR|Abilene, TX|Beaumont, TX|Brownsville, TX|Dalhart, TX|Del Rio, TX|Fort Stockton, TX|Galveston, TX|Huntsville, TX|Longview, TX|Lufkin, TX|San Angelo, TX|Texarkana, TX|Tyler, TX|Van Horn, TX|Victoria, TX|Wichita Falls, TX|Cedar City, UT|Logan, UT|Moab, UT|Price, UT|Salina, UT|Vernal, UT|Aberdeen, WA|Colville, WA|Grand Coulee, WA|Kennewick, WA|Longview, WA|Omak, WA|Port Angeles, WA|Wenatchee, WA|Cody, WY|Evanston, WY|Rawlins, WY|Riverton, WY|Rock Springs, WY|Sheridan, WY");

    private static final Set<String> ETS2_PREMIUM_CITIES = cities("Londres, Reino Unido|Paris, França|München, Alemanha|Amsterdam, Países Baixos|Luxemburgo, Luxemburgo|Zürich, Suíça|Genève, Suíça|Milano, Itália|Barcelona, Espanha|København, Dinamarca|Oslo, Noruega|Stockholm, Suécia|Helsinki, Finlândia");
    private static final Set<String> ETS2_MAJOR_CITIES = cities("Birmingham, Reino Unido|Edimburgo, Reino Unido|Glasgow, Reino Unido|Liverpool, Reino Unido|Manchester, Reino Unido|Bordeaux, França|Lille, França|Lyon, França|Marseille, França|Nice, França|Toulouse, França|Berlin, Alemanha|Düsseldorf, Alemanha|Frankfurt am Main, Alemanha|Hamburg, Alemanha|Köln, Alemanha|Stuttgart, Alemanha|Rotterdam, Países Baixos|Bruxelas, Bélgica|Bern, Suíça|Salzburg, Áustria|Viena, Áustria|Bologna, Itália|Firenze, Itália|Napoli, Itália|Roma, Itália|Torino, Itália|Venezia, Itália|Lisboa, Portugal|Porto, Portugal|Bilbao, Espanha|Madrid, Espanha|Málaga, Espanha|Sevilla, Espanha|Valencia, Espanha|Gdańsk, Polônia|Kraków, Polônia|Poznań, Polônia|Warszawa, Polônia|Wrocław, Polônia|Praha, Tchéquia|Bratislava, Eslováquia|Budapest, Hungria|Aarhus, Dinamarca|Bergen, Noruega|Stavanger, Noruega|Göteborg, Suécia|Malmö, Suécia|Tampere, Finlândia|Turku, Finlândia|Tallinn, Estônia|Rīga, Letônia|Vilnius, Lituânia|București, Romênia|Cluj-Napoca, Romênia|Sofia, Bulgária|İstanbul, Turquia|Ljubljana, Eslovênia|Zagreb, Croácia|Sarajevo, Bósnia e Herzegovina|Beograd, Sérvia|Podgorica, Montenegro|Pristina, Kosovo|Skopje, Macedônia do Norte|Tirana, Albânia|Athína, Grécia|Thessaloníki, Grécia");
    private static final Set<String> ETS2_REGIONAL_CITIES = cities("Aberdeen, Reino Unido|Cambridge, Reino Unido|Cardiff, Reino Unido|Newcastle upon Tyne, Reino Unido|Sheffield, Reino Unido|Southampton, Reino Unido|Dijon, França|Montpellier, França|Nantes, França|Rennes, França|Strasbourg, França|Bremen, Alemanha|Dortmund, Alemanha|Dresden, Alemanha|Hannover, Alemanha|Leipzig, Alemanha|Nürnberg, Alemanha|Groningen, Países Baixos|Liège, Bélgica|Graz, Áustria|Innsbruck, Áustria|Linz, Áustria|Bari, Itália|Cagliari, Itália|Catania, Itália|Genova, Itália|Palermo, Itália|Trieste, Itália|Verona, Itália|Coimbra, Portugal|Faro, Portugal|A Coruña, Espanha|Córdoba, Espanha|Murcia, Espanha|Zaragoza, Espanha|Katowice, Polônia|Łódź, Polônia|Szczecin, Polônia|Brno, Tchéquia|Ostrava, Tchéquia|Košice, Eslováquia|Debrecen, Hungria|Pécs, Hungria|Szeged, Hungria|Aalborg, Dinamarca|Odense, Dinamarca|Kristiansand, Noruega|Trondheim, Noruega|Jönköping, Suécia|Linköping, Suécia|Uppsala, Suécia|Oulu, Finlândia|Tartu, Estônia|Daugavpils, Letônia|Liepāja, Letônia|Kaunas, Lituânia|Klaipėda, Lituânia|Brașov, Romênia|Constanța, Romênia|Iași, Romênia|Timișoara, Romênia|Burgas, Bulgária|Plovdiv, Bulgária|Varna, Bulgária|Maribor, Eslovênia|Rijeka, Croácia|Split, Croácia|Banja Luka, Bósnia e Herzegovina|Mostar, Bósnia e Herzegovina|Novi Sad, Sérvia|Niš, Sérvia|Durrës, Albânia|Pátra, Grécia|Irákleio, Grécia");
    private static final Set<String> ETS2_SMALLER_CITIES = cities("Carlisle, Reino Unido|Dover, Reino Unido|Felixstowe, Reino Unido|Grimsby, Reino Unido|Plymouth, Reino Unido|Swansea, Reino Unido|Calais, França|Reims, França|Metz, França|Clermont-Ferrand, França|Limoges, França|La Rochelle, França|Le Havre, França|Brest, França|Le Mans, França|Tours, França|Bourges, França|Ajaccio, França|Bastia, França|Duisburg, Alemanha|Erfurt, Alemanha|Kassel, Alemanha|Kiel, Alemanha|Magdeburg, Alemanha|Mannheim, Alemanha|Osnabrück, Alemanha|Rostock, Alemanha|IJmuiden, Países Baixos|Klagenfurt, Áustria|Parma, Itália|Livorno, Itália|Ancona, Itália|Pescara, Itália|Taranto, Itália|Villa San Giovanni, Itália|Messina, Itália|Olbia, Itália|Sassari, Itália|Évora, Portugal|Vigo, Espanha|León, Espanha|Santander, Espanha|Albacete, Espanha|Ciudad Real, Espanha|Badajoz, Espanha|Almería, Espanha|Lublin, Polônia|Białystok, Polônia|Olsztyn, Polônia|Banská Bystrica, Eslováquia|Esbjerg, Dinamarca|Hirtshals, Dinamarca|Frederikshavn, Dinamarca|Gedser, Dinamarca|Helsingborg, Suécia|Kalmar, Suécia|Karlskrona, Suécia|Nynäshamn, Suécia|Örebro, Suécia|Södertälje, Suécia|Västerås, Suécia|Växjö, Suécia|Kotka, Finlândia|Lahti, Finlândia|Kouvola, Finlândia|Pori, Finlândia|Vaasa, Finlândia|Rovaniemi, Finlândia|Pärnu, Estônia|Rēzekne, Letônia|Ventspils, Letônia|Panevėžys, Lituânia|Šiauliai, Lituânia|Utena, Lituânia|Craiova, Romênia|Galați, Romênia|Pitești, Romênia|Târgu Mureș, Romênia|Karlovo, Bulgária|Kozloduy, Bulgária|Pernik, Bulgária|Pirdop, Bulgária|Ruse, Bulgária|Veliko Tarnovo, Bulgária|Edirne, Turquia|Tekirdağ, Turquia|Koper, Eslovênia|Novo Mesto, Eslovênia|Osijek, Croácia|Zadar, Croácia|Tuzla, Bósnia e Herzegovina|Kragujevac, Sérvia|Nikšić, Montenegro|Bijelo Polje, Montenegro|Bitola, Macedônia do Norte|Vlorë, Albânia|Lárisa, Grécia|Ioánnina, Grécia|Kavála, Grécia|Kalamáta, Grécia|Chaniá, Grécia");

    private static final Map<String, Profile> ATS_OVERRIDES = Map.ofEntries(
            Map.entry("San Francisco, CA", profile("custom", "Metrópole de custo excepcional", "1.30", "1.10", true)),
            Map.entry("San Rafael, CA", profile("custom", "Área metropolitana de custo muito alto", "1.24", "1.08", true)),
            Map.entry("Oakland, CA", profile("custom", "Metrópole de custo muito alto", "1.22", "1.08", true)),
            Map.entry("Santa Cruz, CA", profile("custom", "Cidade litorânea de custo muito alto", "1.22", "1.05", true)),
            Map.entry("Truckee, CA", profile("custom", "Mercado turístico de custo alto", "1.18", "1", true)),
            Map.entry("Los Angeles, CA", profile("custom", "Metrópole de custo alto", "1.16", "1.08", true)),
            Map.entry("Seattle, WA", profile("custom", "Metrópole de custo alto", "1.18", "1.08", true)),
            Map.entry("Jackson, WY", profile("custom", "Mercado turístico de custo excepcional", "1.26", "1.02", true))
    );

    private static final Map<String, Profile> ETS2_OVERRIDES = Map.ofEntries(
            Map.entry("Londres, Reino Unido", profile("custom", "Capital de custo excepcional", "1.28", "1.10", true)),
            Map.entry("Paris, França", profile("custom", "Capital de custo muito alto", "1.22", "1.09", true)),
            Map.entry("Zürich, Suíça", profile("custom", "Metrópole de custo excepcional", "1.24", "1.09", true)),
            Map.entry("Genève, Suíça", profile("custom", "Metrópole de custo excepcional", "1.22", "1.08", true)),
            Map.entry("Oslo, Noruega", profile("custom", "Capital de custo muito alto", "1.20", "1.09", true)),
            Map.entry("München, Alemanha", profile("custom", "Metrópole de custo muito alto", "1.18", "1.08", true)),
            Map.entry("København, Dinamarca", profile("custom", "Capital de custo muito alto", "1.17", "1.08", true)),
            Map.entry("Luxemburgo, Luxemburgo", profile("custom", "Capital de custo muito alto", "1.16", "1.07", true)),
            Map.entry("Stockholm, Suécia", profile("custom", "Capital de custo alto", "1.15", "1.07", true)),
            Map.entry("Milano, Itália", profile("custom", "Metrópole de custo alto", "1.15", "1.07", true)),
            Map.entry("Barcelona, Espanha", profile("custom", "Metrópole de custo alto", "1.14", "1.06", true)),
            Map.entry("Lisboa, Portugal", profile("custom", "Capital de custo alto", "1.12", "1.05", true)),
            Map.entry("Helsinki, Finlândia", profile("custom", "Capital de custo alto", "1.12", "1.06", true)),
            Map.entry("İstanbul, Turquia", profile("custom", "Metrópole de custo alto", "1.12", "1.06", true))
    );

    private static final Map<String, String> ETS2_COUNTRY_CODES = Map.ofEntries(
            Map.entry("Reino Unido", "GB"), Map.entry("França", "FR"), Map.entry("Alemanha", "DE"),
            Map.entry("Países Baixos", "NL"), Map.entry("Bélgica", "BE"), Map.entry("Luxemburgo", "LU"),
            Map.entry("Suíça", "CH"), Map.entry("Áustria", "AT"), Map.entry("Itália", "IT"),
            Map.entry("Portugal", "PT"), Map.entry("Espanha", "ES"), Map.entry("Polônia", "PL"),
            Map.entry("Tchéquia", "CZ"), Map.entry("Eslováquia", "SK"), Map.entry("Hungria", "HU"),
            Map.entry("Dinamarca", "DK"), Map.entry("Noruega", "NO"), Map.entry("Suécia", "SE"),
            Map.entry("Finlândia", "FI"), Map.entry("Estônia", "EE"), Map.entry("Letônia", "LV"),
            Map.entry("Lituânia", "LT"), Map.entry("Romênia", "RO"), Map.entry("Bulgária", "BG"),
            Map.entry("Turquia", "TR"), Map.entry("Eslovênia", "SI"), Map.entry("Croácia", "HR"),
            Map.entry("Bósnia e Herzegovina", "BA"), Map.entry("Sérvia", "RS"), Map.entry("Montenegro", "ME"),
            Map.entry("Kosovo", "XK"), Map.entry("Macedônia do Norte", "MK"), Map.entry("Albânia", "AL"),
            Map.entry("Grécia", "GR")
    );

    private static final Profile ATS_PREMIUM = profile("premium", "Metrópole de custo muito alto", "1.22", "1.08", true);
    private static final Profile ATS_MAJOR = profile("major", "Metrópole principal", "1.10", "1.05", true);
    private static final Profile ATS_REGIONAL = profile("regional", "Centro regional", "1", "1.01", true);
    private static final Profile ATS_SMALLER = profile("smaller", "Cidade menor", "0.88", "0.96", true);
    private static final Profile ETS2_PREMIUM = profile("premium", "Capital ou metrópole de custo alto", "1.18", "1.08", true);
    private static final Profile ETS2_MAJOR = profile("major", "Capital ou metrópole principal", "1.10", "1.05", true);
    private static final Profile ETS2_REGIONAL = profile("regional", "Centro regional", "1", "1.01", true);
    private static final Profile ETS2_SMALLER = profile("smaller", "Cidade menor", "0.90", "0.97", true);

    private CityMarketPolicyCatalog() {
    }

    public static Profile resolve(CareerGame game, String stateCode, String countryCode, String city) {
        String normalizedCity = city == null ? "" : city.strip();
        return game == CareerGame.ATS
                ? resolveAts(stateCode, normalizedCity)
                : resolveEts2(countryCode, normalizedCity);
    }

    private static Profile resolveAts(String stateCode, String city) {
        Profile override = ATS_OVERRIDES.get(city);
        Profile profile = override != null ? override
                : ATS_PREMIUM_CITIES.contains(city) ? ATS_PREMIUM
                : ATS_MAJOR_CITIES.contains(city) ? ATS_MAJOR
                : ATS_REGIONAL_CITIES.contains(city) ? ATS_REGIONAL
                : ATS_SMALLER_CITIES.contains(city) ? ATS_SMALLER
                : reference(city);
        if (profile.known() && !suffix(city).equals(normalizeCode(stateCode))) {
            throw new IllegalArgumentException("ATS base city does not belong to the career state");
        }
        return profile;
    }

    private static Profile resolveEts2(String countryCode, String city) {
        Profile override = ETS2_OVERRIDES.get(city);
        Profile profile = override != null ? override
                : ETS2_PREMIUM_CITIES.contains(city) ? ETS2_PREMIUM
                : ETS2_MAJOR_CITIES.contains(city) ? ETS2_MAJOR
                : ETS2_REGIONAL_CITIES.contains(city) ? ETS2_REGIONAL
                : ETS2_SMALLER_CITIES.contains(city) ? ETS2_SMALLER
                : reference(city);
        if (profile.known()) {
            String expectedCode = ETS2_COUNTRY_CODES.get(suffix(city));
            if (expectedCode == null || !expectedCode.equals(normalizeCode(countryCode))) {
                throw new IllegalArgumentException("ETS2 base city does not belong to the career country");
            }
        }
        return profile;
    }

    private static Profile reference(String city) {
        return profile(
                "reference",
                city.isBlank() ? "Referência da sede" : "Referência da sede para cidade de mod",
                "1",
                "1",
                false
        );
    }

    private static Set<String> cities(String value) {
        return Set.of(value.split("\\|"));
    }

    private static String suffix(String city) {
        int separator = city.lastIndexOf(',');
        return separator < 0 ? "" : city.substring(separator + 1).strip();
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static Profile profile(String key, String label, String costFactor, String salaryFactor, boolean known) {
        return new Profile(key, label, new BigDecimal(costFactor), new BigDecimal(salaryFactor), known);
    }

    public record Profile(
            String key,
            String label,
            BigDecimal costFactor,
            BigDecimal salaryFactor,
            boolean known
    ) {
    }
}
