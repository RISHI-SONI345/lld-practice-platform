package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.*;

import java.util.List;

/** Hand-written good and bad Parking Lot solutions used across tests. */
public final class Samples {
    private Samples() {
    }

    public static Submission sub(String rationaleUsed, SourceFile... files) {
        return new Submission(Submission.JAVA, List.of(files), "Assume one entrance.",
                new Rationale(rationaleUsed, "fee on the vehicle class", ""));
    }

    private static SourceFile f(String name, String content) {
        return new SourceFile(name, content);
    }

    /** A clean design: interfaces for allocation and pricing, rates as data. */
    public static Submission goodParkingLot() {
        return sub("Strategy for pricing",
                f("ParkingLotService.java", """
                        import java.util.*;
                        public class ParkingLotService {
                            private final SpotAllocator allocator;
                            private final PricingPolicy pricing;
                            private final Map<String, Ticket> active = new HashMap<>();
                            public ParkingLotService(SpotAllocator allocator, PricingPolicy pricing) {
                                this.allocator = allocator;
                                this.pricing = pricing;
                            }
                            public Ticket park(Vehicle vehicle) {
                                Spot spot = allocator.assignSpot(vehicle);
                                Ticket ticket = new Ticket(spot, vehicle);
                                active.put(ticket.id(), ticket);
                                return ticket;
                            }
                            public Receipt unpark(Ticket ticket) {
                                if (active.remove(ticket.id()) == null) throw new IllegalArgumentException("unknown ticket");
                                allocator.releaseSpot(ticket.spot());
                                return new Receipt(pricing.computeFee(ticket.vehicle(), 1));
                            }
                            public int freeSpots() { return allocator.availableSpots(); }
                        }
                        """),
                f("SpotAllocator.java", "public interface SpotAllocator { Spot assignSpot(Vehicle v); void releaseSpot(Spot s); int availableSpots(); }"),
                f("FirstFitAllocator.java", """
                        import java.util.*;
                        public class FirstFitAllocator implements SpotAllocator {
                            private final List<Spot> spots = new ArrayList<>();
                            public Spot assignSpot(Vehicle v) { return spots.get(0); }
                            public void releaseSpot(Spot s) { }
                            public int availableSpots() { return spots.size(); }
                        }
                        """),
                f("PricingPolicy.java", "public interface PricingPolicy { double computeFee(Vehicle v, long hours); }"),
                f("HourlyPricing.java", """
                        import java.util.*;
                        public class HourlyPricing implements PricingPolicy {
                            private final Map<VehicleType, Double> rates = new HashMap<>();
                            public double computeFee(Vehicle v, long hours) { return rates.get(v.type()) * hours; }
                        }
                        """),
                f("Vehicle.java", "public class Vehicle { public VehicleType type() { return null; } }"),
                f("VehicleType.java", "public enum VehicleType { MOTORCYCLE, CAR, TRUCK }"),
                f("Spot.java", "public class Spot { public boolean canFit(Vehicle v) { return true; } }"),
                f("Ticket.java", "public class Ticket { public Ticket(Spot s, Vehicle v) { } public String id() { return \"\"; } public Spot spot() { return null; } public Vehicle vehicle() { return null; } }"),
                f("Receipt.java", "public class Receipt { public Receipt(double amount) { } }"));
    }

    /** A weak design: one class, switch on vehicle type, magic numbers, no release or capacity owner. */
    public static Submission weakParkingLot() {
        return sub("none",
                f("ParkingLotService.java", """
                        public class ParkingLotService {
                            public Ticket park(Vehicle vehicle) { return new Ticket(); }
                            public Receipt unpark(Ticket ticket) {
                                double fee = computeFee(ticket);
                                return new Receipt();
                            }
                            double computeFee(Ticket ticket) {
                                double fee = 0;
                                switch (ticket.vehicleType) {
                                    case 1: fee = 20 * 3; break;
                                    case 2: fee = 40; break;
                                    default: fee = 80;
                                }
                                return fee;
                            }
                        }
                        """),
                f("Vehicle.java", "public class Vehicle { }"),
                f("Ticket.java", "public class Ticket { public int vehicleType; }"),
                f("Receipt.java", "public class Receipt { }"));
    }

    /** The good design, with a service-class tweak so the change round has something to compare. */
    public static Submission goodParkingLotWithEvSpots() {
        Submission base = goodParkingLot();
        java.util.ArrayList<SourceFile> files = new java.util.ArrayList<>(base.files());
        files.add(f("EvSpot.java", "public class EvSpot extends Spot { public int chargingRate() { return 7; } }"));
        files.add(f("ChargingPolicy.java", "public interface ChargingPolicy { double chargeFee(long minutes); }"));
        return new Submission(Submission.JAVA, files, base.assumptions(), base.rationale());
    }

    public static final String GOOD_REVIEW_JSON = """
            {"findings":[{"category":"TRADEOFF","severity":"LOW","subject":"ParkingLotService",
              "claim":"The service tracks active tickets itself.","why":"It adds a second reason to change.",
              "fix":"Move ticket bookkeeping to a TicketRegistry.","tradeoff":"Fine while the lot is small."}],
             "decisionPoints":[{"id":"fee-computation","approachUsed":"pricing-policy","agreesWithRationale":true,
               "tradeoff":"New vehicle types need a rate entry only."},
               {"id":"not-a-real-decision","approachUsed":"x","agreesWithRationale":false,"tradeoff":"y"}],
             "strengths":["Pricing and allocation sit behind interfaces."]}
            """;
}
