package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.model.Rationale;
import com.lldpractice.lld_practice_platform.model.SourceFile;
import com.lldpractice.lld_practice_platform.model.Submission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written good and bad solutions for all three problems, with the approach a careful reader would say each one
 * uses at each decision point. Used by the live review-quality check.
 */
final class ReviewSamples {
    private ReviewSamples() {
    }

    record Sample(String name, String problemId, boolean good, Submission submission,
                  Map<String, Set<String>> acceptableApproaches) {
    }

    private static SourceFile f(String name, String content) {
        return new SourceFile(name, content);
    }

    private static Submission sub(String used, String rejected, SourceFile... files) {
        return new Submission("java", List.of(files), "Ten floors/slots as needed; amounts in cents.", new Rationale(used, rejected, ""));
    }

    static List<Sample> all() {
        return List.of(parkingGood(), parkingBad(), elevatorGood(), elevatorBad(), vendingGood(), vendingBad());
    }

    static Sample parkingGood() {
        return new Sample("parking-good", "parking-lot", true,
                sub("Strategy for pricing and for spot allocation", "fee logic on the vehicle class",
                        f("ParkingLotService.java", """
                                import java.util.*;
                                public class ParkingLotService {
                                    private final SpotAllocator allocator;
                                    private final PricingPolicy pricing;
                                    private final Map<String, Ticket> active = new HashMap<>();
                                    public ParkingLotService(SpotAllocator allocator, PricingPolicy pricing) { this.allocator = allocator; this.pricing = pricing; }
                                    public Ticket park(Vehicle vehicle) {
                                        Spot spot = allocator.assignSpot(vehicle);
                                        Ticket t = new Ticket(spot, vehicle);
                                        active.put(t.id(), t);
                                        return t;
                                    }
                                    public Receipt unpark(Ticket ticket) {
                                        if (active.remove(ticket.id()) == null) throw new IllegalArgumentException("unknown ticket");
                                        allocator.releaseSpot(ticket.spot());
                                        return new Receipt(pricing.computeFee(ticket.vehicle(), 1));
                                    }
                                }
                                """),
                        f("Design.java", """
                                import java.util.*;
                                interface SpotAllocator { Spot assignSpot(Vehicle v); void releaseSpot(Spot s); Map<Integer, Integer> freeSpotsPerFloor(); }
                                class FirstFitAllocator implements SpotAllocator {
                                    private final List<Floor> floors = new ArrayList<>();
                                    public Spot assignSpot(Vehicle v) { for (Floor f : floors) { Spot s = f.findFree(v); if (s != null) return s; } throw new IllegalStateException("no spot fits"); }
                                    public void releaseSpot(Spot s) { s.free(); }
                                    public Map<Integer, Integer> freeSpotsPerFloor() { return new HashMap<>(); }
                                }
                                class Floor { private final List<Spot> spots = new ArrayList<>(); Spot findFree(Vehicle v) { for (Spot s : spots) if (s.canFit(v)) return s; return null; } }
                                class Spot { private SpotSize size; private boolean taken; boolean canFit(Vehicle v) { return !taken && size.fits(v.type()); } void free() { taken = false; } }
                                enum SpotSize { SMALL, MEDIUM, LARGE; boolean fits(VehicleType t) { return ordinal() >= t.ordinal(); } }
                                enum VehicleType { MOTORCYCLE, CAR, TRUCK }
                                class Vehicle { VehicleType type() { return VehicleType.CAR; } }
                                interface PricingPolicy { double computeFee(Vehicle v, long hours); }
                                class RateTablePricing implements PricingPolicy {
                                    private final Map<VehicleType, Double> rates;
                                    RateTablePricing(Map<VehicleType, Double> rates) { this.rates = rates; }
                                    public double computeFee(Vehicle v, long hours) { return rates.get(v.type()) * hours; }
                                }
                                class Ticket { Ticket(Spot s, Vehicle v) { } String id() { return ""; } Spot spot() { return null; } Vehicle vehicle() { return null; } }
                                class Receipt { Receipt(double amount) { } }
                                """)),
                Map.of("fee-computation", Set.of("pricing-policy", "rate-table"),
                        "spot-selection", Set.of("allocation-strategy", "floor-finds"),
                        "fit-decision", Set.of("can-fit", "size-compare")));
    }

