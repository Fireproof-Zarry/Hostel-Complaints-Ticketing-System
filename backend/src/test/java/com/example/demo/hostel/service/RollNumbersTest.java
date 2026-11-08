package com.example.demo.hostel.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollNumbersTest {

    @Test
    void readsRollNumbersFromAnExcelSheetWithNamesAndRooms() {
        RollNumbers.Parsed parsed = RollNumbers.parse("""
                Roll No,Name,Room
                CS23B051,Ragini V,214
                ee21m012,"Kumar, Arun",108
                AE20D005\tPriya\t305
                """);

        assertThat(parsed.rollNumbers()).containsExactly("cs23b051", "ee21m012", "ae20d005");
        assertThat(parsed.skippedCount()).isEqualTo(1);
        assertThat(parsed.skippedLines()).containsExactly("Roll No,Name,Room");
    }

    @Test
    void readsSmailAddressesAndCountsRepeatsOnce() {
        RollNumbers.Parsed parsed = RollNumbers.parse("cs23b051@smail.iitm.ac.in\nCS23B051\nme22b100");

        assertThat(parsed.rollNumbers()).containsExactly("cs23b051", "me22b100");
        assertThat(parsed.duplicates()).isEqualTo(1);
    }

    @Test
    void ignoresTextThatOnlyLooksSimilar() {
        RollNumbers.Parsed parsed = RollNumbers.parse("cs23b05\ncs23b0512\nxcs23b051\n2cs23b051\ncs2b0511");

        assertThat(parsed.rollNumbers()).isEmpty();
        assertThat(parsed.skippedCount()).isEqualTo(5);
    }

    @Test
    void blankLinesAreNotSkippedLines() {
        RollNumbers.Parsed parsed = RollNumbers.parse("\n\ncs23b051\r\n\r\n");

        assertThat(parsed.rollNumbers()).containsExactly("cs23b051");
        assertThat(parsed.skippedCount()).isZero();
    }

    @Test
    void normalizesARollNumberOrAddress() {
        assertThat(RollNumbers.normalize(" CS23B051 ")).isEqualTo("cs23b051");
        assertThat(RollNumbers.normalize("cs23b051@smail.iitm.ac.in")).isEqualTo("cs23b051");
        assertThat(RollNumbers.normalize("Ragini")).isNull();
        assertThat(RollNumbers.normalize(null)).isNull();
    }

    @Test
    void onlySmailAddressesHaveARollNumber() {
        assertThat(RollNumbers.fromEmail("cs23b051@smail.iitm.ac.in")).isEqualTo("cs23b051");
        assertThat(RollNumbers.fromEmail("cs23b051@gmail.com")).isNull();
        assertThat(RollNumbers.fromEmail("student@smail.iitm.ac.in")).isNull();
    }
}
