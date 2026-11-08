package com.example.demo.hostel.service;

import com.example.demo.hostel.dto.ResidentListChange;
import com.example.demo.hostel.model.Resident;
import com.example.demo.hostel.repository.ResidentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resident-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ResidentServiceIntegrationTest {

    private static final String ADMIN = "admin@smail.iitm.ac.in";

    @Autowired private ResidentService residentService;
    @Autowired private ResidentRepository residentRepository;

    @AfterEach
    void tearDown() {
        residentRepository.deleteAllInBatch();
        residentService.reload();
    }

    @Test
    void anEmptyListLetsEverySmailStudentIn() {
        assertThat(residentService.isResident("cs23b051@smail.iitm.ac.in")).isTrue();
        assertThat(residentService.isResident("student@smail.iitm.ac.in")).isTrue();
    }

    @Test
    void onceTheListIsUploadedOnlyResidentsGetIn() {
        residentService.replace("CS23B051\nEE21M012", ADMIN);

        assertThat(residentService.isResident("cs23b051@smail.iitm.ac.in")).isTrue();
        assertThat(residentService.isResident("EE21M012@smail.iitm.ac.in")).isTrue();
        assertThat(residentService.isResident("me22b100@smail.iitm.ac.in")).isFalse();
        assertThat(residentService.isResident("student@smail.iitm.ac.in")).isFalse();
    }

    @Test
    void previewShowsTheChangeWithoutSavingIt() {
        residentService.replace("cs23b051\nee21m012", ADMIN);

        ResidentListChange preview = residentService.preview("Roll No\ncs23b051\nme22b100\nme22b100");

        assertThat(preview.applied()).isFalse();
        assertThat(preview.found()).isEqualTo(2);
        assertThat(preview.added()).isEqualTo(1);
        assertThat(preview.addedSample()).containsExactly("me22b100");
        assertThat(preview.removed()).isEqualTo(1);
        assertThat(preview.removedSample()).containsExactly("ee21m012");
        assertThat(preview.unchanged()).isEqualTo(1);
        assertThat(preview.duplicates()).isEqualTo(1);
        assertThat(preview.skippedLines()).containsExactly("Roll No");
        assertThat(residentRepository.findAllRollNumbers()).containsExactlyInAnyOrder("cs23b051", "ee21m012");
    }

    @Test
    void replaceSwapsTheWholeList() {
        residentService.replace("cs23b051\nee21m012", ADMIN);

        ResidentListChange change = residentService.replace("cs23b051\nme22b100", ADMIN);

        assertThat(change.applied()).isTrue();
        assertThat(change.added()).isEqualTo(1);
        assertThat(change.removed()).isEqualTo(1);
        assertThat(residentRepository.findAllRollNumbers()).containsExactlyInAnyOrder("cs23b051", "me22b100");
        assertThat(residentService.isResident("ee21m012@smail.iitm.ac.in")).isFalse();
        Resident added = residentRepository.findById("me22b100").orElseThrow();
        assertThat(added.getAddedBy()).isEqualTo(ADMIN);
    }

    @Test
    void textWithoutRollNumbersCannotEmptyTheList() {
        residentService.replace("cs23b051", ADMIN);

        assertThatThrownBy(() -> residentService.replace("Name,Room\nRagini,214", ADMIN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(residentRepository.findAllRollNumbers()).containsExactly("cs23b051");
    }

    @Test
    void addAndRemoveOneStudent() {
        residentService.replace("cs23b051", ADMIN);

        assertThat(residentService.add("ME22B100@smail.iitm.ac.in", ADMIN)).isEqualTo("me22b100");
        assertThat(residentService.isResident("me22b100@smail.iitm.ac.in")).isTrue();

        residentService.remove("ME22B100");
        assertThat(residentService.isResident("me22b100@smail.iitm.ac.in")).isFalse();
    }

    @Test
    void addingTwiceOrRemovingSomeoneNotListedIsRefused() {
        residentService.add("cs23b051", ADMIN);

        assertThatThrownBy(() -> residentService.add("CS23B051", ADMIN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> residentService.remove("ee21m012"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> residentService.add("Ragini", ADMIN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void clearingTheListLetsEveryoneInAgain() {
        residentService.replace("cs23b051\nee21m012", ADMIN);

        assertThat(residentService.clear(ADMIN)).isEqualTo(2);
        assertThat(residentService.isResident("me22b100@smail.iitm.ac.in")).isTrue();
    }

    @Test
    void searchFindsMatchingRollNumbers() {
        residentService.replace("cs23b051\ncs23b052\nee21m012", ADMIN);

        assertThat(residentService.summary("CS23").count()).isEqualTo(3);
        assertThat(residentService.summary("CS23").matches()).containsExactly("cs23b051", "cs23b052");
        assertThat(residentService.summary(null).matches()).isEmpty();
    }
}
