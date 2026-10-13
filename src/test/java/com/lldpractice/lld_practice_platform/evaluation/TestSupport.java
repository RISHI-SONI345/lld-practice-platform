package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.javacode.JavaCodeParser;
import com.lldpractice.lld_practice_platform.problem.*;

import java.util.List;

/** Shared helpers for tests: a tiny problem and a quick way to parse code into a DesignModel. */
public final class TestSupport {
    private TestSupport() {
    }

    public static Submission sub(String... nameAndContent) {
        java.util.ArrayList<SourceFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            files.add(new SourceFile(nameAndContent[i], nameAndContent[i + 1]));
        }
        return new Submission(Submission.JAVA, files, "", Rationale.empty());
    }

    public static DesignModel model(String code) {
        return new JavaCodeParser().parse(sub("A.java", code)).model();
    }

    public static Problem problem() {
        return new Problem("parking-lot", "Parking Lot", "stmt", List.of("r1"), List.of(),
                List.of("ParkingLotService"),
                List.of(new Capability("assign-spot", "assign a spot", List.of("assign", "allocate", "find", "park"), false),
                        new Capability("compute-fee", "compute the fee", List.of("fee", "charge", "price"), true),
                        new Capability("track-capacity", "track capacity", List.of("capacity", "available"), true)),
                List.of(),
                new ChangeRequest("ev", "add EV spots"));
    }
}
