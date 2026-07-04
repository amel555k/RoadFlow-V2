package com.amko.roadflow.data.local

import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.domain.model.RadarCoordinate
import com.amko.roadflow.domain.model.RadarLocation

object RadarConfig {

    val locations = listOf(
        RadarLocation("Travnik", listOf(417, 415), Canton.Srednjobosanski, mapEnabled = true, fromFirebase = true),
        RadarLocation("Vitez", listOf(400, 330), Canton.Srednjobosanski, mapEnabled = true, fromFirebase = true),
        RadarLocation("Fojnica", listOf(418, 360), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Donji Vakuf", listOf(419, 416), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Bugojno", listOf(402, 332), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Gornji Vakuf-Uskoplje", listOf(401, 331), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Novi Travnik", listOf(420, 333), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Busovača", listOf(421, 334), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Kiseljak", listOf(422, 335), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Kreševo", listOf(423, 336), Canton.Srednjobosanski, mapEnabled = true,fromFirebase = true),
        RadarLocation("Jajce", listOf(424, 337), Canton.Srednjobosanski, fromFirebase = true),
        RadarLocation("Zenica", listOf(323, 393), Canton.ZenickoDobojski, mapEnabled = true),
        RadarLocation("Tešanj", listOf(328, 399), Canton.ZenickoDobojski),
        RadarLocation("Maglaj", listOf(327, 397), Canton.ZenickoDobojski),
        RadarLocation("Doboj Jug", listOf(354, 353), Canton.ZenickoDobojski),
        RadarLocation("Žepče", listOf(453), Canton.ZenickoDobojski),
        RadarLocation("Tuzla", listOf(391, 319), Canton.Tuzlanski),
        RadarLocation("Gračanica", listOf(471, 355), Canton.Tuzlanski),
        RadarLocation("Kalesija", listOf(2059, 3987), Canton.Tuzlanski),
        RadarLocation("Gradačac", listOf(388), Canton.Tuzlanski),
        RadarLocation("Čelić", listOf(392, 320), Canton.Tuzlanski),
        RadarLocation("Srebrenik", listOf(2971, 2831), Canton.Tuzlanski),
        RadarLocation("Banovići", listOf(435, 806), Canton.Tuzlanski),
        RadarLocation("Sapna", listOf(721, 1551), Canton.Tuzlanski),
        RadarLocation("Teočak", listOf(745, 3242), Canton.Tuzlanski),
        RadarLocation("Sarajevo", listOf(342, 412), Canton.Sarajevo),
        RadarLocation("Brčko", listOf(4821, 4822), Canton.BrckoDistrikt),
    )

    val coordinates = listOf(
        RadarCoordinate("ulica Erika Brandisa", 44.224583, 17.669480, speedLimit = 30),
        RadarCoordinate("Kalibunar (M-5)", 44.23165837901651, 17.636192268618732, speedLimit = 50),
        RadarCoordinate("Kalibunar (M-5)", 44.22878541002124, 17.64796224813097, speedLimit = 50),
        RadarCoordinate("Vrelo (M-5)", 44.244515, 17.594268, speedLimit = 50),
        RadarCoordinate("Karaulska cesta", 44.245832, 17.574472, speedLimit = 50),
        RadarCoordinate("Donje Putićevo (M-5)", 44.211025, 17.709256, speedLimit = 60),
        RadarCoordinate("Turbe (M-5)", 44.242521, 17.586579, speedLimit = 50),
        RadarCoordinate("M-5 Kanare", 44.224728, 17.684708, speedLimit = 60),
        RadarCoordinate("M-5 Kanare", 44.227329, 17.680557, speedLimit = 60),
        RadarCoordinate("Aleja Konzula", 44.226121, 17.647507, speedLimit = 40),
        RadarCoordinate("Dolac na Lašvi", 44.216944, 17.694915, speedLimit = 50),
        RadarCoordinate("R441-Počulica", 44.167670, 17.834732, speedLimit = 50),
        RadarCoordinate("M-5 Divjak", 44.166250, 17.770562, speedLimit = 50),
        RadarCoordinate("Lokalna cesta Divjak", 44.164295, 17.767976, speedLimit = 50),
        RadarCoordinate("Dolac na Lašvi (škola)", 44.216514, 17.690758, speedLimit = 40),
        RadarCoordinate("Polje Slavka Gavrančića", 44.208375, 17.695194, speedLimit = 50),
        RadarCoordinate("R-440 Bila", 44.187022, 17.753603, speedLimit = 50),
        RadarCoordinate("Nova Bila (škola)", 44.189062, 17.739428, speedLimit = 40),
        RadarCoordinate("Nova Bila (škola)", 44.189991, 17.737367, speedLimit = 40),
        RadarCoordinate("M-5 Nova Bila", 44.193082, 17.733456, speedLimit = 50),
        RadarCoordinate("Nova Bila", 44.193082, 17.733456, speedLimit = 50),
        RadarCoordinate("Mehurići (škola)", 44.271682, 17.734149, speedLimit = 50),
        RadarCoordinate("Mosor", 44.233509, 17.711371, speedLimit = 50),
        RadarCoordinate("M-5 Šantići", 44.146207, 17.827426, speedLimit = 60),
        RadarCoordinate("ulica Lašvanska", 44.162307, 17.788039, speedLimit = 30),
        RadarCoordinate("ulica Lašvanska", 44.162223, 17.791571, speedLimit = 30),
        RadarCoordinate("Ulica Branilaca Starog Viteza", 44.158769, 17.786026, speedLimit = 40),
        RadarCoordinate("ulica Kralja Tvrtka", 44.160276, 17.783686, speedLimit = 40),
        RadarCoordinate("Stjepana Radića", 44.149454, 17.804979, speedLimit = 50),
        RadarCoordinate("M-5 Krčevine", 44.165096, 17.791257, speedLimit = 50),
        RadarCoordinate("lokalna cesta PC 96 II, Krčevine", 44.165240, 17.790378, speedLimit = 50),
        RadarCoordinate("lokalna cesta PC 96 II, Krčevine", 44.167380, 17.786715, speedLimit = 50),
        RadarCoordinate("R-441 Dubravica", 44.152590, 17.813073, speedLimit = 50),
        RadarCoordinate("R-441 Dubravica", 44.150207, 17.810891, speedLimit = 50),
        RadarCoordinate("R-441 Zabilje", 44.195071, 17.759600, speedLimit = 50),
        RadarCoordinate("ulica Hrvatske mladeži", 44.145020, 17.794997, speedLimit = 50),
        RadarCoordinate("Guča Gora", 44.243166, 17.728671, speedLimit = 50),
        RadarCoordinate("Han Bila (škola)", 44.237260, 17.758765, speedLimit = 40),
        RadarCoordinate("M-5 Jardol", 44.170353, 17.776828, speedLimit = 60),
        RadarCoordinate("M-5 Ahmići", 44.143064, 17.837669, speedLimit = 50),
        RadarCoordinate("M-5 Bila", 44.180971, 17.752092, speedLimit = 50),
        RadarCoordinate("Stacionarni Radar - ul. Šehida, Travnik", 44.227014, 17.664612, speedLimit = 50, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Vitez (kod Impregnacije)", 44.151029, 17.805830, speedLimit = 50, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Novi Travnik M16.4", 44.192272, 17.707698, speedLimit = 60, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Turbe (kod benzinske INA)", 44.239581, 17.574770, speedLimit = 50, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Oborci", 44.194250, 17.421098, speedLimit = 50, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Busovača", 44.098984, 17.8807316, speedLimit = 50, stacionaran = true),
        RadarCoordinate("Stacionarni Radar - Brnjaci", 43.913573, 18.127666, speedLimit = 40, stacionaran = true),

        RadarCoordinate("M-16,4 VODOVOD OŠ", 44.149076,17.604311,60),
        RadarCoordinate("M-16,4 RANKOVIĆI", 44.175268,17.670869,60),
        RadarCoordinate("M-16,4 BUČIĆI", 44.194974,17.699536,60),
        RadarCoordinate("M-16,4 NEVIĆ POLJE", 44.196256,17.705551,60),
        RadarCoordinate("M-16,4 STOJKOVIĆI", 44.191412,17.682353,60),
        RadarCoordinate("M-16,4 RATANJSKA", 44.182715,17.670732,60),
        RadarCoordinate("R-439 TRENICA", 44.149112,17.670811,50),
        RadarCoordinate("R-439 OPARA", 44.096408,17.639628,50),
        RadarCoordinate("R-439 RASTOVCI", 44.165182,17.670652,50),
        RadarCoordinate("ULICA KALINSKA", 44.168142,17.650108,50),
        RadarCoordinate("LOKALNI PUT NEVIĆ POLJE", 44.200486,17.703774,50),
        RadarCoordinate("ULICA MEHMEDA SPAHE", 44.170386,17.653855,50),
        RadarCoordinate("ULICA STJEPANA TOMAŠEVIĆA", 44.176835,17.663361,50),

        RadarCoordinate("Alagići", 43.889916,18.059056,50),
        RadarCoordinate("Crkvenjak", 43.886923,18.070823,50),
        RadarCoordinate("Kreševo (Grad)", 43.865876,18.042640,50),
        RadarCoordinate("Polje", 43.877755,18.068833,50),
        RadarCoordinate("Rakova Noga", 43.900994,18.032570,50),
        RadarCoordinate("Resnik", 43.878944,18.064492,50),
        RadarCoordinate("Volujak", 43.880298,18.081658,50),
    )

    fun findCoordinatesByName(locationName: String, city: String? = null): List<RadarCoordinate> {
        if (locationName.isBlank()) return emptyList()
        val mainName = resolveMainName(locationName, city) ?: return emptyList()
        return coordinates.filter { it.mainName == mainName }
    }


    fun findCoordinateByName(locationName: String, city: String? = null): RadarCoordinate? {
        return findCoordinatesByName(locationName, city).firstOrNull()
    }

    fun normalizeCityName(raw: String): String? {
        if (raw.isBlank()) return null
        val n = stripDiacritics(raw.lowercase().trim())
        return when {
            contains(n, "travnik") -> "Travnik"
            contains(n, "vitez") -> "Vitez"
            contains(n, "fojnica") -> "Fojnica"
            contains(n, "donji vakuf") -> "Donji Vakuf"
            contains(n, "bugojno") -> "Bugojno"
            contains(n, "gornji vakuf") || contains(n, "uskoplje") -> "Gornji Vakuf-Uskoplje"
            contains(n, "novi travnik") -> "Novi Travnik"
            contains(n, "busovaca") -> "Busovača"
            contains(n, "kiseljak") -> "Kiseljak"
            contains(n, "kresevo") -> "Kreševo"
            contains(n, "jajce") -> "Jajce"
            contains(n, "kalibunar") -> "Kalibunar"
            contains(n, "nevics polje") || contains(n, "nevic polje") -> "Nević Polje"
            contains(n, "nova bila") -> "Nova Bila"
            contains(n, "bila") && !contains(n, "nova") -> "Bila"
            contains(n, "han bila") -> "Han Bila"
            contains(n, "turbe") -> "Turbe"
            contains(n, "dolac") -> "Dolac na Lašvi"
            contains(n, "vladimira nazora") -> "Ulica Vladimira Nazora"
            contains(n, "skopaljska") -> "Skopaljska ulica"
            contains(n, "polje slavka") || contains(n, "gavrancica") -> "Polje Slavka Gavrančića"
            contains(n, "guca gora") -> "Guča Gora"
            contains(n, "mosor") -> "Mosor"
            else -> {
                null
            }
        }
    }


    private fun resolveMainName(raw: String, city: String? = null): String? {
        var n = stripDiacritics(raw.lowercase().trim())
        val c = city?.let { stripDiacritics(it.lowercase().trim()) } ?: ""

        if (contains(n, "lok") && contains(n, "divjak"))
            return "Lokalna cesta Divjak"
        if (contains(n, "lok") && (contains(n, "pc 96") || contains(n, "pc96") || contains(n, "krcevine")))
            return "lokalna cesta PC 96 II, Krčevine"

        n = removeStreetPrefixes(n)

        if (contains(n, "bolnici") || contains(n, "prema bolnici")) return "R-440 Bila"
        if ((contains(n, "r-440") || contains(n, "r440") || contains(n, "r-441") || contains(n, "r441")) && contains(n, "bila")) return "R-440 Bila"
        if (contains(n, "han bila")) return "Han Bila (škola)"
        if (contains(n, "nova bila") && (contains(n, "m-5") || contains(n, "m5") || contains(n, "m 5"))) return "M-5 Nova Bila"
        if (contains(n, "nova bila") && (contains(n, "skola") || contains(n, "skole"))) return "Nova Bila (škola)"
        if (contains(n, "nova bila")) return "Nova Bila"
        if (contains(n, "bila") && (contains(n, "m-5") || contains(n, "m5") || contains(n, "m 5"))) return "M-5 Bila"
        if (n == "bila") return "M-5 Bila"
        if (contains(n, "krcevine") && (contains(n, "m-5") || contains(n, "m5") || contains(n, "m 5"))) return "M-5 Krčevine"
        if (contains(n, "krcevine")) return "M-5 Krčevine"
        if (contains(n, "kalibunar")) return "Kalibunar (M-5)"
        if (contains(n, "vrelo")) return "Vrelo (M-5)"
        if (contains(n, "karaulska")) return "Karaulska cesta"
        if (contains(n, "turbe")) return "Turbe (M-5)"
        if (contains(n, "puticevo")) return "Donje Putićevo (M-5)"
        if (contains(n, "dolac") && contains(n, "lasvi") && (contains(n, "skola") || contains(n, "skole"))) return "Dolac na Lašvi (škola)"
        if (contains(n, "dolac") && contains(n, "lasvi")) return "Dolac na Lašvi"
        if (contains(n, "mehuric")) return "Mehurići (škola)"
        if (contains(n, "dubravica") || contains(n, "dubravice")) return "R-441 Dubravica"
        if (contains(n, "zabilje")) return "R-441 Zabilje"
        if (contains(n, "poculica")) return "R441-Počulica"
        if (contains(n, "erika brandisa") || contains(n, "erika brandis")) return "ulica Erika Brandisa"
        if (contains(n, "lasvanska")) return "ulica Lašvanska"
        if (contains(n, "branilaca starog viteza") ||
            contains(n, "branilaca st. viteza") ||
            contains(n, "b.s.viteza") ||
            contains(n, "b. s. viteza") ||
            contains(n, "b.s. viteza") ||
            contains(n, "b. s.viteza") ||
            (contains(n, "branilaca") && contains(n, "viteza")))
            return "Ulica Branilaca Starog Viteza"
        if (contains(n, "kralja tvrtka")) return "ulica Kralja Tvrtka"
        if (contains(n, "hrvatske mladezi") || contains(n, "hrvatske mlade")) return "ulica Hrvatske mladeži"
        if (contains(n, "aleja konzula") || contains(n, "konzula")) return "Aleja Konzula"
        if (contains(n, "stjepana radica") || contains(n, "stjepana radic") || (contains(n, "radica") && !contains(n, "branilaca"))) return "Stjepana Radića"
        if (contains(n, "kanare")) return "M-5 Kanare"
        if (contains(n, "santici")) return "M-5 Šantići"
        if (contains(n, "jardol")) return "M-5 Jardol"
        if (contains(n, "ahmic")) return "M-5 Ahmići"
        if (contains(n, "divjak")) return "M-5 Divjak"
        if (contains(n, "vodovod os") || (contains(n, "vodovod") && contains(n, "os")))
            return "M-16,4 VODOVOD OŠ"
        if (contains(n, "vodovod"))
            return "M-16,4 VODOVOD OŠ"
        if (contains(n, "rankovic"))
            return "M-16,4 RANKOVIĆI"
        if (contains(n, "bucic"))
            return "M-16,4 BUČIĆI"
        if (contains(n, "duhovo"))
            return "M-16,4 DUHOVO"
        if (contains(n, "stojkovic"))
            return "M-16,4 STOJKOVIĆI"
        if (contains(n, "ratanjska"))
            return "M-16,4 RATANJSKA"
        if (contains(n, "nevic") && contains(n, "lok"))
            return "LOKALNI PUT NEVIĆ POLJE"
        if (contains(n, "nevic"))
            return "M-16,4 NEVIĆ POLJE"
        if (contains(n, "trenica"))
            return "R-439 TRENICA"
        if (contains(n, "opara"))
            return "R-439 OPARA"
        if (contains(n, "rastovci"))
            return "R-439 RASTOVCI"
        if (contains(n, "kalinska"))
            return "ULICA KALINSKA"
        if (contains(n, "mehmeda spahe") || contains(n, "mehmed spaho") || contains(n, "m. spahe"))
            return "ULICA MEHMEDA SPAHE"
        if (contains(n, "stjepana tomasevica") || contains(n, "tomasevic"))
            return "ULICA STJEPANA TOMAŠEVIĆA"
        if (contains(n, "alagic"))
            return "Alagići"
        if (contains(n, "crkvenjak"))
            return "Crkvenjak"
        if (contains(n, "kresevo") && (contains(n, "grad") || contains(n, "centar")))
            return "Kreševo (Grad)"
        if (contains(n, "rakova noga") || contains(n, "r.noga") || (contains(n, "noga") && contains(n, "lok")))
            return "Rakova Noga"
        if (contains(n, "noga"))
            return "Rakova Noga"
        if (contains(n, "resnik"))
            return "Resnik"
        if (contains(n, "volujak"))
            return "Volujak"


        if (contains(n, "polje") && (contains(n, "slavka") || contains(n, "gavrancica"))) return "Polje Slavka Gavrančića"
        if (contains(n, "polje") && (contains(n, "s.g.") || contains(n, "s.g") || contains(n, "s g"))) return "Polje Slavka Gavrančića"
        if (contains(n, "slavka") || contains(n, "gavrancica")) return "Polje Slavka Gavrančića"
        if (contains(n, "guca gora")) return "Guča Gora"
        if (contains(n, "mosor")) return "Mosor"
        if (n == "polje" && contains(c, "kresevo"))
            return "Polje"

        println("[RadarConfig] NIJE PREPOZNATO: '$raw'")
        return null
    }

    private fun removeStreetPrefixes(n: String): String {
        val prefixes = listOf("ulica ", "ul. ", "ul ", "ulica.", "lokalna cesta ", "lok. cesta ", "aleja ", "cesta ")
        for (p in prefixes) {
            if (n.startsWith(p)) return n.removePrefix(p).trimStart()
        }
        return n
    }

    private fun contains(source: String, value: String): Boolean = source.contains(value)

    private fun stripDiacritics(text: String): String {
        return text
            .replace('č', 'c').replace('ć', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace('đ', 'd')
            .replace('Č', 'C').replace('Ć', 'C')
            .replace('Š', 'S')
            .replace('Ž', 'Z')
            .replace('Đ', 'D')
    }
}
