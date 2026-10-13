package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

/** Finds strongly connected components of size two or more over HAS_A and USES edges (Tarjan). */
@Component
public class DependencyCycleRule implements Rule {
    @Override
    public String id() {
        return "DEPENDENCY_CYCLE";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        model.classes().keySet().forEach(n -> graph.put(n, new ArrayList<>()));
        for (Relationship r : model.relationships()) {
            if ((r.kind() == RelationshipKind.HAS_A || r.kind() == RelationshipKind.USES)
                    && graph.containsKey(r.from()) && graph.containsKey(r.to()) && !r.from().equals(r.to())) {
                graph.get(r.from()).add(r.to());
            }
        }
        List<Finding> out = new ArrayList<>();
        for (List<String> scc : new Tarjan(graph).run()) {
            if (scc.size() < 2) continue;
            List<String> members = new ArrayList<>(scc);
            Collections.sort(members);
            Set<String> set = new HashSet<>(members);
            List<SourceRef> evidence = model.relationships().stream()
                    .filter(r -> set.contains(r.from()) && set.contains(r.to()) && !r.from().equals(r.to())
                            && (r.kind() == RelationshipKind.HAS_A || r.kind() == RelationshipKind.USES))
                    .map(Relationship::source).distinct().toList();
            out.add(fact(id(), Severity.MEDIUM, members.get(0),
                    "These classes depend on each other in a cycle: " + String.join(", ", members)
                            + ". Check whether a parent-child link is intended.",
                    "Classes in a cycle cannot be understood, tested or changed one at a time.",
                    "Break the cycle: pass an id instead of the object, or put the shared behaviour behind an interface.",
                    evidence));
        }
        return out;
    }

    private static final class Tarjan {
        private final Map<String, List<String>> g;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> result = new ArrayList<>();
        private int counter = 0;

        Tarjan(Map<String, List<String>> g) {
            this.g = g;
        }

        List<List<String>> run() {
            for (String v : g.keySet()) if (!index.containsKey(v)) visit(v);
            return result;
        }

        private void visit(String v) {
            index.put(v, counter);
            low.put(v, counter);
            counter++;
            stack.push(v);
            onStack.add(v);
            for (String w : g.get(v)) {
                if (!index.containsKey(w)) {
                    visit(w);
                    low.put(v, Math.min(low.get(v), low.get(w)));
                } else if (onStack.contains(w)) {
                    low.put(v, Math.min(low.get(v), index.get(w)));
                }
            }
            if (low.get(v).equals(index.get(v))) {
                List<String> comp = new ArrayList<>();
                String w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    comp.add(w);
                } while (!w.equals(v));
                result.add(comp);
            }
        }
    }
}