    static Sample parkingBad() {
        return new Sample("parking-bad", "parking-lot", false,
                sub("none", "none",
                        f("ParkingLotService.java", """
                                import java.util.*;
                                public class ParkingLotService {
                                    public int[] floors = new int[3];
                                    public List<Object> vehicles = new ArrayList<>();
                                    public Ticket park(Vehicle v) {
                                        for (int i = 0; i < 3; i++) { if (floors[i] < 50) { floors[i]++; return new Ticket(i, v); } }
                                        return null;
                                    }
                                    public Receipt unpark(Ticket t) {
                                        double fee = 0;
                                        if (t.vehicle instanceof Car) fee = 20 * t.hours;
                                        else if (t.vehicle instanceof Truck) fee = 50 * t.hours;
                                        else if (t.vehicle instanceof Motorcycle) fee = 10 * t.hours;
                                        floors[t.floor]--;
                                        return new Receipt(fee);
                                    }
                                }
                                """),
                        f("Design.java", """
                                class Vehicle { }
                                class Car extends Vehicle { }
                                class Truck extends Vehicle { }
                                class Motorcycle extends Vehicle { }
                                class Ticket { int floor; int hours; Vehicle vehicle; Ticket(int f, Vehicle v) { floor = f; vehicle = v; } }
                                class Receipt { Receipt(double amount) { } }
                                """)),
                Map.of("fee-computation", Set.of("none", "fee-on-vehicle"),
                        "spot-selection", Set.of("none", "single-scan"),
                        "fit-decision", Set.of("none", "instanceof-chain")));
    }

    static Sample elevatorGood() {
        return new Sample("elevator-good", "elevator", true,
                sub("Strategy for dispatch; enum for car state; two sorted sets for stops", "State classes, too many for four states",
                        f("ElevatorSystem.java", """
                                import java.util.*;
                                public class ElevatorSystem {
                                    private final List<Elevator> elevators;
                                    private final DispatchStrategy dispatcher;
                                    private final int floors;
                                    public ElevatorSystem(List<Elevator> elevators, DispatchStrategy dispatcher, int floors) { this.elevators = elevators; this.dispatcher = dispatcher; this.floors = floors; }
                                    public void requestElevator(int floor, Direction direction) {
                                        if (floor < 0 || floor >= floors) throw new IllegalArgumentException("no such floor");
                                        dispatcher.pick(elevators, floor, direction).addStop(floor);
                                    }
                                    public void selectFloor(int elevatorId, int floor) {
                                        if (floor < 0 || floor >= floors) throw new IllegalArgumentException("no such floor");
                                        for (Elevator e : elevators) if (e.id() == elevatorId) e.addStop(floor);
                                    }
                                    public void step() { for (Elevator e : elevators) e.step(); }
                                }
                                """),
                        f("Design.java", """
                                import java.util.*;
                                interface DispatchStrategy { Elevator pick(List<Elevator> cars, int floor, Direction direction); }
                                class NearestCarStrategy implements DispatchStrategy {
                                    public Elevator pick(List<Elevator> cars, int floor, Direction direction) {
                                        Elevator best = cars.get(0);
                                        for (Elevator c : cars) if (Math.abs(c.floor() - floor) < Math.abs(best.floor() - floor)) best = c;
                                        return best;
                                    }
                                }
                                enum CarState { IDLE, MOVING_UP, MOVING_DOWN, DOORS_OPEN }
                                class Elevator {
                                    private static final int PASSENGER_LIMIT = 8;
                                    private final int id; private int floor; private int passengers; private CarState state = CarState.IDLE;
                                    private final TreeSet<Integer> upStops = new TreeSet<>();
                                    private final TreeSet<Integer> downStops = new TreeSet<>();
                                    Elevator(int id) { this.id = id; }
                                    int id() { return id; } int floor() { return floor; } CarState state() { return state; }
                                    void addStop(int target) { if (target >= floor) upStops.add(target); else downStops.add(target); }
                                    void board(int people) { if (passengers + people > PASSENGER_LIMIT) throw new IllegalStateException("over the passenger limit"); passengers += people; }
                                    void step() {
                                        switch (state) {
                                            case DOORS_OPEN -> state = CarState.IDLE;
                                            case IDLE, MOVING_UP, MOVING_DOWN -> move();
                                        }
                                    }
                                    private void move() {
                                        if (upStops.remove(floor) || downStops.remove(floor)) { state = CarState.DOORS_OPEN; return; }
                                        if (!upStops.isEmpty()) { floor++; state = CarState.MOVING_UP; }
                                        else if (!downStops.isEmpty()) { floor--; state = CarState.MOVING_DOWN; }
                                        else state = CarState.IDLE;
                                    }
                                }
                                """)),
                Map.of("dispatch-strategy", Set.of("nearest-car"),
                        "car-behaviour", Set.of("enum-switch"),
                        "stop-holding", Set.of("sorted-per-direction")));
    }

