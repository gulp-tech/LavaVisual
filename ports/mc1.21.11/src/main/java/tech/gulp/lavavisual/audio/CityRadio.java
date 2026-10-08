package tech.gulp.lavavisual.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * The radio of the player's city. The stations are listed in {@code assets/lavavisual/radio/stations.tsv}, and every
 * address there was checked to answer with audio before it went into the mod ({@code tools/radio_check.py}). The city
 * comes from the IP address ({@link CityLocator}) or is chosen by hand in the menu; the nearest listed city within
 * {@link #RANGE_KM} gives its stations. The stations of the whole country play in every city.
 */
public final class CityRadio {
    private CityRadio() { }

    /** The place of the stations that play anywhere in the country. */
    public static final String NATIONAL = "Вся Россия";
    /** A located player gets the stations of a listed city only within this distance. */
    public static final double RANGE_KM = 120;
    private static final String RESOURCE = "/assets/lavavisual/radio/stations.tsv";

    /** A city of the list: its name, its region and its coordinates. */
    public record City(String name, String region, double lat, double lon) { }

    /** One station of the list: the place it belongs to and the station itself, grouped for the menu. */
    private record Item(String place, NetRadio.Station station) { }

    private static volatile List<City> cities = List.of();
    private static volatile List<Item> items = List.of();
    private static volatile boolean loaded;

    /** The group of the stations of one city, as the menu shows it. */
    public static String group(City city) { return "Радио · " + city.name(); }

    /** The cities of the list, in the order of the file. */
    public static List<City> cities() {
        load();
        return cities;
    }

    /** The stations of the whole country that are grouped under {@code group} (talk radio or music). */
    public static List<NetRadio.Station> national(String group) {
        load();
        List<NetRadio.Station> out = new ArrayList<>();
        for (Item item : items) if (item.place().equals(NATIONAL) && item.station().group().equals(group)) out.add(item.station());
        return out;
    }

    /** The stations of one city: they play only there, so they are listed under the city's own group. */
    public static List<NetRadio.Station> of(City city) {
        load();
        List<NetRadio.Station> out = new ArrayList<>();
        for (Item item : items) if (item.place().equals(city.name())) out.add(item.station());
        return out;
    }

    /**
     * The city the player is in: the one chosen by hand, otherwise the one found by the IP address; null when the
     * player is nowhere near a listed city or the city is not known yet.
     */
    public static City current() {
        HudConfig c = LavaVisualClient.config();
        if (c == null) return null;
        load();
        if (!c.radioCity.isEmpty()) {
            for (City city : cities) if (city.name().equals(c.radioCity)) return city;
        }
        if (c.radioAutoCity && CityLocator.located(c)) return nearest(c.radioLat, c.radioLon);
        return null;
    }

    /** The listed city nearest to the point, when it is within {@link #RANGE_KM}; otherwise null. */
    public static City nearest(double lat, double lon) {
        load();
        City best = null;
        double bestKm = Double.MAX_VALUE;
        for (City city : cities) {
            double km = distanceKm(lat, lon, city.lat(), city.lon());
            if (km < bestKm) { bestKm = km; best = city; }
        }
        return best != null && bestKm <= RANGE_KM ? best : null;
    }

    /** Great-circle distance in kilometres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Reads the list once; a broken line is skipped, never the whole list. */
    private static synchronized void load() {
        if (loaded) return;
        List<City> places = new ArrayList<>();
        List<Item> all = new ArrayList<>();
        try (InputStream in = CityRadio.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LavaVisual.LOGGER.warn("LavaVisual: the radio list {} is missing", RESOURCE);
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line.trim();
                    if (text.isEmpty() || text.startsWith("#")) continue;
                    String[] p = text.split("\t");
                    if (p.length < 8) continue;
                    try {
                        String place = p[0].trim(), region = p[3].trim(), kind = p[4].trim();
                        String name = p[5].trim(), genre = p[6].trim(), url = p[7].trim();
                        double lat = Double.parseDouble(p[1].trim()), lon = Double.parseDouble(p[2].trim());
                        if (name.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) continue;
                        if (place.equals(NATIONAL)) {
                            String group = switch (kind) {
                                case "talk" -> NetRadio.TALK;
                                case "music" -> NetRadio.MUSIC;
                                default -> null;
                            };
                            if (group != null) all.add(new Item(place, new NetRadio.Station(group, name, genre, url)));
                        } else {
                            City city = null;
                            for (City known : places) if (known.name().equals(place)) city = known;
                            if (city == null) {
                                city = new City(place, region, lat, lon);
                                places.add(city);
                            }
                            all.add(new Item(place, new NetRadio.Station(group(city), name, genre, url)));
                        }
                    } catch (NumberFormatException ignored) {
                        // a line with a broken coordinate is skipped
                    }
                }
            }
        } catch (IOException failure) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read the radio list", failure);
        }
        cities = List.copyOf(places);
        items = List.copyOf(all);
        loaded = true;
    }
}
