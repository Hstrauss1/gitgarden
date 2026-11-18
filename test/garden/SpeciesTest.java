package garden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeciesTest {

    @Test
    void fromNameFindsEverySpeciesByDisplayNameAndEnumName() {
        for (Species species : Species.values()) {
            assertEquals(species, Species.fromName(species.displayName));
            assertEquals(species, Species.fromName(species.name()));
        }
    }

    @Test
    void fromNameIgnoresCaseSpacesHyphensAndUnderscores() {
        assertEquals(Species.CHERRY, Species.fromName("cherry blossom"));
        assertEquals(Species.CHERRY, Species.fromName("  CHERRY BLOSSOM  "));
        assertEquals(Species.CHERRY, Species.fromName("cherry_blossom"));
        assertEquals(Species.CHERRY, Species.fromName("Cherry-Blossom"));
        assertEquals(Species.CHERRY, Species.fromName("CherryBlossom"));
        assertEquals(Species.MAPLE, Species.fromName("japanese maple"));
        assertEquals(Species.MAPLE, Species.fromName("maple"));
        assertEquals(Species.PINE, Species.fromName("pInE"));
        assertEquals(Species.JUNIPER, Species.fromName("juniper\t"));
    }

    @Test
    void fromNameReturnsNullForUnknownOrMissingNames() {
        assertNull(Species.fromName("Rose"));
        assertNull(Species.fromName("Tomato"));
        assertNull(Species.fromName("Cherry Blossoms Tree"));
        assertNull(Species.fromName(""));
        assertNull(Species.fromName("   "));
        assertNull(Species.fromName(null));
    }

    @Test
    void parasiteNamesMatchIgnoringCasePluralsAndSpacing() {
        assertTrue(Species.CHERRY.isVulnerableTo("aphids"));
        assertTrue(Species.CHERRY.isVulnerableTo("Aphids"));
        assertTrue(Species.CHERRY.isVulnerableTo("APHID"));
        assertTrue(Species.CHERRY.isVulnerableTo("  aphids "));
        assertTrue(Species.JUNIPER.isVulnerableTo("Spider Mites"));
        assertTrue(Species.JUNIPER.isVulnerableTo("spider-mite"));
        assertTrue(Species.JUNIPER.isVulnerableTo("spider_mites"));
        assertTrue(Species.PINE.isVulnerableTo("pine beetle"));
    }

    @Test
    void parasiteNamesDoNotMatchOtherSpeciesOrPartialNames() {
        assertFalse(Species.JUNIPER.isVulnerableTo("aphids"));
        assertFalse(Species.PINE.isVulnerableTo("caterpillars"));
        assertFalse(Species.MAPLE.isVulnerableTo("scale"));      // only "scale insects"
        assertFalse(Species.MAPLE.isVulnerableTo("insects"));
        assertFalse(Species.CHERRY.isVulnerableTo("mites"));
        assertFalse(Species.CHERRY.isVulnerableTo(""));
        assertFalse(Species.CHERRY.isVulnerableTo(null));
    }

    @Test
    void everySpeciesHasParasitesAndAWaterRequirement() {
        for (Species species : Species.values()) {
            assertFalse(species.getParasites().isEmpty(), species.displayName);
            assertTrue(species.waterRequirement > 0, species.displayName);
            for (String parasite : species.getParasites()) {
                assertTrue(species.isVulnerableTo(parasite), species.displayName + " / " + parasite);
            }
        }
    }

    @Test
    void parasiteListCannotBeChanged() {
        assertThrows(UnsupportedOperationException.class, () -> Species.PINE.getParasites().add("rabbits"));
    }
}
