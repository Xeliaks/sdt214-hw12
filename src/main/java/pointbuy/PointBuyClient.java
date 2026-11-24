package pointbuy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Swing GUI that talks to the point-buy REST service. Start with: gradlew runClient */
public class PointBuyClient {

    static final String BASE_URL = "http://localhost:8090";

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<Ability, String> NAMES = Map.of(
            Ability.STR, "Strength", Ability.DEX, "Dexterity", Ability.CON, "Constitution",
            Ability.INT, "Intelligence", Ability.WIS, "Wisdom", Ability.CHA, "Charisma");

    private final HttpClient http = HttpClient.newHttpClient();
    private final JFrame frame = new JFrame("5e Point Buy Calculator");
    private final JComboBox<Background> background = new JComboBox<>();
    private final JRadioButton twoOne = new JRadioButton("+2 / +1", true);
    private final JRadioButton oneOneOne = new JRadioButton("+1 / +1 / +1");
    private final JComboBox<Ability> plus2 = new JComboBox<>();
    private final JComboBox<Ability> plus1 = new JComboBox<>();
    private final Map<Ability, JSpinner> spinners = new EnumMap<>(Ability.class);
    private final Map<Ability, JLabel[]> cells = new EnumMap<>(Ability.class); // bonus, total, modifier, cost
    private final JLabel points = new JLabel(" ");
    private final JLabel problems = new JLabel(" ");
    private final JTextField name = new JTextField(16);
    private final DefaultListModel<String> saved = new DefaultListModel<>();
    private boolean updating; // true while combos are refilled, so they don't each fire an evaluate
    private int seq;          // ignore evaluate answers that arrive after a newer one

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PointBuyClient().start());
    }

    /** Bonus map from the UI choices: +2/+1 on the picked abilities, or +1 on all three. */
    static Map<Ability, Integer> bonuses(Background bg, boolean twoOne, Ability plus2, Ability plus1) {
        Map<Ability, Integer> m = new EnumMap<>(Ability.class);
        if (bg == null) return m;
        if (!twoOne) {
            bg.abilities().forEach(a -> m.put(a, 1));
            return m;
        }
        if (plus2 != null) m.put(plus2, 2);
        if (plus1 != null) m.put(plus1, 1);
        return m;
    }

    static String signed(int n) {
        return (n >= 0 ? "+" : "") + n;
    }

    private void start() {
        DefaultListCellRenderer text = new DefaultListCellRenderer();
        background.setRenderer((list, bg, i, sel, focus) -> text.getListCellRendererComponent(list,
                bg == null ? "Select" : bg.name() + " (" + bg.abilities().stream().map(Enum::name)
                        .collect(Collectors.joining(", ")) + ")", i, sel, focus));
        ButtonGroup mode = new ButtonGroup();
        mode.add(twoOne);
        mode.add(oneOneOne);

        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bgRow.add(new JLabel("Select Background:"));
        bgRow.add(background);
        JPanel bonusRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bonusRow.add(new JLabel("Bonus:"));
        bonusRow.add(twoOne);
        bonusRow.add(oneOneOne);
        bonusRow.add(new JLabel("   +2 to"));
        bonusRow.add(plus2);
        bonusRow.add(new JLabel("+1 to"));
        bonusRow.add(plus1);

        JPanel grid = new JPanel(new GridLayout(0, 6, 8, 4));
        for (String h : List.of("Attribute", "Ability Score", "Bonus", "Total Score", "Modifier", "Point Cost")) {
            JLabel l = new JLabel(h, SwingConstants.CENTER);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            grid.add(l);
        }
        for (Ability a : Ability.values()) {
            JSpinner s = new JSpinner(new SpinnerNumberModel(8, 8, 15, 1));
            s.addChangeListener(e -> evaluate());
            spinners.put(a, s);
            JLabel[] c = new JLabel[4];
            for (int i = 0; i < 4; i++) c[i] = new JLabel("", SwingConstants.CENTER);
            c[1].setFont(c[1].getFont().deriveFont(Font.BOLD, 16f));
            cells.put(a, c);
            grid.add(new JLabel(NAMES.get(a)));
            grid.add(s);
            for (JLabel l : c) grid.add(l);
        }

        points.setFont(points.getFont().deriveFont(Font.BOLD, 15f));
        problems.setForeground(new Color(0xB0, 0, 0));
        JButton saveButton = new JButton("Save build");
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        saveRow.add(new JLabel("Name:"));
        saveRow.add(name);
        saveRow.add(saveButton);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (var c : List.of(bgRow, bonusRow, grid, points, problems, saveRow)) {
            c.setAlignmentX(0);
            top.add(c);
        }

        JList<String> list = new JList<>(saved);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Saved Builds"));

        frame.add(top, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(720, 640);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        background.addActionListener(e -> onBackground());
        plus2.addActionListener(e -> { fillPlus1(); evaluate(); });
        plus1.addActionListener(e -> evaluate());
        twoOne.addActionListener(e -> onMode());
        oneOneOne.addActionListener(e -> onMode());
        saveButton.addActionListener(e -> save());

        send("GET", "/backgrounds", null, r -> {
            updating = true;
            background.addItem(null);
            parse(r, new TypeReference<List<Background>>() {}).forEach(background::addItem);
            updating = false;
            evaluate();
        });
        loadSaved();
    }

    private void onBackground() {
        if (updating) return;
        updating = true;
        plus2.removeAllItems();
        Background bg = (Background) background.getSelectedItem();
        if (bg != null) bg.abilities().forEach(plus2::addItem);
        fillPlus1();
        updating = false;
        evaluate();
    }

    /** +1 can go on either of the background's abilities that didn't get the +2. */
    private void fillPlus1() {
        boolean was = updating;
        updating = true;
        plus1.removeAllItems();
        Background bg = (Background) background.getSelectedItem();
        if (bg != null) {
            bg.abilities().stream().filter(a -> a != plus2.getSelectedItem()).forEach(plus1::addItem);
        }
        updating = was;
    }

    private void onMode() {
        plus2.setEnabled(twoOne.isSelected());
        plus1.setEnabled(twoOne.isSelected());
        evaluate();
    }

    private BuildRequest buildRequest() {
        Map<Ability, Integer> scores = new EnumMap<>(Ability.class);
        spinners.forEach((a, s) -> scores.put(a, (Integer) s.getValue()));
        Background bg = (Background) background.getSelectedItem();
        return new BuildRequest(name.getText(), bg == null ? null : bg.name(), scores,
                bonuses(bg, twoOne.isSelected(), (Ability) plus2.getSelectedItem(), (Ability) plus1.getSelectedItem()));
    }

    private void evaluate() {
        if (updating) return;
        int mine = ++seq;
        send("POST", "/builds/evaluate", buildRequest(), r -> {
            if (mine == seq) show(parse(r, new TypeReference<BuildResult>() {}));
        });
    }

    private void show(BuildResult result) {
        for (AbilityLine l : result.abilities()) {
            JLabel[] c = cells.get(l.ability());
            c[0].setText(String.valueOf(l.bonus()));
            c[1].setText(String.valueOf(l.total()));
            c[2].setText(signed(l.modifier()));
            c[3].setText(String.valueOf(l.cost()));
        }
        points.setText("Total Points: " + result.pointsSpent() + " / " + PointBuyRules.BUDGET);
        points.setForeground(result.pointsRemaining() < 0 ? problems.getForeground() : null);
        problems.setText(result.problems().isEmpty() ? " "
                : "<html>" + String.join("<br>", result.problems()) + "</html>");
    }

    private void save() {
        send("POST", "/builds", buildRequest(), r -> {
            if (r.statusCode() == 400) {
                BuildResult result = read(r.body(), new TypeReference<BuildResult>() {});
                JOptionPane.showMessageDialog(frame, "Can't save this build:\n• "
                        + String.join("\n• ", result.problems()), "Point Buy", JOptionPane.WARNING_MESSAGE);
                return;
            }
            BuildResult result = parse(r, new TypeReference<BuildResult>() {});
            JOptionPane.showMessageDialog(frame, "Saved \"" + result.name() + "\" as #" + result.id());
            loadSaved();
        });
    }

    private void loadSaved() {
        send("GET", "/builds", null, r -> {
            saved.clear();
            parse(r, new TypeReference<List<BuildResult>>() {}).forEach(b -> saved.addElement(savedRow(b)));
        });
    }

    static String savedRow(BuildResult b) {
        String totals = b.abilities().stream()
                .map(l -> l.ability() + " " + String.format("%2d", l.total()))
                .collect(Collectors.joining("  "));
        return String.format("#%-3d %-14.14s %-11s %s   %s", b.id(), b.name(), b.background(), totals,
                TIME.format(b.savedAt()));
    }

    /** Body of a 2xx answer as T; anything else is an error. */
    private static <T> T parse(HttpResponse<String> r, TypeReference<T> type) {
        if (r.statusCode() / 100 != 2) {
            throw new UncheckedIOException(new IOException("Service answered HTTP " + r.statusCode()));
        }
        return read(r.body(), type);
    }

    private static <T> T read(String body, TypeReference<T> type) {
        try {
            return JSON.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Calls the service off the UI thread, then hands the response to onDone on the UI thread. */
    private void send(String method, String path, Object body, Consumer<HttpResponse<String>> onDone) {
        new SwingWorker<HttpResponse<String>, Void>() {
            @Override
            protected HttpResponse<String> doInBackground() throws Exception {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(5)); // a hung service can't freeze the client forever
                if (body == null) {
                    b.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    b.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
                }
                return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            }

            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(e.getCause());
                } catch (RuntimeException e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void showError(Throwable cause) {
        String message = "Can't reach the point buy service at " + BASE_URL
                + ".\nStart it first with: gradlew bootRun";
        if (!(cause instanceof ConnectException)) {
            message += "\n\n(" + cause.getMessage() + ")";
        }
        JOptionPane.showMessageDialog(frame, message, "Point Buy", JOptionPane.ERROR_MESSAGE);
    }
}
