package com.lldpractice.lld_practice_platform.problem;

import com.lldpractice.lld_practice_platform.model.SourceFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Loads every problem file at startup and fails fast, naming the file and the missing field. */
@Component
public class YamlProblemRepository implements ProblemRepository {
    private final Map<String, Problem> byId = new LinkedHashMap<>();

    public YamlProblemRepository(@Value("${lld.problems.location:classpath:problems/*.yml}") String location) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list problem files at " + location, e);
        }
        Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
        for (Resource r : resources) {
            String file = String.valueOf(r.getFilename());
            Problem p = load(file, r);
            if (byId.putIfAbsent(p.id(), p) != null) {
                throw new IllegalStateException("Problem file " + file + ": duplicate problem id '" + p.id() + "'");
            }
        }
        if (byId.isEmpty()) {
            throw new IllegalStateException("No problem files found at " + location);
        }
    }

    @Override
    public List<Problem> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public Optional<Problem> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    private static Problem load(String file, Resource r) {
        Object root;
        try (InputStream in = r.getInputStream()) {
            root = new Yaml().load(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Problem file " + file + " is not valid YAML: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Problem file " + file + ": the top level must be a mapping");
        }
        Reader rd = new Reader(file);
        String id = rd.text(map, "id", "id");
        String title = rd.text(map, "title", "title");
        String statement = rd.text(map, "statement", "statement");
        List<String> requirements = rd.texts(map, "requirements", "requirements");
        List<String> entryTypes = rd.texts(map, "entryTypes", "entryTypes");
        List<SourceFile> skeleton = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> f : rd.maps(map, "skeleton", "skeleton")) {
            String path = "skeleton[" + i++ + "]";
            skeleton.add(new SourceFile(rd.text(f, "name", path + ".name"), rd.text(f, "content", path + ".content")));
        }
        List<Capability> caps = new ArrayList<>();
        for (Map<?, ?> c : rd.maps(map, "capabilities", "capabilities")) {
            String path = "capabilities[" + caps.size() + "]";
            caps.add(new Capability(rd.text(c, "id", path + ".id"), rd.text(c, "description", path + ".description"),
                    rd.texts(c, "nameHints", path + ".nameHints"), Boolean.TRUE.equals(c.get("watchLiterals"))));
        }
        List<DecisionPoint> dps = new ArrayList<>();
        for (Map<?, ?> d : rd.maps(map, "decisionPoints", "decisionPoints")) {
            String path = "decisionPoints[" + dps.size() + "]";
            List<Approach> approaches = new ArrayList<>();
            for (Map<?, ?> a : rd.maps(d, "approaches", path + ".approaches")) {
                String ap = path + ".approaches[" + approaches.size() + "]";
                approaches.add(new Approach(rd.text(a, "id", ap + ".id"), rd.text(a, "name", ap + ".name"),
                        rd.optionalTexts(a, "signals"), rd.text(a, "tradeoff", ap + ".tradeoff")));
            }
            dps.add(new DecisionPoint(rd.text(d, "id", path + ".id"), rd.text(d, "question", path + ".question"),
                    approaches));
        }
        Map<?, ?> cr = rd.map(map, "changeRequest", "changeRequest");
        ChangeRequest change = new ChangeRequest(rd.text(cr, "id", "changeRequest.id"),
                rd.text(cr, "text", "changeRequest.text"));
        return new Problem(id, title, statement, requirements, skeleton, entryTypes, caps, dps, change);
    }

    private record Reader(String file) {
        private IllegalStateException missing(String path) {
            return new IllegalStateException("Problem file " + file + ": missing or invalid field '" + path + "'");
        }

        String text(Map<?, ?> m, String key, String path) {
            Object v = m.get(key);
            if (!(v instanceof String s) || s.isBlank()) throw missing(path);
            return s;
        }

        Map<?, ?> map(Map<?, ?> m, String key, String path) {
            if (!(m.get(key) instanceof Map<?, ?> v)) throw missing(path);
            return v;
        }

        List<String> texts(Map<?, ?> m, String key, String path) {
            if (!(m.get(key) instanceof List<?> l) || l.isEmpty()) throw missing(path);
            List<String> out = new ArrayList<>();
            for (Object o : l) {
                if (!(o instanceof String s) || s.isBlank()) throw missing(path);
                out.add(s);
            }
            return out;
        }

        List<String> optionalTexts(Map<?, ?> m, String key) {
            if (!(m.get(key) instanceof List<?> l)) return List.of();
            return l.stream().map(String::valueOf).toList();
        }

        List<Map<?, ?>> maps(Map<?, ?> m, String key, String path) {
            if (!(m.get(key) instanceof List<?> l) || l.isEmpty()) throw missing(path);
            List<Map<?, ?>> out = new ArrayList<>();
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> mm)) throw missing(path);
                out.add(mm);
            }
            return out;
        }
    }
}