    static Sample elevatorBad() {
        return new Sample("elevator-bad", "elevator", false,
                sub("none", "none",
                        f("ElevatorSystem.java", """
                                import java.util.*;
                                public class ElevatorSystem {
                                    private int[] floorsOf = new int[3];
                                    private boolean[] moving = new boolean[3];
                                    private boolean[] doorsOpen = new boolean[3];
                                    private final List<Integer> queue = new LinkedList<>();
                                    private int next = 0;
                                    public void requestElevator(int floor, Direction direction) {
                                        queue.add(floor);
                                        next = (next + 1) % 3;
                                    }
                                    public void selectFloor(int elevatorId, int floor) { queue.add(floor); }
                                    public void step() {
                                        for (int i = 0; i < 3; i++) {
                                            if (!queue.isEmpty()) {
                                                int target = queue.get(0);
                                                moving[i] = true;
                                                if (floorsOf[i] < target) floorsOf[i]++; else if (floorsOf[i] > target) floorsOf[i]--; else { doorsOpen[i] = true; queue.remove(0); moving[i] = false; }
                                            }
                                        }
                                    }
                                }
                                """)),
                Map.of("dispatch-strategy", Set.of("round-robin", "fixed-rule", "none"),
                        "car-behaviour", Set.of("boolean-flags"),
                        "stop-holding", Set.of("fifo-queue")));
    }

