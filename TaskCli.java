import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskCli {
    private static final Path TASK_FILE = Paths.get("tasks.json");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException(usage());
        }

        String command = args[0];
        if ("add".equals(command)) {
            requireArgumentCount(args, 2, "add <description>");
            String description = requireDescription(args[1]);
            List<Task> tasks = loadTasks();
            long id = nextId(tasks);
            String now = Instant.now().toString();
            tasks.add(new Task(id, description, "todo", now, now));
            saveTasks(tasks);
            System.out.println("Task added successfully (ID: " + id + ")");
        } else if ("update".equals(command)) {
            requireArgumentCount(args, 3, "update <id> <description>");
            List<Task> tasks = loadTasks();
            int index = findTaskIndex(tasks, parseId(args[1]));
            Task task = tasks.get(index);
            tasks.set(index, new Task(task.id, requireDescription(args[2]), task.status,
                    task.createdAt, Instant.now().toString()));
            saveTasks(tasks);
            System.out.println("Task updated successfully (ID: " + task.id + ")");
        } else if ("delete".equals(command)) {
            requireArgumentCount(args, 2, "delete <id>");
            List<Task> tasks = loadTasks();
            long id = parseId(args[1]);
            int index = findTaskIndex(tasks, id);
            tasks.remove(index);
            saveTasks(tasks);
            System.out.println("Task deleted successfully (ID: " + id + ")");
        } else if ("mark-in-progress".equals(command) || "mark-done".equals(command)) {
            requireArgumentCount(args, 2, command + " <id>");
            List<Task> tasks = loadTasks();
            long id = parseId(args[1]);
            int index = findTaskIndex(tasks, id);
            Task task = tasks.get(index);
            String status = "mark-done".equals(command) ? "done" : "in-progress";
            tasks.set(index, new Task(task.id, task.description, status, task.createdAt,
                    Instant.now().toString()));
            saveTasks(tasks);
            System.out.println("Task marked as " + status + " (ID: " + id + ")");
        } else if ("list".equals(command)) {
            if (args.length > 2) {
                throw new IllegalArgumentException(usage());
            }
            String status = args.length == 2 ? parseStatus(args[1]) : null;
            listTasks(loadTasks(), status);
        } else {
            throw new IllegalArgumentException("Unknown command: " + command + "\n" + usage());
        }
    }

    private static void requireArgumentCount(String[] args, int expected, String syntax) {
        if (args.length != expected) {
            throw new IllegalArgumentException("Usage: task-cli " + syntax);
        }
    }

    private static String requireDescription(String description) {
        if (description.trim().isEmpty()) {
            throw new IllegalArgumentException("Task description cannot be empty.");
        }
        return description;
    }

    private static long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new IllegalArgumentException("Task ID must be a positive integer.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid task ID: " + value);
        }
    }

    private static String parseStatus(String status) {
        if ("todo".equals(status) || "in-progress".equals(status) || "done".equals(status)) {
            return status;
        }
        throw new IllegalArgumentException(
                "Unknown status: " + status + ". Use todo, in-progress, or done.");
    }

    private static long nextId(List<Task> tasks) {
        long maxId = 0;
        for (Task task : tasks) {
            if (task.id > maxId) {
                maxId = task.id;
            }
        }
        if (maxId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Cannot add a task: task ID limit reached.");
        }
        return maxId + 1;
    }

    private static int findTaskIndex(List<Task> tasks, long id) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == id) {
                return i;
            }
        }
        throw new IllegalArgumentException("No task found with ID " + id + ".");
    }

    private static List<Task> loadTasks() throws IOException {
        if (!Files.exists(TASK_FILE)) {
            try {
                Files.write(TASK_FILE, "[]\n".getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // Another invocation created the file first.
            }
        }

        String json = new String(Files.readAllBytes(TASK_FILE), StandardCharsets.UTF_8);
        Object parsed;
        try {
            parsed = new JsonParser(json).parse();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid tasks.json: " + e.getMessage());
        }
        if (!(parsed instanceof List<?>)) {
            throw new IllegalArgumentException("Invalid tasks.json: expected a JSON array.");
        }

        List<Task> tasks = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Object item : (List<?>) parsed) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Invalid tasks.json: each task must be an object.");
            }
            Map<?, ?> object = (Map<?, ?>) item;
            Object idValue = object.get("id");
            Object descriptionValue = object.get("description");
            Object statusValue = object.get("status");
            Object createdAtValue = object.get("createdAt");
            Object updatedAtValue = object.get("updatedAt");
            if (!(idValue instanceof Long) || ((Long) idValue) <= 0
                    || !(descriptionValue instanceof String)
                    || !(statusValue instanceof String)
                    || !(createdAtValue instanceof String)
                    || !(updatedAtValue instanceof String)) {
                throw new IllegalArgumentException(
                        "Invalid tasks.json: tasks require a positive integer id and string description, status, createdAt, and updatedAt.");
            }
            long id = (Long) idValue;
            String status = (String) statusValue;
            if (((String) descriptionValue).trim().isEmpty()
                    || !("todo".equals(status) || "in-progress".equals(status) || "done".equals(status))) {
                throw new IllegalArgumentException(
                        "Invalid tasks.json: task descriptions must be non-empty and statuses must be todo, in-progress, or done.");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Invalid tasks.json: duplicate task ID " + id + ".");
            }
            tasks.add(new Task(id, (String) descriptionValue, status,
                    (String) createdAtValue, (String) updatedAtValue));
        }
        return tasks;
    }

    private static void saveTasks(List<Task> tasks) throws IOException {
        String json = serializeTasks(tasks);
        Path destination = TASK_FILE.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), "tasks-", ".tmp");
        try {
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String serializeTasks(List<Task> tasks) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            json.append(i == 0 ? "\n" : ",\n")
                    .append("  {\n")
                    .append("    \"id\": ").append(task.id).append(",\n")
                    .append("    \"description\": ").append(quote(task.description)).append(",\n")
                    .append("    \"status\": ").append(quote(task.status)).append(",\n")
                    .append("    \"createdAt\": ").append(quote(task.createdAt)).append(",\n")
                    .append("    \"updatedAt\": ").append(quote(task.updatedAt)).append("\n")
                    .append("  }");
        }
        if (!tasks.isEmpty()) {
            json.append('\n');
        }
        return json.append("]\n").toString();
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.append('"').toString();
    }

    private static void listTasks(List<Task> tasks, String status) {
        boolean found = false;
        for (Task task : tasks) {
            if (status == null || status.equals(task.status)) {
                System.out.println(task.id + " | " + task.status + " | " + task.description);
                found = true;
            }
        }
        if (!found) {
            System.out.println("No tasks found.");
        }
    }

    private static String usage() {
        return "Usage:\n"
                + "  task-cli add <description>\n"
                + "  task-cli update <id> <description>\n"
                + "  task-cli delete <id>\n"
                + "  task-cli mark-in-progress <id>\n"
                + "  task-cli mark-done <id>\n"
                + "  task-cli list [todo|in-progress|done]";
    }

    private static final class Task {
        private final long id;
        private final String description;
        private final String status;
        private final String createdAt;
        private final String updatedAt;

        private Task(long id, String description, String status, String createdAt, String updatedAt) {
            this.id = id;
            this.description = description;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    private static final class JsonParser {
        private final String input;
        private int position;

        private JsonParser(String input) {
            this.input = input;
        }

        private Object parse() {
            skipWhitespace();
            Object value = readValue();
            skipWhitespace();
            if (position != input.length()) {
                fail("unexpected content after the JSON value");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (position >= input.length()) {
                fail("expected a JSON value");
            }
            char c = input.charAt(position);
            if (c == '{') {
                return readObject();
            }
            if (c == '[') {
                return readArray();
            }
            if (c == '"') {
                return readString();
            }
            if (c == 't') {
                return readLiteral("true", Boolean.TRUE);
            }
            if (c == 'f') {
                return readLiteral("false", Boolean.FALSE);
            }
            if (c == 'n') {
                return readLiteral("null", null);
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return readNumber();
            }
            fail("unexpected character '" + c + "'");
            return null;
        }

        private Map<String, Object> readObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> object = new LinkedHashMap<>();
            if (take('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (position >= input.length() || input.charAt(position) != '"') {
                    fail("expected a string key");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                if (object.containsKey(key)) {
                    fail("duplicate object key " + quote(key));
                }
                object.put(key, value);
                skipWhitespace();
                if (take('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            skipWhitespace();
            List<Object> array = new ArrayList<>();
            if (take(']')) {
                return array;
            }
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (take(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < input.length()) {
                char c = input.charAt(position++);
                if (c == '"') {
                    return value.toString();
                }
                if (c == '\\') {
                    if (position >= input.length()) {
                        fail("incomplete escape sequence");
                    }
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            value.append(escaped);
                            break;
                        case 'b':
                            value.append('\b');
                            break;
                        case 'f':
                            value.append('\f');
                            break;
                        case 'n':
                            value.append('\n');
                            break;
                        case 'r':
                            value.append('\r');
                            break;
                        case 't':
                            value.append('\t');
                            break;
                        case 'u':
                            value.append(readUnicodeEscape());
                            break;
                        default:
                            fail("invalid escape sequence");
                    }
                } else {
                    if (c < 0x20) {
                        fail("unescaped control character in string");
                    }
                    value.append(c);
                }
            }
            fail("unterminated string");
            return null;
        }

        private char readUnicodeEscape() {
            if (position + 4 > input.length()) {
                fail("incomplete Unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(position++), 16);
                if (digit < 0) {
                    fail("invalid Unicode escape");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Object readNumber() {
            int start = position;
            if (take('-') && position >= input.length()) {
                fail("invalid number");
            }
            if (take('0')) {
                if (position < input.length() && Character.isDigit(input.charAt(position))) {
                    fail("leading zero in number");
                }
            } else {
                readDigits();
            }
            boolean decimal = false;
            if (take('.')) {
                decimal = true;
                readDigits();
            }
            if (take('e') || take('E')) {
                decimal = true;
                if (!take('+')) {
                    take('-');
                }
                readDigits();
            }
            String number = input.substring(start, position);
            try {
                return decimal ? new BigDecimal(number) : Long.valueOf(number);
            } catch (NumberFormatException e) {
                fail("number is out of range");
                return null;
            }
        }

        private void readDigits() {
            int start = position;
            while (position < input.length() && input.charAt(position) >= '0'
                    && input.charAt(position) <= '9') {
                position++;
            }
            if (start == position) {
                fail("expected a digit");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!input.regionMatches(position, literal, 0, literal.length())) {
                fail("invalid JSON value");
            }
            position += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (position < input.length()) {
                char c = input.charAt(position);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private boolean take(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                fail("expected '" + expected + "'");
            }
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at character " + position);
        }
    }
}