    static Sample vendingGood() {
        return new Sample("vending-good", "vending-machine", true,
                sub("State pattern for the machine, Strategy for change, an interface for payment", "boolean flags",
                        f("VendingMachine.java", """
                                import java.util.*;
                                public class VendingMachine {
                                    private final Inventory inventory;
                                    private final ChangeStrategy changeStrategy;
                                    private Payment payment;
                                    private MachineState state = new IdleState();
                                    private String selectedCode;
                                    public VendingMachine(Inventory inventory, ChangeStrategy changeStrategy, Payment payment) { this.inventory = inventory; this.changeStrategy = changeStrategy; this.payment = payment; }
                                    void setState(MachineState s) { this.state = s; }
                                    public void selectProduct(String code) { state.select(this, code); }
                                    public void insertMoney(int denomination) { state.insert(this, denomination); }
                                    public Product dispense() { return state.dispense(this); }
                                    public void cancel() { payment.refund(); state = new IdleState(); selectedCode = null; }
                                    Inventory inventory() { return inventory; } Payment payment() { return payment; } ChangeStrategy changeStrategy() { return changeStrategy; }
                                    String selectedCode() { return selectedCode; } void select(String code) { this.selectedCode = code; }
                                }
                                """),
                        f("Design.java", """
                                import java.util.*;
                                interface MachineState { void select(VendingMachine m, String code); void insert(VendingMachine m, int denomination); Product dispense(VendingMachine m); }
                                class IdleState implements MachineState {
                                    public void select(VendingMachine m, String code) { m.inventory().require(code); m.select(code); m.setState(new HasSelectionState()); }
                                    public void insert(VendingMachine m, int d) { throw new IllegalStateException("select a product first"); }
                                    public Product dispense(VendingMachine m) { throw new IllegalStateException("select a product first"); }
                                }
                                class HasSelectionState implements MachineState {
                                    public void select(VendingMachine m, String code) { m.inventory().require(code); m.select(code); }
                                    public void insert(VendingMachine m, int d) { m.payment().accept(d); }
                                    public Product dispense(VendingMachine m) {
                                        int price = m.inventory().priceOf(m.selectedCode());
                                        if (m.payment().balance() < price) throw new IllegalStateException("insufficient money");
                                        List<Integer> change = m.changeStrategy().change(m.payment().balance() - price);
                                        m.payment().settle(price, change);
                                        m.setState(new IdleState());
                                        return m.inventory().take(m.selectedCode());
                                    }
                                }
                                interface ChangeStrategy { List<Integer> change(int amount); }
                                class GreedyChangeStrategy implements ChangeStrategy {
                                    private final List<Integer> denominations;
                                    GreedyChangeStrategy(List<Integer> denominations) { this.denominations = denominations; }
                                    public List<Integer> change(int amount) { List<Integer> out = new ArrayList<>(); for (int d : denominations) while (amount >= d) { amount -= d; out.add(d); } if (amount != 0) throw new IllegalStateException("exact change unavailable"); return out; }
                                }
                                interface Payment { void accept(int denomination); int balance(); void settle(int price, List<Integer> change); void refund(); }
                                class CashPayment implements Payment {
                                    private int balance;
                                    public void accept(int d) { balance += d; }
                                    public int balance() { return balance; }
                                    public void settle(int price, List<Integer> change) { balance = 0; }
                                    public void refund() { balance = 0; }
                                }
                                class Inventory {
                                    private final Map<String, Slot> slots = new HashMap<>();
                                    void require(String code) { Slot s = slots.get(code); if (s == null) throw new IllegalArgumentException("unknown code"); if (s.quantity() == 0) throw new IllegalStateException("sold out"); }
                                    int priceOf(String code) { return slots.get(code).price(); }
                                    Product take(String code) { return slots.get(code).take(); }
                                    void restock(String code, int quantity) { slots.get(code).add(quantity); }
                                }
                                class Slot { private int quantity; private int price; int quantity() { return quantity; } int price() { return price; } Product take() { quantity--; return new Product(); } void add(int q) { quantity += q; } }
                                """)),
                Map.of("state-behaviour", Set.of("state-pattern"),
                        "change-making", Set.of("change-strategy"),
                        "payment-model", Set.of("payment-abstraction")));
    }

    static Sample vendingBad() {
        return new Sample("vending-bad", "vending-machine", false,
                sub("none", "none",
                        f("VendingMachine.java", """
                                import java.util.*;
                                public class VendingMachine {
                                    private final Map<String, int[]> slots = new HashMap<>();
                                    private int balance;
                                    private String selected;
                                    private boolean hasSelection;
                                    public void selectProduct(String code) { selected = code; hasSelection = true; }
                                    public void insertMoney(int denomination) { if (denomination == 5 || denomination == 10 || denomination == 25 || denomination == 100) balance += denomination; }
                                    public Product dispense() {
                                        int[] slot = slots.get(selected);
                                        if (balance >= slot[0]) { slot[1]--; int change = balance - slot[0]; balance = 0; while (change >= 100) change -= 100; while (change >= 25) change -= 25; while (change >= 10) change -= 10; return new Product(); }
                                        return null;
                                    }
                                    public void cancel() { balance = 0; hasSelection = false; }
                                }
                                """)),
                Map.of("state-behaviour", Set.of("boolean-flags", "none"),
                        "change-making", Set.of("inline-greedy", "hardcoded-list"),
                        "payment-model", Set.of("coins-only")));
    }
}
